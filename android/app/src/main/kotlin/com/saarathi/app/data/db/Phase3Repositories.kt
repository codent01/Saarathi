package com.saarathi.app.data.db

import kotlinx.coroutines.flow.Flow

/** "My Kurukshetra": private journal entries. */
class JournalRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<JournalEntryEntity>> = db.journal().observeAll()
    fun observeSearch(q: String): Flow<List<JournalEntryEntity>> = if (q.isBlank()) observeAll() else db.journal().observeSearch(q.trim())
    fun observe(id: Long): Flow<JournalEntryEntity?> = db.journal().observe(id)
    suspend fun get(id: Long): JournalEntryEntity? = db.journal().get(id)

    suspend fun create(title: String, body: String, tags: String): Long {
        val now = System.currentTimeMillis()
        return db.journal().insert(JournalEntryEntity(title = cleanTitle(title, body), body = body.trim(), tags = cleanTags(tags), createdAt = now, updatedAt = now))
    }

    suspend fun save(entry: JournalEntryEntity, title: String, body: String, tags: String) =
        db.journal().update(entry.copy(title = cleanTitle(title, body), body = body.trim(), tags = cleanTags(tags), updatedAt = System.currentTimeMillis()))

    suspend fun link(id: Long, conversationId: Long) {
        val e = db.journal().get(id) ?: return
        db.journal().update(e.copy(linkedConversationId = conversationId))
    }

    suspend fun delete(id: Long) = db.journal().delete(id)
    suspend fun deleteAll() = db.journal().deleteAll()

    companion object {
        fun cleanTitle(title: String, body: String): String =
            title.trim().ifBlank { body.trim().lineSequence().firstOrNull()?.trim()?.take(48)?.ifBlank { null } ?: "Untitled" }

        fun cleanTags(tags: String): String =
            tags.split(',', ' ', '#', '\n').map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().joinToString(",")
    }
}

/** Karma Steps: small actions the user kept from a reply. */
class KarmaRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<KarmaStepEntity>> = db.karmaSteps().observeAll()
    fun observeOpenOrDoneSince(since: Long): Flow<List<KarmaStepEntity>> = db.karmaSteps().observeOpenOrDoneSince(since)
    fun observeForConversation(conversationId: Long): Flow<List<KarmaStepEntity>> = db.karmaSteps().observeForConversation(conversationId)

    suspend fun saveSteps(texts: List<String>, conversationId: Long?, messageId: Long): List<Long> {
        // Lists are shown newest first, so the first step of a reply gets the latest timestamp to keep the reply's order.
        val now = System.currentTimeMillis()
        return db.karmaSteps().insertAll(texts.mapIndexed { i, t -> KarmaStepEntity(text = t, conversationId = conversationId, messageId = messageId, createdAt = now + (texts.size - 1 - i)) })
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val s = db.karmaSteps().get(id) ?: return
        db.karmaSteps().update(s.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null))
    }

    suspend fun delete(id: Long) = db.karmaSteps().delete(id)
    suspend fun deleteForMessage(messageId: Long) = db.karmaSteps().deleteForMessage(messageId)
    suspend fun deleteAll() = db.karmaSteps().deleteAll()
}

/** 18-chapter journey: which verses have been read in the reader. */
class JourneyRepository(private val db: AppDatabase) {
    fun observeCounts(): Flow<List<ChapterReadCount>> = db.verseReads().observeCounts()
    fun observeReadIds(chapter: Int): Flow<List<Int>> = db.verseReads().observeReadIds(chapter)

    suspend fun markRead(chapter: Int, verseIds: Collection<Int>) {
        if (verseIds.isEmpty()) return
        val now = System.currentTimeMillis()
        db.verseReads().insertAll(verseIds.map { VerseReadEntity(it, chapter, now) })
    }

    suspend fun clearChapter(chapter: Int) = db.verseReads().clearChapter(chapter)
    suspend fun deleteAll() = db.verseReads().deleteAll()
}
