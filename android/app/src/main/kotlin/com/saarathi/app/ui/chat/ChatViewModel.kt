package com.saarathi.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saarathi.app.AppContainer
import com.saarathi.app.data.db.BookmarkKind
import com.saarathi.app.data.db.KarmaStepEntity
import com.saarathi.app.data.db.MessageEntity
import com.saarathi.app.data.scripture.Citation
import com.saarathi.app.data.scripture.ScriptureRepository
import com.saarathi.app.ingest.Attachment
import com.saarathi.app.karma.KarmaSteps
import com.saarathi.app.rag.ReplyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneId

data class StreamingState(val messageId: Long, val text: String, val promptDone: Int, val promptTotal: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer, initialConversationId: Long, private val mode: String) : ViewModel() {
    private val _conversationId = MutableStateFlow(initialConversationId)
    val conversationId: StateFlow<Long> = _conversationId

    val messages: StateFlow<List<MessageEntity>> = _conversationId
        .flatMapLatest { id -> if (id > 0) container.conversations.observeMessages(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming

    private val _attachment = MutableStateFlow<Attachment?>(container.pendingAttachment.also { container.pendingAttachment = null })
    val attachment: StateFlow<Attachment?> = _attachment
    private val _attachmentBusy = MutableStateFlow(false)
    val attachmentBusy: StateFlow<Boolean> = _attachmentBusy

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _cards = MutableStateFlow<Map<Int, ScriptureRepository.VerseCard>>(emptyMap())
    val cards: StateFlow<Map<Int, ScriptureRepository.VerseCard>> = _cards

    val bookmarks = container.conversations.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Karma Steps saved from replies in this conversation (to show "saved" instead of the save action). */
    val conversationSteps: StateFlow<List<KarmaStepEntity>> = _conversationId
        .flatMapLatest { id -> if (id > 0) container.karma.observeForConversation(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Open steps plus the ones finished today, for the empty-chat screen. */
    val todaySteps: StateFlow<List<KarmaStepEntity>> = container.karma.observeOpenOrDoneSince(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null
    val isGenerating: Boolean get() = job?.isActive == true

    /** A journal entry waiting to be linked to the conversation this chat creates. */
    private var journalLink: Long? = container.pendingJournalLink.also { container.pendingJournalLink = null }

    var prefill: String? = container.pendingPrompt.also { container.pendingPrompt = null }
        private set
    var sendNow: Boolean = container.pendingSendNow.also { container.pendingSendNow = false }
        private set

    fun consumePrefill(): Pair<String, Boolean>? = prefill?.let { p -> (p to sendNow).also { prefill = null; sendNow = false } }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || isGenerating) return
        val att = _attachment.value
        _attachment.value = null
        _error.value = null
        job = viewModelScope.launch {
            val id = if (_conversationId.value > 0) _conversationId.value else container.conversations.newConversation(mode = mode).also { _conversationId.value = it }
            journalLink?.let { j -> container.journal.link(j, id); journalLink = null }
            runReply(container.orchestrator.respond(id, clean, att?.toJson() ?: "", att?.extractedText))
        }
    }

    fun regenerate(message: MessageEntity) {
        if (isGenerating) return
        val all = messages.value
        val idx = all.indexOfFirst { it.id == message.id }
        val user = all.subList(0, maxOf(idx, 0)).lastOrNull { it.role == com.saarathi.app.data.db.MessageRole.USER } ?: return
        val att = Attachment.fromJson(user.attachmentJson)
        job = viewModelScope.launch {
            runReply(container.orchestrator.respond(_conversationId.value, user.text, user.attachmentJson, att?.extractedText, regenerateOf = message.id))
        }
    }

    private suspend fun runReply(flow: kotlinx.coroutines.flow.Flow<ReplyEvent>) {
        var current: Long = -1
        flow.collect { ev ->
            when (ev) {
                is ReplyEvent.Prepared -> { current = ev.krishnaMessageId; _streaming.value = StreamingState(current, "", 0, 0) }
                is ReplyEvent.PromptProgress -> _streaming.value = _streaming.value?.copy(promptDone = ev.done, promptTotal = ev.total)
                is ReplyEvent.Delta -> _streaming.value = _streaming.value?.copy(text = ev.fullText)
                is ReplyEvent.Done -> { _streaming.value = null; preloadCards(ev.message) }
                is ReplyEvent.Failed -> { _streaming.value = null; _error.value = ev.reason }
            }
        }
        _streaming.value = null
    }

    fun cancel() {
        container.engine.cancel()
        job?.cancel()
        job = null
        _streaming.value = null
    }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            _attachmentBusy.value = true
            runCatching { container.attachments.process(uri) }
                .onSuccess { _attachment.value = it }
                .onFailure { _error.value = "Could not read that file: ${it.message}" }
            _attachmentBusy.value = false
        }
    }

    fun clearAttachment() { _attachment.value = null }
    fun clearError() { _error.value = null }

    fun citations(m: MessageEntity): List<Citation> = runCatching { Json.decodeFromString<List<Citation>>(m.citationsJson) }.getOrDefault(emptyList())

    fun suggestedRef(m: MessageEntity): String? = runCatching {
        val o = Json.parseToJsonElement(m.metaJson) as kotlinx.serialization.json.JsonObject
        (o["suggested"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()

    fun ensureCard(verseId: Int) {
        if (_cards.value.containsKey(verseId)) return
        viewModelScope.launch { container.scripture.card(verseId)?.let { c -> _cards.value = _cards.value + (verseId to c) } }
    }

    fun ensureCardByRef(ref: String, onId: (Int) -> Unit) {
        viewModelScope.launch { container.scripture.cardByRef(ref)?.let { c -> _cards.value = _cards.value + (c.verse.id to c); onId(c.verse.id) } }
    }

    private fun preloadCards(m: MessageEntity) { citations(m).forEach { if (it is Citation.Verse) ensureCard(it.verseId) } }

    fun toggleMessageBookmark(m: MessageEntity) = viewModelScope.launch { container.conversations.setBookmarked(m.id, !m.bookmarked) }

    fun deleteMessage(m: MessageEntity) = viewModelScope.launch { container.conversations.deleteMessage(m) }

    fun isVerseBookmarked(ref: String): Boolean = bookmarks.value.any { it.kind == BookmarkKind.VERSE && it.refKey == ref }

    fun toggleVerseBookmark(card: ScriptureRepository.VerseCard) = viewModelScope.launch {
        val ref = card.verse.ref
        if (isVerseBookmarked(ref)) container.conversations.removeBookmark(BookmarkKind.VERSE, ref)
        else container.conversations.addBookmark(BookmarkKind.VERSE, ref, "Bhagavad Gita $ref", card.translation?.text?.take(160) ?: card.verse.iast.take(160))
    }

    // Karma Steps
    fun stepsIn(m: MessageEntity): List<String> = KarmaSteps.extract(m.text)
    fun hasSavedSteps(m: MessageEntity): Boolean = conversationSteps.value.any { it.messageId == m.id }
    fun saveSteps(m: MessageEntity) = viewModelScope.launch {
        val steps = stepsIn(m)
        if (steps.isNotEmpty() && !hasSavedSteps(m)) container.karma.saveSteps(steps, _conversationId.value.takeIf { it > 0 }, m.id)
    }
    fun setStepDone(step: KarmaStepEntity, done: Boolean) = viewModelScope.launch { container.karma.setDone(step.id, done) }

    fun startNew() { cancel(); _conversationId.value = -1 }

    override fun onCleared() { if (isGenerating) container.engine.cancel() }

    companion object {
        fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
