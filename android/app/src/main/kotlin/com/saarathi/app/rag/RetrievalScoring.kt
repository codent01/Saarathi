package com.saarathi.app.rag

/**
 * Pure scoring rules shared by the retriever and its unit tests.
 * Curated themes remain the primary signal; keyword and embedding similarity re-rank.
 */
object RetrievalScoring {
    /** Theme overlap: share of the message's theme weight that this verse covers, scaled by the verse weight. */
    fun themeScore(messageThemes: Map<String, Double>, verseThemes: List<String>, verseWeight: Double): Double {
        if (messageThemes.isEmpty()) return 0.0
        val total = messageThemes.values.sum().coerceAtLeast(1.0)
        val matched = verseThemes.filter { it in messageThemes }
        if (matched.isEmpty()) return 0.0
        var s = matched.sumOf { (messageThemes[it] ?: 0.0) / total } * 2.0
        if (matched.size > 1) s += 0.4 * (matched.size - 1)
        return s * (0.6 + verseWeight)
    }

    /** Keyword hit from FTS, decaying with rank. */
    fun keywordScore(rank: Int, verseWeight: Double): Double = 0.9 * (1.0 - rank / 40.0).coerceAtLeast(0.2) * (0.6 + verseWeight)

    /**
     * Embedding similarity. Cosine values for unrelated sentences sit around 0.1–0.3 with MiniLM,
     * so anything below the floor contributes nothing and strong matches add up to ~1.6.
     */
    fun embeddingScore(cosine: Float, verseWeight: Double, floor: Float = 0.30f): Double {
        val above = (cosine - floor).coerceAtLeast(0f)
        return above * 4.0 * (0.6 + verseWeight)
    }
}
