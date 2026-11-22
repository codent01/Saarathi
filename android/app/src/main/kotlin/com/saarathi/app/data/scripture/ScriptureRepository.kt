package com.saarathi.app.data.scripture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the rest of the app needs from scripture, with a preferred-translation policy. */
class ScriptureRepository(private val db: ScriptureDatabase) {

    /** Imported edition first (the user's own book), then the bundled public-domain rendering. */
    private val translationPreference = listOf("as_it_is", "besant1922")

    data class VerseCard(
        val verse: GitaVerse,
        val translation: Translation?,
        val otherTranslations: List<Translation>,
        val commentary: Translation?,
        val chapterTitle: String,
    ) {
        val translationLabel: String get() = when (translation?.sourceKey) {
            "as_it_is" -> "Translation: Bhagavad-gītā As It Is (your imported edition)"
            "besant1922" -> "Translation: Annie Besant, 1922 (public domain)"
            null -> "No verified translation available for this verse"
            else -> "Translation: ${translation.sourceKey}"
        }
    }

    suspend fun card(verseId: Int): VerseCard? = withContext(Dispatchers.IO) {
        val verse = db.verse(verseId) ?: return@withContext null
        cardFor(verse)
    }

    suspend fun cardByRef(ref: String): VerseCard? = withContext(Dispatchers.IO) {
        val verse = db.verseByRef(ref) ?: parseRef(ref)?.let { (c, v) -> db.verseAt(c, v) } ?: return@withContext null
        cardFor(verse)
    }

    private fun cardFor(verse: GitaVerse): VerseCard {
        val translations = db.translations(verse.id)
        val preferred = translationPreference.firstNotNullOfOrNull { key -> translations.firstOrNull { it.sourceKey == key } }
            ?: translations.firstOrNull()
        val commentary = db.commentary(verse.id).firstOrNull()
        val chapterTitle = chapters().firstOrNull { it.chapter == verse.chapter }?.title ?: ""
        return VerseCard(verse, preferred, translations.filter { it !== preferred }, commentary, chapterTitle)
    }

    suspend fun verseExists(chapter: Int, verse: Int): GitaVerse? = withContext(Dispatchers.IO) { db.verseAt(chapter, verse) }

    private var chapterCache: List<GitaChapter>? = null
    fun chapters(): List<GitaChapter> = chapterCache ?: db.chapters().also { chapterCache = it }

    suspend fun versesForChapter(chapter: Int): List<GitaVerse> = withContext(Dispatchers.IO) { db.versesForChapter(chapter) }
    suspend fun versesWithThemes(themes: Collection<String>): List<GitaVerse> = withContext(Dispatchers.IO) { db.versesWithThemes(themes) }
    suspend fun weightedVerses(minWeight: Double): List<GitaVerse> = withContext(Dispatchers.IO) { db.versesWithMinWeight(minWeight) }
    suspend fun ftsGita(query: String, limit: Int = 30): List<Int> = withContext(Dispatchers.IO) { db.searchGita(query, limit) }
    suspend fun ftsMahabharata(query: String, limit: Int = 20): List<Int> = withContext(Dispatchers.IO) { db.searchMahabharata(query, limit) }
    suspend fun passages(ids: Collection<Int>): List<MbPassage> = withContext(Dispatchers.IO) { db.passages(ids) }

    @Volatile private var embeddingCache: Map<Int, FloatArray>? = null
    suspend fun verseEmbeddings(): Map<Int, FloatArray> = embeddingCache ?: withContext(Dispatchers.IO) { db.embeddings() }.also { embeddingCache = it }

    data class SearchResults(val verses: List<VerseCard>, val passages: List<MbPassage>)

    /** Free-text search over both scriptures for the reader. */
    suspend fun search(query: String, limit: Int = 20): SearchResults = withContext(Dispatchers.IO) {
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.take(8)
        if (terms.isEmpty()) return@withContext SearchResults(emptyList(), emptyList())
        val fts = terms.joinToString(" OR ") { "\"" + it + "\"" }
        val explicit = parseRef(query.trim())?.let { (c, v) -> db.verseAt(c, v) }
        val verseIds = (listOfNotNull(explicit?.id) + db.searchGita(fts, limit)).distinct().take(limit)
        val verses = verseIds.mapNotNull { id -> db.verse(id)?.let { cardFor(it) } }
        val passages = db.passages(db.searchMahabharata(fts, limit)).sortedByDescending { p ->
            val l = p.text.lowercase(); terms.count { l.contains(it) }
        }.take(limit)
        SearchResults(verses, passages)
    }
    suspend fun passage(id: Int): MbPassage? = withContext(Dispatchers.IO) { db.passage(id) }
    suspend fun passagesForSection(book: Int, section: Int): List<MbPassage> = withContext(Dispatchers.IO) { db.passagesForSection(book, section) }
    suspend fun parvaSummary() = withContext(Dispatchers.IO) { db.parvaSummary() }
    suspend fun sources(): List<ScriptureSource> = withContext(Dispatchers.IO) { db.sources() }
    suspend fun translation(verseId: Int, sourceKey: String): Translation? = withContext(Dispatchers.IO) { db.translations(verseId).firstOrNull { it.sourceKey == sourceKey } }
    suspend fun verse(id: Int): GitaVerse? = withContext(Dispatchers.IO) { db.verse(id) }

    data class Status(val verses: Int, val passages: Int, val editionVerses: Int, val builtAt: String)

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        Status(db.verseCount(), db.passageCount(), db.importedTranslationCount("as_it_is"), db.meta("built_at") ?: "")
    }

    suspend fun importEditionPack(json: String) = withContext(Dispatchers.IO) { db.importEditionPack(json) }

    companion object {
        private val REF = Regex("""^(\d{1,2})[.:](\d{1,2})(?:-(\d{1,2}))?$""")
        fun parseRef(ref: String): Pair<Int, Int>? = REF.find(ref.trim())?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
    }
}
