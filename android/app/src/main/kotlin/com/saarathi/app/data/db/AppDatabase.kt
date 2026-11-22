package com.saarathi.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun roleToString(r: MessageRole): String = r.name
    @TypeConverter fun stringToRole(s: String): MessageRole = MessageRole.valueOf(s)
    @TypeConverter fun statusToString(s: MessageStatus): String = s.name
    @TypeConverter fun stringToStatus(s: String): MessageStatus = MessageStatus.valueOf(s)
    @TypeConverter fun kindToString(k: BookmarkKind): String = k.name
    @TypeConverter fun stringToKind(s: String): BookmarkKind = BookmarkKind.valueOf(s)
}

@Database(
    entities = [
        ConversationEntity::class, MessageEntity::class, BookmarkEntity::class, MemoryEntity::class,
        JournalEntryEntity::class, KarmaStepEntity::class, VerseReadEntity::class,
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun memories(): MemoryDao
    abstract fun journal(): JournalDao
    abstract fun karmaSteps(): KarmaStepDao
    abstract fun verseReads(): VerseReadDao

    companion object {
        /** Phase 3: journal, Karma Steps and Gita journey progress. Existing conversations are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `journal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `tags` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `linkedConversationId` INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_updatedAt` ON `journal_entries` (`updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `karma_steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `conversationId` INTEGER, `messageId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `done` INTEGER NOT NULL, `doneAt` INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_karma_steps_conversationId` ON `karma_steps` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_karma_steps_done` ON `karma_steps` (`done`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `verse_reads` (`verseId` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `readAt` INTEGER NOT NULL, PRIMARY KEY(`verseId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_verse_reads_chapter` ON `verse_reads` (`chapter`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "saarathi.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
