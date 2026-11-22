package com.saarathi.app

import android.content.Context
import com.saarathi.app.data.db.AppDatabase
import com.saarathi.app.data.db.ConversationRepository
import com.saarathi.app.data.db.JournalRepository
import com.saarathi.app.data.db.JourneyRepository
import com.saarathi.app.data.db.KarmaRepository
import com.saarathi.app.data.model.ModelRepository
import com.saarathi.app.ingest.Attachment
import com.saarathi.app.data.prefs.SettingsRepository
import com.saarathi.app.data.scripture.ScriptureDatabase
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ingest.AttachmentProcessor
import com.saarathi.app.llm.LlamaEngine
import com.saarathi.app.rag.Embedder
import com.saarathi.app.rag.KrishnaOrchestrator
import com.saarathi.app.rag.Lexicon
import com.saarathi.app.rag.PromptBuilder
import com.saarathi.app.rag.QueryAnalyzer
import com.saarathi.app.rag.Retriever
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency container. Everything is lazy so the splash screen stays fast and the
 * scripture database is only copied out of the APK the first time it is needed.
 */
class AppContainer(private val context: Context) {
    val appContext: Context get() = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A message another screen wants the chat to pre-fill (e.g. "Discuss this verse"). Consumed once. */
    @Volatile var pendingPrompt: String? = null

    /** A route to open after the splash (set from a notification tap). Consumed once. */
    @Volatile var pendingRoute: String? = null

    /** When true, the next chat screen sends [pendingPrompt] immediately instead of only pre-filling it. Consumed once. */
    @Volatile var pendingSendNow: Boolean = false

    /** An attachment another screen prepared for the chat (e.g. a journal entry to reflect on). Consumed once. */
    @Volatile var pendingAttachment: Attachment? = null

    /** The journal entry to link with the next conversation the chat creates. Consumed once. */
    @Volatile var pendingJournalLink: Long? = null

    /** Title and body another screen wants a new journal entry to start with. Consumed once. */
    @Volatile var pendingJournalDraft: Pair<String, String>? = null

    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val conversations: ConversationRepository by lazy { ConversationRepository(database) }
    val journal: JournalRepository by lazy { JournalRepository(database) }
    val karma: KarmaRepository by lazy { KarmaRepository(database) }
    val journey: JourneyRepository by lazy { JourneyRepository(database) }
    val scriptureDb: ScriptureDatabase by lazy { ScriptureDatabase(context) }
    val scripture: ScriptureRepository by lazy { ScriptureRepository(scriptureDb) }
    val lexicon: Lexicon by lazy { Lexicon.load(context) }
    val analyzer: QueryAnalyzer by lazy { QueryAnalyzer(lexicon) }
    val embedder: Embedder by lazy { Embedder(context) }
    val retriever: Retriever by lazy { Retriever(scripture, lexicon, embedder) }
    val models: ModelRepository by lazy { ModelRepository(context) }
    val engine: LlamaEngine by lazy { LlamaEngine() }
    val promptBuilder: PromptBuilder by lazy { PromptBuilder() }
    val attachments: AttachmentProcessor by lazy { AttachmentProcessor(context) }
    val orchestrator: KrishnaOrchestrator by lazy {
        KrishnaOrchestrator(engine, analyzer, retriever, promptBuilder, scripture, conversations, settings)
    }
}
