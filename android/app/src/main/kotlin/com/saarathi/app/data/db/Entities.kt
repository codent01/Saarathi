package com.saarathi.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mode: String = "chat", // chat | arjuna
)

enum class MessageRole { USER, KRISHNA, SYSTEM }
enum class MessageStatus { COMPLETE, STREAMING, ERROR, CANCELLED }

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId"), Index("createdAt")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val text: String,
    val createdAt: Long,
    val status: MessageStatus = MessageStatus.COMPLETE,
    /** JSON array of citations the app verified against the scripture database. */
    val citationsJson: String = "[]",
    /** JSON describing an attachment the user sent with this message (kind, name, extracted text). */
    val attachmentJson: String = "",
    val bookmarked: Boolean = false,
    /** Free-form diagnostics: tokens, timing, retrieval themes. Never shown unless the user opens details. */
    val metaJson: String = "",
)

enum class BookmarkKind { VERSE, PASSAGE, MESSAGE }

@Entity(tableName = "bookmarks", indices = [Index(value = ["kind", "refKey"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: BookmarkKind,
    /** verse ref like "2.47", passage id, or message id. */
    val refKey: String,
    val title: String,
    val preview: String,
    val note: String = "",
    val createdAt: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val source: String, // "user" | "conversation:<id>"
    val createdAt: Long,
    val enabled: Boolean = true,
)

/** A private journal entry ("My Kurukshetra"). Never leaves the phone; optionally linked to the conversation it was reflected on. */
@Entity(tableName = "journal_entries", indices = [Index("updatedAt")])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    /** Comma-separated, lower-case, trimmed tags. */
    val tags: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val linkedConversationId: Long? = null,
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
}

/** One small step the user chose to keep from a Krishna reply. No reminders are attached to it. */
@Entity(tableName = "karma_steps", indices = [Index("conversationId"), Index("done")])
data class KarmaStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val conversationId: Long?,
    /** The Krishna message the step came from (0 when added by hand). */
    val messageId: Long = 0,
    val createdAt: Long,
    val done: Boolean = false,
    val doneAt: Long? = null,
)

/** A Gita verse the user has read in the reader; one row per verse record, so chapter progress is a count. */
@Entity(tableName = "verse_reads", indices = [Index("chapter")])
data class VerseReadEntity(
    @PrimaryKey val verseId: Int,
    val chapter: Int,
    val readAt: Long,
)

data class ChapterReadCount(val chapter: Int, val count: Int)
