package com.saarathi.app.karma

/**
 * Finds an explicit short list of steps in a reply so the user can keep it as Karma Steps.
 * Only numbered ("1.", "1)", "Step 1:") or dashed ("-", "•", "*") lists count; prose advice is left alone.
 */
object KarmaSteps {
    const val MIN_STEPS = 2
    const val MAX_STEPS = 7
    private const val MAX_STEP_CHARS = 180

    private val lineMarker = Regex("""^\s*(?:(?:step\s*)?(\d{1,2})\s*[.):\-]|[-•*–]|•)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val inlineNumbered = Regex("""(?:^|\s)(\d{1,2})[.)]\s+""")

    fun extract(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val fromLines = extractLines(text)
        if (fromLines.size >= MIN_STEPS) return fromLines.take(MAX_STEPS)
        val inline = extractInline(text)
        if (inline.size >= MIN_STEPS) return inline.take(MAX_STEPS)
        return emptyList()
    }

    /** One step per line, keeping only the longest contiguous run of marked lines. */
    private fun extractLines(text: String): List<String> {
        var best = emptyList<String>()
        var run = ArrayList<String>()
        for (raw in text.lines()) {
            val m = lineMarker.find(raw)
            if (m != null) {
                val step = clean(m.groupValues[2])
                if (step.isNotBlank()) { run.add(step); continue }
            }
            if (raw.isBlank() && run.isNotEmpty()) continue // blank lines between items do not break the list
            if (run.size > best.size) best = run
            run = ArrayList()
        }
        if (run.size > best.size) best = run
        return best
    }

    /** "1. Breathe 2. Walk 3. Call her" written in one paragraph. */
    private fun extractInline(text: String): List<String> {
        val matches = inlineNumbered.findAll(text).toList()
        if (matches.size < MIN_STEPS) return emptyList()
        // require the numbers to count up from 1
        val numbers = matches.map { it.groupValues[1].toInt() }
        if (numbers.withIndex().any { (i, n) -> n != i + 1 }) return emptyList()
        val out = ArrayList<String>()
        for ((i, m) in matches.withIndex()) {
            val start = m.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val step = clean(text.substring(start, end).lineSequence().first())
            if (step.isNotBlank()) out.add(step)
        }
        return out
    }

    private fun clean(s: String): String {
        var t = s.trim().replace(Regex("""\*\*|__"""), "").trim()
        t = t.trimEnd('.', ';', ',').trim()
        if (t.length > MAX_STEP_CHARS) t = t.take(MAX_STEP_CHARS - 1).trimEnd() + "…"
        return t
    }
}
