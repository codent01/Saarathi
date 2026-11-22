package com.saarathi.app.journey

import com.saarathi.app.data.db.ChapterReadCount
import com.saarathi.app.data.scripture.GitaChapter

/** 18-chapter journey: per-chapter progress maths and one reflection prompt per chapter built from its themes. */
object GitaJourney {
    data class Progress(val chapter: Int, val read: Int, val total: Int) {
        val fraction: Float get() = if (total <= 0) 0f else (read.toFloat() / total).coerceIn(0f, 1f)
        val complete: Boolean get() = total > 0 && read >= total
        val started: Boolean get() = read > 0
    }

    /** Verse records are counted (a combined verse like 1.21-22 is one record), so totals come from the reader, not chapters.json. */
    fun progress(chapters: List<GitaChapter>, recordCounts: Map<Int, Int>, reads: List<ChapterReadCount>): List<Progress> {
        val byChapter = reads.associate { it.chapter to it.count }
        return chapters.map { ch ->
            val total = recordCounts[ch.chapter] ?: ch.verseCount
            Progress(ch.chapter, (byChapter[ch.chapter] ?: 0).coerceAtMost(total), total)
        }
    }

    fun overall(progress: List<Progress>): Progress =
        Progress(0, progress.sumOf { it.read }, progress.sumOf { it.total })

    private val prompts = mapOf(
        "fear" to "Arjuna's bow slipped from his hand. What is the thing you have put down because fear told you to?",
        "grief" to "What loss are you still carrying as if it happened this morning?",
        "doubt" to "Which doubt of yours deserves a real answer, and which one is just a way to delay?",
        "attachment" to "What are you holding so tightly that it has started holding you?",
        "duty" to "What is the one duty you know is yours today, and what would doing it cost you?",
        "soul" to "If the body is the cloth and you are the wearer, what in you has not changed since childhood?",
        "death" to "If this day were one of a limited number, what would you stop postponing?",
        "equanimity" to "What praise or criticism this week moved you more than it deserved?",
        "action" to "Which action can you do fully today without needing a particular result?",
        "detachment" to "Where did your effort end and your worry about the outcome begin?",
        "mind" to "Where did your mind wander most today, and what was it looking for?",
        "desire" to "Which wish, if it came true today, would leave you wanting something else tomorrow?",
        "anger" to "When you were last angry, what did you want that you were not getting?",
        "leadership" to "Who watches what you do and quietly copies it? What are they learning?",
        "ego" to "Where did the word I get in the way of what actually needed doing?",
        "knowledge" to "What belief about yourself have you never actually examined?",
        "faith" to "What do you already know to be right but are waiting for permission to do?",
        "surrender" to "What would change if you did your part today and then let the rest go?",
        "peace" to "When were you last quietly at peace with nothing to show for it, and what made it possible?",
        "happiness" to "What made you content today that no one else could see?",
        "compassion" to "Who around you is carrying something heavy that you have not noticed?",
        "meditation" to "Can you sit for five minutes with nothing to fix? What comes up first?",
        "discipline" to "What is one promise to yourself you can keep for the next 24 hours?",
        "lifestyle" to "Which daily habit is quietly shaping the person you are becoming?",
        "failure" to "What did your last setback teach you that success could not have?",
        "patience" to "What are you rushing that would be better if you let it take its time?",
        "devotion" to "What do you give yourself to without counting the cost?",
        "remembrance" to "What do you want to be remembering at the end of a hard day?",
        "forgiveness" to "Whose mistake are you still holding, and what is it costing you to hold it?",
        "simplicity" to "What small, simple offering could you make today without anyone noticing?",
        "humility" to "What did you learn this week from someone you did not expect to learn from?",
        "wonder" to "When did something last stop you in your tracks? What did it show you?",
        "time" to "Time takes everything eventually. What matters enough to do while it is still yours?",
        "relationships" to "Who in your life gets your patience, and who gets only what is left over?",
        "gunas" to "Which mood ran your day: restlessness, dullness or clarity? What fed it?",
        "habit" to "Which habit would your future self thank you for starting, or stopping, this week?",
        "divine_qualities" to "Which quality in you, fearlessness, honesty, gentleness, needs a little more room?",
        "greed" to "What do you already have enough of but keep reaching for?",
        "speech" to "Was there something you said today that was true but not kind, or kind but not true?",
        "charity" to "What have you given lately, and was it given freely or to be seen?",
        "decision" to "What decision are you circling? What would you tell a friend in your place?",
        "freedom" to "Having heard everything, what will you choose, and can you own it fully?",
    )

    private const val FALLBACK = "What is one line from this chapter you could carry with you today?"

    fun promptForTheme(theme: String): String? = prompts[theme]

    /**
     * Picks each chapter's prompt from its own themes, choosing themes so that no two chapters share a prompt
     * (a small backtracking search over 18 chapters; chapters.json themes overlap a lot). If no distinct
     * assignment exists the chapter's first known theme is used, and a generic question if it has none.
     */
    fun reflectionPrompts(chapters: List<GitaChapter>): Map<Int, String> {
        val ordered = chapters.sortedBy { it.chapter }
        val candidates = ordered.map { ch -> ch.themes.filter { it in prompts } }
        val chosen = arrayOfNulls<String>(ordered.size)
        fun assign(i: Int, used: MutableSet<String>): Boolean {
            if (i == ordered.size) return true
            if (candidates[i].isEmpty()) return assign(i + 1, used)
            for (t in candidates[i]) {
                if (t in used) continue
                used.add(t); chosen[i] = t
                if (assign(i + 1, used)) return true
                used.remove(t); chosen[i] = null
            }
            return false
        }
        if (!assign(0, HashSet())) {
            // fall back to the greedy pick when distinctness is impossible
            val used = HashSet<String>()
            for (i in ordered.indices) chosen[i] = (candidates[i].firstOrNull { it !in used } ?: candidates[i].firstOrNull())?.also { used.add(it) }
        }
        val out = LinkedHashMap<Int, String>()
        for ((i, ch) in ordered.withIndex()) out[ch.chapter] = chosen[i]?.let { prompts[it] } ?: FALLBACK
        return out
    }
}
