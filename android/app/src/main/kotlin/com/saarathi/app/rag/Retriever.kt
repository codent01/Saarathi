package com.saarathi.app.rag

import com.saarathi.app.data.scripture.GitaVerse
import com.saarathi.app.data.scripture.MbPassage
import com.saarathi.app.data.scripture.ScriptureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScoredVerse(val card: ScriptureRepository.VerseCard, val score: Double, val reasons: List<String>)

data class RetrievalResult(
    val verses: List<ScoredVerse>,
    val passages: List<MbPassage>,
    val themes: List<String>,
) {
    companion object { val EMPTY = RetrievalResult(emptyList(), emptyList(), emptyList()) }
}

/**
 * Hybrid retrieval over the bundled scripture database:
 *  1. curated theme tags (strongest signal for emotional messages),
 *  2. FTS keyword search over translations and notes,
 *  3. explicit verse references the user typed.
 * Mahabharata passages are only pulled in for scripture questions or when a character is named,
 * so an ordinary "I feel lost" never gets a random war passage attached.
 */
class Retriever(private val scripture: ScriptureRepository, private val lexicon: Lexicon, private val embedder: Embedder? = null) {

    suspend fun retrieve(analysis: QueryAnalysis, text: String): RetrievalResult {
        if (analysis.intent == Intent.CASUAL) return RetrievalResult.EMPTY

        val scores = HashMap<Int, Double>()
        val reasons = HashMap<Int, MutableList<String>>()
        val verseById = HashMap<Int, GitaVerse>()
        fun bump(v: GitaVerse, delta: Double, why: String) {
            verseById[v.id] = v
            scores[v.id] = (scores[v.id] ?: 0.0) + delta
            reasons.getOrPut(v.id) { mutableListOf() }.add(why)
        }

        // 1. explicit references always win
        for ((c, v) in analysis.explicitRefs) {
            scripture.verseExists(c, v)?.let { bump(it, 10.0, "asked for $c.$v") }
        }

        // 2. curated themes
        val themeWeights = analysis.themes.take(5).toMap()
        if (themeWeights.isNotEmpty()) {
            for (v in scripture.versesWithThemes(themeWeights.keys)) {
                val matched = v.themes.filter { it in themeWeights }
                bump(v, RetrievalScoring.themeScore(themeWeights, v.themes, v.weight), "themes " + matched.joinToString(","))
            }
        }

        // 2b. embedding similarity (re-ranks; never the only signal unless nothing else matched)
        val queryVec = embedder?.takeIf { it.available }?.let { e -> withContext(Dispatchers.Default) { e.embed(text) } }
        if (queryVec != null) {
            val vectors = scripture.verseEmbeddings()
            val top = vectors.entries.asSequence()
                .map { (id, vec) -> id to Embedder.cosine(queryVec, vec) }
                .filter { it.second > 0.30f }
                .sortedByDescending { it.second }
                .take(12).toList()
            for ((id, cos) in top) {
                val v = verseById[id] ?: scripture.verse(id) ?: continue
                bump(v, RetrievalScoring.embeddingScore(cos, v.weight), "embedding %.2f".format(cos))
            }
        }

        // 3. keyword search (FTS4) over translations and curated notes
        val ftsQuery = buildFtsQuery(analysis.keywords)
        if (ftsQuery != null) {
            val ids = scripture.ftsGita(ftsQuery, 25)
            ids.forEachIndexed { rank, id ->
                val v = verseById[id] ?: scripture.verse(id) ?: return@forEachIndexed
                bump(v, RetrievalScoring.keywordScore(rank, v.weight), "keywords")
            }
        }

        val limit = if (analysis.intent == Intent.SCRIPTURE) 4 else 3
        val ranked = scores.entries.sortedByDescending { it.value }
        val chosen = ArrayList<GitaVerse>()
        val perChapter = HashMap<Int, Int>()
        for ((id, _) in ranked) {
            val v = verseById[id] ?: continue
            val n = perChapter[v.chapter] ?: 0
            if (n >= 2 && analysis.explicitRefs.none { it.first == v.chapter }) continue
            perChapter[v.chapter] = n + 1
            chosen += v
            if (chosen.size >= limit) break
        }
        val verses = chosen.mapNotNull { v ->
            scripture.card(v.id)?.let { ScoredVerse(it, scores[v.id] ?: 0.0, reasons[v.id] ?: emptyList()) }
        }

        // 4. Mahabharata passages only when the question is about the epic itself
        // Mahabharata passages only for questions about the epic itself. A question about a Gita verse
        // (explicit reference, no character named) stays with the Gita: shorter prompt, faster first token.
        val passages = if (analysis.characterNames.isNotEmpty() || (analysis.intent == Intent.SCRIPTURE && analysis.explicitRefs.isEmpty())) {
            retrievePassages(analysis)
        } else emptyList()

        return RetrievalResult(verses, passages, analysis.topThemes)
    }

    private suspend fun retrievePassages(analysis: QueryAnalysis): List<MbPassage> {
        val names = analysis.characterNames.map { canonicalName(it) }.distinct()
        val terms = (names + analysis.keywords.filter { it !in names }).take(8)
        if (terms.isEmpty()) return emptyList()
        // names must all appear; other keywords are optional
        val required = names.joinToString(" ") { quote(it) }
        val optional = analysis.keywords.filter { it !in names && it.length > 3 }.take(5)
        val queries = buildList {
            if (required.isNotBlank() && optional.isNotEmpty()) add("$required ${optional.joinToString(" OR ") { quote(it) }}")
            if (required.isNotBlank()) add(required)
            if (optional.isNotEmpty()) add(optional.joinToString(" OR ") { quote(it) })
        }
        for (q in queries) {
            val ids = scripture.ftsMahabharata(q, 20)
            if (ids.isEmpty()) continue
            val passages = scripture.passages(ids)
            val termSet = terms.toSet()
            val scored = passages.map { p ->
                val lower = p.text.lowercase()
                val density = termSet.count { lower.contains(it) }.toDouble() / termSet.size
                val lengthPenalty = if (p.text.length > 1800) 0.7 else 1.0
                val gitaBonus = if (p.gitaChapter != null) 0.1 else 0.0
                // Adi Parva sections I–II are the epic's own table of contents; they mention everyone and explain nothing.
                val summaryPenalty = if (p.book == 1 && p.section <= 2) 0.4 else 1.0
                p to (density * lengthPenalty * summaryPenalty + gitaBonus)
            }.sortedByDescending { it.second }
            return scored.take(2).map { it.first }
        }
        return emptyList()
    }

    private fun canonicalName(n: String): String = when (n) {
        "arjun" -> "arjuna"; "karn" -> "karna"; "bheeshma" -> "bhishma"; "dronacharya" -> "drona"
        "duryodhan" -> "duryodhana"; "yudhishthir", "yudhisthira", "yudhishtira" -> "yudhishthira"; "bheem" -> "bhima"
        "pandav" -> "pandavas"; "kaurav" -> "kauravas"; "eklavya" -> "ekalavya"; "vidur" -> "vidura"; "sanjay" -> "sanjaya"
        "vyas" -> "vyasa"; "ashwatthama" -> "aswatthaman"
        else -> n
    }

    private fun quote(term: String) = "\"" + term.replace("\"", "") + "\""

    private fun buildFtsQuery(keywords: List<String>): String? {
        val terms = keywords.filter { it.length > 2 }.take(8)
        if (terms.isEmpty()) return null
        return terms.joinToString(" OR ") { quote(it) }
    }
}
