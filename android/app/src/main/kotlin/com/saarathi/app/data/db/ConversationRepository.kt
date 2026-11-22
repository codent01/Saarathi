package com.saarathi.app.data.db

import kotlinx.coroutines.flow.Flow

class ConversationRepository(private val db: AppDatabase) {
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversations().observeAll()
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> = db.messages().observeForConversation(conversationId)
    fun observeBookmarkedMessages(): Flow<List<MessageEntity>> = db.messages().observeBookmarked()
    fun observeBookmarks(): Flow<List<BookmarkEntity>> = db.bookmarks().observeAll()
    fun observeMemories(): Flow<List<MemoryEntity>> = db.memories().observeAll()

    suspend fun conversation(id: Long): ConversationEntity? = db.conversations().get(id)
    suspend fun latestConversation(): ConversationEntity? = db.conversations().latest()
    suspend fun messages(conversationId: Long): List<MessageEntity> = db.messages().forConversation(conversationId)

    suspend fun newConversation(title: String = "New conversation", mode: String = "chat"): Long {
        val now = System.currentTimeMillis()
        return db.conversations().insert(ConversationEntity(title = title, createdAt = now, updatedAt = now, mode = mode))
    }

    suspend fun touch(conversationId: Long, title: String? = null) {
        val c = db.conversations().get(conversationId) ?: return
        db.conversations().update(c.copy(updatedAt = System.currentTimeMillis(), title = title ?: c.title))
    }

    suspend fun addMessage(m: MessageEntity): Long = db.messages().insert(m)
    suspend fun updateMessage(m: MessageEntity) = db.messages().update(m)
    suspend fun message(id: Long): MessageEntity? = db.messages().get(id)
    suspend fun deleteMessage(m: MessageEntity) = db.messages().delete(m)
    suspend fun deleteConversation(id: Long) = db.conversations().delete(id)
    suspend fun clearAllConversations() = db.conversations().deleteAll()
    suspend fun markInterrupted() = db.messages().markInterrupted(MessageStatus.CANCELLED)
    suspend fun searchConversations(q: String): List<ConversationEntity> = db.conversations().search(q)
    suspend fun searchMessages(q: String): List<MessageEntity> = db.messages().search(q)

    suspend fun setBookmarked(messageId: Long, bookmarked: Boolean) {
        val m = db.messages().get(messageId) ?: return
        db.messages().update(m.copy(bookmarked = bookmarked))
    }

    suspend fun addBookmark(kind: BookmarkKind, refKey: String, title: String, preview: String) {
        db.bookmarks().insert(BookmarkEntity(kind = kind, refKey = refKey, title = title, preview = preview, createdAt = System.currentTimeMillis()))
    }

    suspend fun removeBookmark(kind: BookmarkKind, refKey: String) = db.bookmarks().delete(kind, refKey)
    fun observeBookmarkExists(kind: BookmarkKind, refKey: String): Flow<Boolean> = db.bookmarks().observeExists(kind, refKey)

    suspend fun enabledMemories(limit: Int = 12): List<MemoryEntity> = db.memories().enabled(limit)
    suspend fun addMemory(text: String, source: String) = db.memories().insert(MemoryEntity(text = text, source = source, createdAt = System.currentTimeMillis()))
    suspend fun updateMemory(m: MemoryEntity) = db.memories().update(m)
    suspend fun deleteMemory(id: Long) = db.memories().delete(id)
    suspend fun clearMemories() = db.memories().deleteAll()
}
