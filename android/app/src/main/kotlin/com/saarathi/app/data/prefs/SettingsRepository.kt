package com.saarathi.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "saarathi_settings")

enum class ThemeKey(val label: String) { DIVINE_EMBER("Divine Ember"), KURUKSHETRA("Kurukshetra"), ETERNAL_CALM("Eternal Calm") }
enum class LanguageStyle(val label: String) { ENGLISH("English"), HINGLISH("English with Hinglish"), MORE_HINDI("More Hindi") }
enum class ResponseLength(val label: String) { SHORT("Short and comforting"), BALANCED("Balanced"), DETAILED("Detailed with scripture") }
enum class ConversationStyle(val label: String) { NATURAL("Natural conversation"), SCRIPTURE("More scripture-focused"), GUIDED("Guided reflection") }

data class UserSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeKey = ThemeKey.DIVINE_EMBER,
    val textScale: Float = 1.0f,
    val language: LanguageStyle = LanguageStyle.HINGLISH,
    val responseLength: ResponseLength = ResponseLength.BALANCED,
    val conversationStyle: ConversationStyle = ConversationStyle.NATURAL,
    val memoryEnabled: Boolean = false,
    val dailyWisdomEnabled: Boolean = false,
    val dailyWisdomHour: Int = 7,
    val modelFileName: String = "",
    val contextSize: Int = 4096,
    val threads: Int = 0, // 0 = auto
    val gitaPdfUri: String = "",
    val mahabharataPdfUri: String = "",
    val editionPackImported: Boolean = false,
    val userName: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme")
        val textScale = floatPreferencesKey("text_scale")
        val language = stringPreferencesKey("language")
        val responseLength = stringPreferencesKey("response_length")
        val conversationStyle = stringPreferencesKey("conversation_style")
        val memoryEnabled = booleanPreferencesKey("memory_enabled")
        val dailyWisdomEnabled = booleanPreferencesKey("daily_wisdom_enabled")
        val dailyWisdomHour = intPreferencesKey("daily_wisdom_hour")
        val modelFileName = stringPreferencesKey("model_file_name")
        val contextSize = intPreferencesKey("context_size")
        val threads = intPreferencesKey("threads")
        val gitaPdfUri = stringPreferencesKey("gita_pdf_uri")
        val mahabharataPdfUri = stringPreferencesKey("mahabharata_pdf_uri")
        val editionPackImported = booleanPreferencesKey("edition_pack_imported")
        val userName = stringPreferencesKey("user_name")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            onboardingDone = p[Keys.onboardingDone] ?: false,
            theme = p[Keys.theme]?.let { runCatching { ThemeKey.valueOf(it) }.getOrNull() } ?: ThemeKey.DIVINE_EMBER,
            textScale = p[Keys.textScale] ?: 1.0f,
            language = p[Keys.language]?.let { runCatching { LanguageStyle.valueOf(it) }.getOrNull() } ?: LanguageStyle.HINGLISH,
            responseLength = p[Keys.responseLength]?.let { runCatching { ResponseLength.valueOf(it) }.getOrNull() } ?: ResponseLength.BALANCED,
            conversationStyle = p[Keys.conversationStyle]?.let { runCatching { ConversationStyle.valueOf(it) }.getOrNull() } ?: ConversationStyle.NATURAL,
            memoryEnabled = p[Keys.memoryEnabled] ?: false,
            dailyWisdomEnabled = p[Keys.dailyWisdomEnabled] ?: false,
            dailyWisdomHour = p[Keys.dailyWisdomHour] ?: 7,
            modelFileName = p[Keys.modelFileName] ?: "",
            contextSize = p[Keys.contextSize] ?: 4096,
            threads = p[Keys.threads] ?: 0,
            gitaPdfUri = p[Keys.gitaPdfUri] ?: "",
            mahabharataPdfUri = p[Keys.mahabharataPdfUri] ?: "",
            editionPackImported = p[Keys.editionPackImported] ?: false,
            userName = p[Keys.userName] ?: "",
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(block: (UserSettings) -> UserSettings) {
        val next = block(current())
        context.dataStore.edit { p ->
            p[Keys.onboardingDone] = next.onboardingDone
            p[Keys.theme] = next.theme.name
            p[Keys.textScale] = next.textScale
            p[Keys.language] = next.language.name
            p[Keys.responseLength] = next.responseLength.name
            p[Keys.conversationStyle] = next.conversationStyle.name
            p[Keys.memoryEnabled] = next.memoryEnabled
            p[Keys.dailyWisdomEnabled] = next.dailyWisdomEnabled
            p[Keys.dailyWisdomHour] = next.dailyWisdomHour
            p[Keys.modelFileName] = next.modelFileName
            p[Keys.contextSize] = next.contextSize
            p[Keys.threads] = next.threads
            p[Keys.gitaPdfUri] = next.gitaPdfUri
            p[Keys.mahabharataPdfUri] = next.mahabharataPdfUri
            p[Keys.editionPackImported] = next.editionPackImported
            p[Keys.userName] = next.userName
        }
    }
}
