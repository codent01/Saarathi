package com.saarathi.app.data.scripture

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Thin wrapper over the bundled SQLite scripture database (built by tools/scripture-compiler).
 * The file is copied from assets into app storage once per build so that edition packs the
 * user imports can be written into it. All reads are plain SQL; FTS4 handles keyword search.
 */
class ScriptureDatabase(private val context: Context) {
    private val dir = File(context.filesDir, "scripture").apply { mkdirs() }
    private val file = File(dir, "scripture.db")
    private val versionFile = File(dir, "scripture.version")

    @Volatile private var db: SQLiteDatabase? = null

    @Synchronized
    fun open(): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        ensureCopied()
        val opened = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        db = opened
        return opened
    }

    private fun ensureCopied() {
        val assetVersion = runCatching {
            context.assets.open("scripture/scripture.version").bufferedReader().readText().trim()
        }.getOrDefault("0")
        val installed = runCatching { versionFile.readText().trim() }.getOrDefault("")
        if (!file.exists() || installed != assetVersion) {
            Log.i(TAG, "installing scripture.db (asset version $assetVersion)")
            val tmp = File(dir, "scripture.db.tmp")
            context.assets.open("scripture/scripture.db").use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
            // keep user-imported translations if the user already imported an edition pack
            val previousPack = if (file.exists()) runCatching { exportImportedPack(file) }.getOrNull() else null
            if (file.exists()) file.delete()
            tmp.renameTo(file)
            versionFile.writeText(assetVersion)
            if (previousPack != null) {
                runCatching { SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { importPackRows(it, previousPack) } }
            }
        }
    }

    // ---- reads ------------------------------------------------------------------------------

    fun meta(key: String): String? = open().rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    fun sources(): List<ScriptureSource> = open().rawQuery("SELECT key,title,edition,author,kind,license,file_name,pages,bundled FROM sources", null).use { c ->
        buildList { while (c.moveToNext()) add(ScriptureSource(c.getString(0), c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4), c.getString(5) ?: "", c.getString(6) ?: "", c.getInt(7), c.getInt(8) == 1)) }
    }

    fun chapters(): List<GitaChapter> = open().rawQuery("SELECT chapter,title,title_edition,summary,themes,verse_count,first_page_shlokas,first_page_edition FROM gita_chapters ORDER BY chapter", null).use { c ->
        buildList { while (c.moveToNext()) add(GitaChapter(c.getInt(0), c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "", (c.getString(4) ?: "").split(' ').filter { it.isNotBlank() }, c.getInt(5), c.intOrNull(6), c.intOrNull(7))) }
    }

    private val verseColumns = "id,chapter,verse_start,verse_end,ref,speaker,iast,devanagari,page_shlokas,page_edition,themes,keywords,weight"

    private fun Cursor.toVerse() = GitaVerse(
        getInt(0), getInt(1), getInt(2), getInt(3), getString(4), getString(5) ?: "", getString(6), getString(7),
        intOrNull(8), intOrNull(9), (getString(10) ?: "").split(' ').filter { it.isNotBlank() }, getString(11) ?: "", getDouble(12)
    )

    fun verse(id: Int): GitaVerse? = open().rawQuery("SELECT $verseColumns FROM gita_verses WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.toVerse() else null }

    fun verseByRef(ref: String): GitaVerse? = open().rawQuery("SELECT $verseColumns FROM gita_verses WHERE ref = ?", arrayOf(ref)).use { if (it.moveToFirst()) it.toVerse() else null }

    /** Finds the record containing chapter:verse, including combined verses like 1.21-22. */
    fun verseAt(chapter: Int, verse: Int): GitaVerse? = open().rawQuery(
        "SELECT $verseColumns FROM gita_verses WHERE chapter = ? AND verse_start <= ? AND verse_end >= ? LIMIT 1",
        arrayOf(chapter.toString(), verse.toString(), verse.toString())
    ).use { if (it.moveToFirst()) it.toVerse() else null }

    fun versesForChapter(chapter: Int): List<GitaVerse> = open().rawQuery("SELECT $verseColumns FROM gita_verses WHERE chapter = ? ORDER BY verse_start", arrayOf(chapter.toString())).use { c ->
        buildList { while (c.moveToNext()) add(c.toVerse()) }
    }

    fun versesWithMinWeight(minWeight: Double): List<GitaVerse> = open().rawQuery("SELECT $verseColumns FROM gita_verses WHERE weight >= ? ORDER BY id", arrayOf(minWeight.toString())).use { c ->
        buildList { while (c.moveToNext()) add(c.toVerse()) }
    }

    fun versesWithThemes(themes: Collection<String>): List<GitaVerse> {
        if (themes.isEmpty()) return emptyList()
        val where = themes.joinToString(" OR ") { "(' ' || themes || ' ') LIKE ?" }
        val args = themes.map { "% $it %" }.toTypedArray()
        return open().rawQuery("SELECT $verseColumns FROM gita_verses WHERE $where", args).use { c ->
            buildList { while (c.moveToNext()) add(c.toVerse()) }
        }
    }

    fun translations(verseId: Int): List<Translation> = open().rawQuery("SELECT source_key,text,page FROM gita_translations WHERE verse_id = ?", arrayOf(verseId.toString())).use { c ->
        buildList { while (c.moveToNext()) add(Translation(c.getString(0), c.getString(1), c.intOrNull(2))) }
    }

    fun commentary(verseId: Int): List<Translation> = open().rawQuery("SELECT source_key,text,page FROM gita_commentary WHERE verse_id = ?", arrayOf(verseId.toString())).use { c ->
        buildList { while (c.moveToNext()) add(Translation(c.getString(0), c.getString(1), c.intOrNull(2))) }
    }

    fun searchGita(ftsQuery: String, limit: Int = 30): List<Int> = runCatching {
        open().rawQuery("SELECT docid FROM gita_fts WHERE gita_fts MATCH ? LIMIT ?", arrayOf(ftsQuery, limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0)) }
        }
    }.getOrElse { Log.w(TAG, "gita fts failed for '$ftsQuery': ${it.message}"); emptyList() }

    fun searchMahabharata(ftsQuery: String, limit: Int = 20): List<Int> = runCatching {
        open().rawQuery("SELECT docid FROM mb_fts WHERE mb_fts MATCH ? LIMIT ?", arrayOf(ftsQuery, limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0)) }
        }
    }.getOrElse { Log.w(TAG, "mb fts failed for '$ftsQuery': ${it.message}"); emptyList() }

    private val passageColumns = "id,book,parva,sub_parva,section,section_roman,speaker,text,page,gita_chapter"
    private fun Cursor.toPassage() = MbPassage(getInt(0), getInt(1), getString(2), getString(3) ?: "", getInt(4), getString(5) ?: "", getString(6) ?: "", getString(7), getInt(8), intOrNull(9))

    fun passage(id: Int): MbPassage? = open().rawQuery("SELECT $passageColumns FROM mb_passages WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.toPassage() else null }

    fun passages(ids: Collection<Int>): List<MbPassage> {
        if (ids.isEmpty()) return emptyList()
        val inList = ids.joinToString(",")
        return open().rawQuery("SELECT $passageColumns FROM mb_passages WHERE id IN ($inList)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toPassage()) }
        }
    }

    fun passagesForSection(book: Int, section: Int): List<MbPassage> = open().rawQuery("SELECT $passageColumns FROM mb_passages WHERE book = ? AND section = ? ORDER BY id", arrayOf(book.toString(), section.toString())).use { c ->
        buildList { while (c.moveToNext()) add(c.toPassage()) }
    }

    fun parvaSummary(): List<Triple<Int, String, Int>> = open().rawQuery("SELECT book, parva, COUNT(*) FROM mb_passages GROUP BY book ORDER BY book", null).use { c ->
        buildList { while (c.moveToNext()) add(Triple(c.getInt(0), c.getString(1), c.getInt(2))) }
    }

    /** All precomputed verse vectors (float32 little-endian blobs). About 1 MB; loaded once. */
    fun embeddings(): Map<Int, FloatArray> = runCatching {
        open().rawQuery("SELECT verse_id, dim, vec FROM gita_embeddings", null).use { c ->
            val out = HashMap<Int, FloatArray>(700)
            while (c.moveToNext()) {
                val dim = c.getInt(1)
                val bb = java.nio.ByteBuffer.wrap(c.getBlob(2)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val v = FloatArray(dim) { bb.getFloat() }
                out[c.getInt(0)] = v
            }
            out
        }
    }.getOrElse { Log.w(TAG, "no embeddings table: ${it.message}"); emptyMap() }

    fun verseCount(): Int = open().rawQuery("SELECT COUNT(*) FROM gita_verses", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun passageCount(): Int = open().rawQuery("SELECT COUNT(*) FROM mb_passages", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    fun importedTranslationCount(sourceKey: String): Int = open().rawQuery("SELECT COUNT(*) FROM gita_translations WHERE source_key = ?", arrayOf(sourceKey)).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---- edition pack import -----------------------------------------------------------------

    data class PackResult(val sourceKey: String, val imported: Int, val skipped: Int)

    /** Imports a JSON pack produced by tools/scripture-compiler (format saarathi-gita-edition-pack/1). */
    fun importEditionPack(json: String): PackResult {
        val root = Json.parseToJsonElement(json).jsonObject
        val format = root["format"]?.jsonPrimitive?.content ?: ""
        require(format.startsWith("saarathi-gita-edition-pack/")) { "Not a Saarathi edition pack" }
        val source = root["source"]!!.jsonObject
        val key = source["key"]!!.jsonPrimitive.content
        val rows = mutableListOf<PackRow>()
        for (v in root["verses"]!!.jsonArray) {
            val o = v.jsonObject
            rows += PackRow(
                chapter = o["chapter"]!!.jsonPrimitive.content.toInt(),
                verseStart = o["verse_start"]!!.jsonPrimitive.content.toInt(),
                page = o["page"]?.jsonPrimitive?.content?.toIntOrNull(),
                translation = o["translation"]?.jsonPrimitive?.content ?: "",
                commentary = o["commentary"]?.jsonPrimitive?.content ?: "",
            )
        }
        val pack = Pack(key, source["title"]?.jsonPrimitive?.content ?: key, source["edition"]?.jsonPrimitive?.content ?: "",
            source["file_name"]?.jsonPrimitive?.content ?: "", source["pages"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0, rows)
        return importPackRows(open(), pack)
    }

    private data class PackRow(val chapter: Int, val verseStart: Int, val page: Int?, val translation: String, val commentary: String)
    private data class Pack(val key: String, val title: String, val edition: String, val fileName: String, val pages: Int, val rows: List<PackRow>)

    private fun importPackRows(database: SQLiteDatabase, pack: Pack): PackResult {
        var imported = 0
        var skipped = 0
        database.beginTransaction()
        try {
            database.execSQL(
                "INSERT OR REPLACE INTO sources(key,title,edition,author,kind,license,file_name,pages,bundled) VALUES(?,?,?,?,?,?,?,?,0)",
                arrayOf(pack.key, pack.title, pack.edition, "", "gita_edition", "Imported from the user's own copy", pack.fileName, pack.pages)
            )
            for (r in pack.rows) {
                val verse = database.rawQuery(
                    "SELECT id FROM gita_verses WHERE chapter = ? AND verse_start <= ? AND verse_end >= ? LIMIT 1",
                    arrayOf(r.chapter.toString(), r.verseStart.toString(), r.verseStart.toString())
                ).use { if (it.moveToFirst()) it.getInt(0) else null }
                if (verse == null) { skipped++; continue }
                if (r.translation.length >= 20) {
                    database.execSQL("INSERT OR REPLACE INTO gita_translations(verse_id,source_key,text,page) VALUES(?,?,?,?)", arrayOf(verse, pack.key, r.translation, r.page))
                    imported++
                } else skipped++
                if (r.commentary.length >= 40) {
                    database.execSQL("INSERT OR REPLACE INTO gita_commentary(verse_id,source_key,text,page) VALUES(?,?,?,?)", arrayOf(verse, pack.key, r.commentary, r.page))
                }
                if (r.page != null) {
                    database.execSQL("UPDATE gita_verses SET page_edition = ? WHERE id = ? AND page_edition IS NULL", arrayOf(r.page, verse))
                }
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return PackResult(pack.key, imported, skipped)
    }

    private fun exportImportedPack(dbFile: File): Pack? {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { old ->
            val src = old.rawQuery("SELECT key,title,edition,file_name,pages FROM sources WHERE bundled = 0 AND kind = 'gita_edition' AND key IN (SELECT DISTINCT source_key FROM gita_translations)", null).use {
                if (it.moveToFirst()) Pack(it.getString(0), it.getString(1), it.getString(2) ?: "", it.getString(3) ?: "", it.getInt(4), emptyList()) else null
            } ?: return null
            val rows = old.rawQuery(
                "SELECT v.chapter, v.verse_start, t.page, t.text, COALESCE(c.text,'') FROM gita_translations t JOIN gita_verses v ON v.id = t.verse_id LEFT JOIN gita_commentary c ON c.verse_id = t.verse_id AND c.source_key = t.source_key WHERE t.source_key = ?",
                arrayOf(src.key)
            ).use { c -> buildList { while (c.moveToNext()) add(PackRow(c.getInt(0), c.getInt(1), c.intOrNull(2), c.getString(3), c.getString(4))) } }
            return src.copy(rows = rows)
        }
    }

    private fun Cursor.intOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)

    companion object { private const val TAG = "ScriptureDb" }
}
