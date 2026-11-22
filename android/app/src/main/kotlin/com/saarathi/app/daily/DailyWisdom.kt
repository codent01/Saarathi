package com.saarathi.app.daily

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saarathi.app.MainActivity
import com.saarathi.app.R
import com.saarathi.app.SaarathiApp
import com.saarathi.app.data.scripture.ScriptureRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Daily Wisdom: one verified verse per day, chosen deterministically from the date so every
 * device shows the same verse on the same day with no server involved.
 */
object DailyWisdom {
    const val CHANNEL_ID = "daily_wisdom"
    const val WORK_NAME = "daily_wisdom_notification"
    const val EXTRA_ROUTE = "route"
    const val ROUTE_DAILY = "daily"
    private const val NOTIFICATION_ID = 1008
    private const val KEY_FORCE = "force"

    private val reflections = mapOf(
        "fear" to "What is one fear you have been treating as a fact?",
        "anxiety" to "What is one thing you are trying to control that may not be entirely within your control?",
        "anger" to "When you were last angry, what did you want that you were not getting?",
        "desire" to "Which wish, if it came true today, would leave you wanting something else tomorrow?",
        "attachment" to "What are you holding so tightly that it has started holding you?",
        "duty" to "What small duty have you been avoiding, and what would doing it cost you?",
        "action" to "Which action today can you do fully without needing a particular result?",
        "results" to "Where did your effort end and your worry about the outcome begin?",
        "equanimity" to "What praise or criticism this week moved you more than it deserved?",
        "mind" to "Where did your mind wander most today, and what was it looking for?",
        "discipline" to "What is one promise to yourself you can keep for the next 24 hours?",
        "failure" to "What did your last setback teach you that success could not have?",
        "work" to "If no one ever found out, would you still do your work the same way?",
        "happiness" to "What made you quietly content today, with nothing to show for it?",
        "death" to "If this day were one of a limited number, what would you stop postponing?",
        "ego" to "Where did the word I get in the way of what actually needed doing?",
        "surrender" to "What would change if you did your part and then let the rest go?",
        "faith" to "What do you already know to be right but are waiting for permission to do?",
        "knowledge" to "What belief about yourself have you never actually examined?",
        "compassion" to "Who around you is carrying something heavy that you have not noticed?",
    )

    /** Verses with a strong life-guidance weight, ordered by id so the sequence is stable across builds. */
    suspend fun verseForDate(scripture: ScriptureRepository, date: LocalDate = LocalDate.now()): ScriptureRepository.VerseCard? {
        val pool = scripture.weightedVerses(0.8)
        if (pool.isEmpty()) return null
        val day = date.toEpochDay()
        // a fixed stride keeps consecutive days from walking through one chapter in order
        val index = (((day * 37) % pool.size) + pool.size) % pool.size
        return scripture.card(pool[index.toInt()].id)
    }

    fun reflectionFor(card: ScriptureRepository.VerseCard): String {
        for (t in card.verse.themes) reflections[t]?.let { return it }
        return "What is one line of this verse you could carry with you today?"
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Daily wisdom", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One verified verse from the Bhagavad Gita each day"
            })
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = ChronoUnit.MINUTES.between(now, next)
        val request = PeriodicWorkRequestBuilder<DailyWisdomWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

    /** Fires the notification right away; used by the "Send a test notification" button. */
    fun showNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DailyWisdomWorker>().setInputData(workDataOf(KEY_FORCE to true)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("daily_wisdom_now", ExistingWorkPolicy.REPLACE, request)
    }

    internal fun notify(context: Context, card: ScriptureRepository.VerseCard) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROUTE, ROUTE_DAILY)
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = card.translation?.text ?: card.verse.iast.replace('\n', ' ')
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Bhagavad Gita ${card.verse.ref}")
            .setContentText(body.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body + "\n\n" + reflectionFor(card)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    internal fun isForced(worker: WorkerParameters): Boolean = worker.inputData.getBoolean(KEY_FORCE, false)
}

class DailyWisdomWorker(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? SaarathiApp ?: return Result.failure()
        val settings = app.container.settings.current()
        if (!settings.dailyWisdomEnabled && !DailyWisdom.isForced(params)) return Result.success()
        val card = DailyWisdom.verseForDate(app.container.scripture) ?: return Result.retry()
        DailyWisdom.notify(applicationContext, card)
        return Result.success()
    }
}
