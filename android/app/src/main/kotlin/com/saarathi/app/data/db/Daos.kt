package com.saarathi.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): ConversationEntity?

    @Insert
    suspend fun insert(c: ConversationEntity): Long

    @Update
    suspend fun update(c: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT c.* FROM conversations c WHERE c.id IN (SELECT DISTINCT conversationId FROM messages WHERE text LIKE '%' || :q || '%') OR c.title LIKE '%' || :q || '%' ORDER BY c.updatedAt DESC")
    suspend fun search(q: String): List<ConversationEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun forConversation(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity): Long

    @Update
    suspend fun update(m: MessageEntity)

    @Delete
    suspend fun delete(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE bookmarked = 1 ORDER BY createdAt DESC")
    fun observeBookmarked(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE text LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT 100")
    suspend fun search(q: String): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE status = 'STREAMING'")
    suspend fun markInterrupted(status: MessageStatus)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(b: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE kind = :kind AND refKey = :refKey")
    suspend fun delete(kind: BookmarkKind, refKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE kind = :kind AND refKey = :refKey)")
    fun observeExists(kind: BookmarkKind, refKey: String): Flow<Boolean>
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun enabled(limit: Int): List<MemoryEntity>

    @Insert
    suspend fun insert(m: MemoryEntity): Long

    @Update
    suspend fun update(m: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun observeSearch(q: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun get(id: Long): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    fun observe(id: Long): Flow<JournalEntryEntity?>

    @Insert
    suspend fun insert(e: JournalEntryEntity): Long

    @Update
    suspend fun update(e: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}

@Dao
interface KarmaStepDao {
    @Query("SELECT * FROM karma_steps ORDER BY done ASC, createdAt DESC, id ASC")
    fun observeAll(): Flow<List<KarmaStepEntity>>

    /** Open steps from any day plus steps finished since :since, open ones first. */
    @Query("SELECT * FROM karma_steps WHERE done = 0 OR (doneAt IS NOT NULL AND doneAt >= :since) ORDER BY done ASC, createdAt DESC, id ASC")
    fun observeOpenOrDoneSince(since: Long): Flow<List<KarmaStepEntity>>

    @Query("SELECT * FROM karma_steps WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<KarmaStepEntity>>

    @Query("SELECT * FROM karma_steps WHERE id = :id")
    suspend fun get(id: Long): KarmaStepEntity?

    @Insert
    suspend fun insertAll(steps: List<KarmaStepEntity>): List<Long>

    @Update
    suspend fun update(s: KarmaStepEntity)

    @Query("DELETE FROM karma_steps WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM karma_steps WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)

    @Query("DELETE FROM karma_steps")
    suspend fun deleteAll()
}

@Dao
interface VerseReadDao {
    @Query("SELECT chapter, COUNT(*) AS count FROM verse_reads GROUP BY chapter")
    fun observeCounts(): Flow<List<ChapterReadCount>>

    @Query("SELECT verseId FROM verse_reads WHERE chapter = :chapter")
    fun observeReadIds(chapter: Int): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reads: List<VerseReadEntity>)

    @Query("DELETE FROM verse_reads WHERE chapter = :chapter")
    suspend fun clearChapter(chapter: Int)

    @Query("DELETE FROM verse_reads")
    suspend fun deleteAll()
}
