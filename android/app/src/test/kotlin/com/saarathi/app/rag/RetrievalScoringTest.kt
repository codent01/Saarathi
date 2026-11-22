package com.saarathi.app.rag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalScoringTest {
    @Test fun themeOverlapOutranksSingleKeywordHit() {
        val msg = mapOf("fear" to 2.0, "work" to 1.0)
        val theme = RetrievalScoring.themeScore(msg, listOf("fear", "work", "action"), 1.0)
        val keyword = RetrievalScoring.keywordScore(0, 1.0)
        assertTrue("themes $theme should beat keyword $keyword", theme > keyword)
    }

    @Test fun noThemeOverlapScoresZero() {
        assertEquals(0.0, RetrievalScoring.themeScore(mapOf("fear" to 1.0), listOf("charity"), 1.0), 1e-9)
        assertEquals(0.0, RetrievalScoring.themeScore(emptyMap(), listOf("fear"), 1.0), 1e-9)
    }

    @Test fun embeddingBelowFloorAddsNothingAndStrongMatchReranks() {
        assertEquals(0.0, RetrievalScoring.embeddingScore(0.2f, 1.0), 1e-9)
        val strong = RetrievalScoring.embeddingScore(0.7f, 1.0)
        val weak = RetrievalScoring.embeddingScore(0.35f, 1.0)
        assertTrue(strong > weak)
        // a very strong embedding match is worth about one theme match, never more than two
        val oneTheme = RetrievalScoring.themeScore(mapOf("fear" to 1.0), listOf("fear"), 1.0)
        assertTrue(strong < 2 * oneTheme)
    }

    @Test fun tokenizerMatchesBertConventions() {
        val vocab = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "i", "am", "afraid", "of", "fail", "##ing", ",", "job")
        val tok = WordPieceTokenizer(vocab)
        val ids = tok.encode("I am afraid of failing, job").toList()
        assertEquals(listOf(2, 4, 5, 6, 7, 8, 9, 10, 11, 3), ids)
        assertEquals(listOf(2, 1, 3), tok.encode("zzz").toList())
    }
}
