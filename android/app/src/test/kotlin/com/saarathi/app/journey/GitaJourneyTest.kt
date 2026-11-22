package com.saarathi.app.journey

import com.saarathi.app.data.db.ChapterReadCount
import com.saarathi.app.data.scripture.GitaChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitaJourneyTest {
    // Themes exactly as listed in tools/scripture-compiler/chapters.json.
    private val chapterThemes = listOf(
        listOf("fear", "grief", "doubt", "attachment", "duty"),
        listOf("soul", "death", "equanimity", "action", "detachment", "mind"),
        listOf("action", "duty", "desire", "anger", "leadership", "ego"),
        listOf("knowledge", "faith", "doubt", "action", "surrender"),
        listOf("detachment", "equanimity", "peace", "happiness", "compassion"),
        listOf("mind", "meditation", "discipline", "lifestyle", "failure", "patience"),
        listOf("faith", "knowledge", "desire", "devotion"),
        listOf("death", "mind", "devotion", "remembrance"),
        listOf("devotion", "faith", "forgiveness", "simplicity"),
        listOf("knowledge", "humility", "wonder", "faith"),
        listOf("time", "surrender", "humility", "fear", "wonder"),
        listOf("devotion", "equanimity", "compassion", "patience", "relationships"),
        listOf("knowledge", "humility", "detachment", "soul"),
        listOf("gunas", "habit", "lifestyle", "mind", "equanimity"),
        listOf("attachment", "detachment", "soul", "knowledge"),
        listOf("divine_qualities", "ego", "anger", "greed", "desire", "lifestyle"),
        listOf("faith", "speech", "lifestyle", "charity", "discipline"),
        listOf("duty", "surrender", "decision", "action", "happiness", "freedom"),
    )
    private val chapters = chapterThemes.mapIndexed { i, t -> GitaChapter(i + 1, "Chapter ${i + 1}", "", "", t, 40, null, null) }

    @Test fun everyChapterThemeHasAPrompt() {
        for (t in chapterThemes.flatten().distinct()) assertNotNull("no prompt for theme $t", GitaJourney.promptForTheme(t))
    }

    @Test fun eighteenDistinctPromptsOneFromEachChaptersOwnThemes() {
        val prompts = GitaJourney.reflectionPrompts(chapters)
        assertEquals(18, prompts.size)
        assertEquals(18, prompts.values.distinct().size)
        for (ch in chapters) assertTrue("chapter ${ch.chapter}", ch.themes.any { GitaJourney.promptForTheme(it) == prompts[ch.chapter] })
        assertTrue(prompts[1]!!.contains("Arjuna's bow"))
    }

    @Test fun progressUsesRecordCountsAndClamps() {
        val counts = mapOf(1 to 46, 2 to 72) // 1.21-22 and 1.32-35 are single records
        val p = GitaJourney.progress(chapters.take(2), counts, listOf(ChapterReadCount(1, 50), ChapterReadCount(2, 10)))
        assertEquals(46, p[0].total); assertEquals(46, p[0].read); assertTrue(p[0].complete)
        assertEquals(72, p[1].total); assertEquals(10, p[1].read); assertTrue(p[1].started); assertEquals(10f / 72f, p[1].fraction, 1e-6f)
        val all = GitaJourney.overall(p)
        assertEquals(56, all.read); assertEquals(118, all.total)
        val none = GitaJourney.progress(chapters.take(1), emptyMap(), emptyList())[0]
        assertEquals(40, none.total); assertEquals(0f, none.fraction, 0f)
    }
}
