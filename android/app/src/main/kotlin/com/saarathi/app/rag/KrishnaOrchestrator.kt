package com.saarathi.app.rag

import android.util.Log
import com.saarathi.app.data.db.ConversationRepository
import com.saarathi.app.data.db.MessageEntity
import com.saarathi.app.data.db.MessageRole
import com.saarathi.app.data.db.MessageStatus
import com.saarathi.app.data.prefs.ResponseLength
import com.saarathi.app.data.prefs.SettingsRepository
import com.saarathi.app.data.scripture.Citation
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ingest.Attachment
import com.saarathi.app.llm.ChatMessage
import com.saarathi.app.llm.GenEvent
import com.saarathi.app.llm.GenParams
import com.saarathi.app.llm.LlamaEngine
import com.saarathi.app.llm.ModelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

sealed class ReplyEvent {
    data class Prepared(val analysis: QueryAnalysis, val retrieval: RetrievalResult, val krishnaMessageId: Long) : ReplyEvent()
    data class PromptProgress(val done: Int, val total: Int) : ReplyEvent()
    data class Delta(val fullText: String) : ReplyEvent()
    data class Done(val message: MessageEntity) : ReplyEvent()
    data class Failed(val reason: String, val krishnaMessageId: Long?) : ReplyEvent()
}

/**
 * The whole answer pipeline: analyze -> retrieve -> prompt -> generate -> verify citations -> store.
 * Verse cards are attached only for references that exist in the local scripture database.
 */
class KrishnaOrchestrator(
    private val engine: LlamaEngine,
    private val analyzer: QueryAnalyzer,
    private val retriever: Retriever,
    private val prompts: PromptBuilder,
    private val scripture: ScriptureRepository,
    private val conversations: ConversationRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun respond(conversationId: Long, userText: String, attachmentJson: String = "", attachmentText: String? = null, regenerateOf: Long? = null): Flow<ReplyEvent> = flow {
        val prefs = settings.current()
        val history = conversations.messages(conversationId)
        val conversation = conversations.conversation(conversationId)
        val arjunaMode = conversation?.mode == "arjuna"

        // store the user's message (unless regenerating, in which case it is already there)
        val userMessageId = if (regenerateOf == null) {
            conversations.addMessage(MessageEntity(conversationId = conversationId, role = MessageRole.USER, text = userText, createdAt = System.currentTimeMillis(), attachmentJson = attachmentJson))
        } else -1L
        // Name the conversation after the first message unless another screen already gave it a title (e.g. Decision Compass).
        if (history.isEmpty() && (conversation == null || conversation.title == "New conversation")) conversations.touch(conversationId, title = userText.trim().take(48).ifBlank { "Conversation" })

        val previousThemes = history.lastOrNull { it.role == MessageRole.KRISHNA }?.let { metaThemes(it.metaJson) } ?: emptyList()
        val analysis = analyzer.analyze(userText, previousThemes)
        val retrieval = runCatching { retriever.retrieve(analysis, userText) }.getOrElse { Log.w(TAG, "retrieval failed", it); RetrievalResult.EMPTY }

        val krishnaId = if (regenerateOf != null) regenerateOf else conversations.addMessage(
            MessageEntity(conversationId = conversationId, role = MessageRole.KRISHNA, text = "", createdAt = System.currentTimeMillis() + 1, status = MessageStatus.STREAMING)
        )
        emit(ReplyEvent.Prepared(analysis, retrieval, krishnaId))

        if (engine.state.value !is ModelState.Ready) {
            val reason = "The language model is not loaded. Open Setup to load a model."
            finalizeError(krishnaId, reason)
            emit(ReplyEvent.Failed(reason, krishnaId))
            return@flow
        }

        val memories = if (prefs.memoryEnabled) conversations.enabledMemories() else emptyList()
        val system = prompts.systemPrompt(prefs, memories, arjunaMode)
        val priorTurns = prompts.historyTurns(history.filter { it.id != regenerateOf && it.status == MessageStatus.COMPLETE })
        val attachmentKind = Attachment.fromJson(attachmentJson)?.kind ?: ""
        val userTurn = prompts.userTurn(userText, retrieval, analysis.intent, attachmentText, attachmentKind)
        // Remember the exact turn text: next time it is replayed verbatim so the KV prefix cache matches.
        if (userMessageId > 0) conversations.message(userMessageId)?.let { conversations.updateMessage(it.copy(metaJson = promptMeta(userTurn))) }
        val messages = trimToBudget(system, priorTurns, userTurn, prefs.contextSize)

        val maxTokens = when (prefs.responseLength) {
            ResponseLength.SHORT -> 220
            ResponseLength.BALANCED -> 420
            ResponseLength.DETAILED -> 650
        }.let { if (analysis.intent == Intent.CASUAL) minOf(it, 160) else it }

        val sb = StringBuilder()
        var failure: String? = null
        var cancelled = false
        var promptTokens = 0
        val started = System.currentTimeMillis()
        var firstToken = 0L
        engine.generate(messages, GenParams(maxTokens = maxTokens, temperature = 0.7f, topP = 0.92f, topK = 40, repeatPenalty = 1.08f)).collect { ev ->
            when (ev) {
                is GenEvent.PromptProgress -> { promptTokens = ev.total; emit(ReplyEvent.PromptProgress(ev.done, ev.total)) }
                is GenEvent.Token -> {
                    if (firstToken == 0L) firstToken = System.currentTimeMillis()
                    sb.append(ev.piece)
                    emit(ReplyEvent.Delta(sb.toString()))
                }
                is GenEvent.Finished -> if (ev.cancelled) cancelled = true
                is GenEvent.Error -> failure = ev.message
            }
        }

        if (failure != null && sb.isBlank()) {
            finalizeError(krishnaId, failure!!)
            emit(ReplyEvent.Failed(failure!!, krishnaId))
            return@flow
        }

        var text = cleanup(sb.toString())
        val citations = verifyCitations(text, retrieval, analysis.explicitRefs)
        val suggested = if (citations.isEmpty() && analysis.intent == Intent.SUPPORT) {
            retrieval.verses.firstOrNull()?.takeIf { it.score >= 1.6 }?.card?.verse?.let { Citation.Verse(it.id, it.ref, "") }
        } else null
        if (analysis.intent == Intent.CRISIS) text += prompts.crisisFooter()

        val meta = buildJsonObject {
            put("raw", JsonPrimitive(sb.toString()))
            put("intent", JsonPrimitive(analysis.intent.name))
            put("themes", JsonPrimitive(retrieval.themes.joinToString(",")))
            put("retrieved", JsonPrimitive(retrieval.verses.joinToString(",") { it.card.verse.ref }))
            put("promptTokens", JsonPrimitive(promptTokens))
            put("firstTokenMs", JsonPrimitive(if (firstToken > 0) firstToken - started else -1))
            put("totalMs", JsonPrimitive(System.currentTimeMillis() - started))
            put("chars", JsonPrimitive(text.length))
            suggested?.let { put("suggested", JsonPrimitive(it.ref)) }
        }
        Log.i(TAG, "reply intent=${analysis.intent} prompt=$promptTokens firstToken=${if (firstToken > 0) firstToken - started else -1}ms total=${System.currentTimeMillis() - started}ms chars=${text.length} cites=${citations.size}")
        val stored = conversations.message(krishnaId)?.copy(
            text = text,
            status = if (cancelled) MessageStatus.CANCELLED else MessageStatus.COMPLETE,
            citationsJson = json.encodeToString(citations),
            metaJson = meta.toString(),
        ) ?: return@flow
        conversations.updateMessage(stored)
        conversations.touch(conversationId)
        emit(ReplyEvent.Done(stored))
    }

    private suspend fun finalizeError(id: Long, reason: String) {
        conversations.message(id)?.let { conversations.updateMessage(it.copy(text = reason, status = MessageStatus.ERROR)) }
    }

    /** Drops the oldest history turns until the prompt fits the context window with room for the answer. */
    private suspend fun trimToBudget(system: String, history: List<ChatMessage>, userTurn: String, contextSize: Int): List<ChatMessage> {
        val reserve = 700
        var turns = history
        while (true) {
            val messages = listOf(ChatMessage("system", system)) + turns + ChatMessage("user", userTurn)
            val tokens = engine.tokenCount(engine.applyTemplate(messages, addAssistant = true))
            if (tokens + reserve <= contextSize || turns.isEmpty()) return messages
            // drop one exchange (user + assistant) from the front
            turns = turns.drop(if (turns.size >= 2) 2 else 1)
        }
    }

    private val citationRegexes = listOf(
        Regex("""(?:Bhagavad[\s-]*)?G[iī]t[aā]\s*,?\s*(?:chapter\s*)?(\d{1,2})\s*[.:]\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""BG\s*(\d{1,2})\s*[.:]\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""chapter\s*(\d{1,2})\s*,?\s*(?:verse|text|shloka|sloka)\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""(?:verse|text|shloka|sloka)\s*(\d{1,2})\s*(?:of|in|from)\s*chapter\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
    )

    private suspend fun verifyCitations(text: String, retrieval: RetrievalResult, explicit: List<Pair<Int, Int>>): List<Citation> {
        val out = LinkedHashMap<String, Citation>()
        // Verses the user asked about by number are always shown, whatever the model wrote.
        for ((c, v) in explicit) {
            val verse = scripture.verseExists(c, v) ?: continue
            out.putIfAbsent(verse.ref, Citation.Verse(verse.id, verse.ref, scripture.card(verse.id)?.translation?.sourceKey ?: ""))
        }
        val found = citationRegexes.flatMap { r -> r.findAll(text).map { m -> m.range.first to m } }.sortedBy { it.first }
        for ((_, m) in found) {
            // the "verse N of chapter C" form has the numbers reversed
            val reversed = m.value.lowercase().let { it.startsWith("verse") || it.startsWith("text") || it.startsWith("shloka") || it.startsWith("sloka") }
            val c = (if (reversed) m.groupValues[2] else m.groupValues[1]).toIntOrNull() ?: continue
            val v = (if (reversed) m.groupValues[1] else m.groupValues[2]).toIntOrNull() ?: continue
            val verse = scripture.verseExists(c, v) ?: continue
            val inContext = retrieval.verses.any { it.card.verse.id == verse.id }
            if (!inContext && retrieval.verses.isNotEmpty()) {
                // The model wandered outside the offered context. The verse still exists, so show it,
                // but only if a translation is available so the card is never empty.
                if (scripture.card(verse.id)?.translation == null) continue
            }
            val source = scripture.card(verse.id)?.translation?.sourceKey ?: ""
            out.putIfAbsent(verse.ref, Citation.Verse(verse.id, verse.ref, source))
            if (out.size >= 3) break
        }
        return out.values.toList()
    }

    private fun cleanup(raw: String): String = raw
        .replace(Regex("""^\s*(Krishna|Assistant|Model)\s*:\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[CONTEXT[\s\S]*?\]\s*"""), "")
        .replace(Regex("""(?m)^#{1,6}\s*"""), "")
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .trim()

    private fun promptMeta(turn: String): String = buildJsonObject { put("prompt", JsonPrimitive(turn)) }.toString()

    private fun metaThemes(metaJson: String): List<String> = runCatching {
        (Json.parseToJsonElement(metaJson) as JsonObject)["themes"]?.let { (it as JsonPrimitive).content.split(',').filter { t -> t.isNotBlank() } }
    }.getOrNull() ?: emptyList()

    companion object { private const val TAG = "Orchestrator" }
}
