package com.saarathi.app.compass

/**
 * Decision Compass: seven guided questions whose answers become the opening message of one Arjuna-Mode
 * conversation, so Krishna asks before advising. The last step is a reflection, never a verdict.
 */
object DecisionCompass {
    data class Step(val key: String, val title: String, val question: String, val hint: String, val label: String)

    val steps: List<Step> = listOf(
        Step("situation", "The situation", "What decision is in front of you?", "Describe it plainly, as you would to a friend who knows nothing about it. Name the options if there are clear ones.", "The decision"),
        Step("motivations", "What draws you", "What do you actually want here, and why?", "For each option: what pulls you toward it? Be honest about the small, unflattering reasons too.", "What draws me"),
        Step("fears", "Fears and assumptions", "What are you afraid of, and what are you assuming is true?", "Fears often hide as facts. Write the fear, then the assumption underneath it.", "My fears and assumptions"),
        Step("responsibilities", "Responsibilities", "Who or what depends on this choice?", "People, promises, duties, your own health. What do you owe, and to whom?", "My responsibilities"),
        Step("consequences", "Consequences of each option", "If you choose each path, what likely follows?", "Near and far: next week, next year. Include what happens if you do nothing.", "Likely consequences"),
        Step("principles", "Principles that matter", "Which of your values or principles should guide this?", "The ones you would want to have honoured when you look back, not the ones that are convenient now.", "Principles I want to honour"),
        Step("next", "Before you decide", "Having written all this, what do you notice now that you did not see before?", "This is a reflection, not a verdict. If a small, honest next step suggests itself, write it, but you are not deciding yet. The decision stays yours.", "What I notice now"),
    )

    const val OPENING = "I am using the Decision Compass and have written down where I stand. Please read it and ask me what you need to understand before you advise anything. The decision is mine to make; help me see it clearly."

    /** The single message sent to open the Arjuna-Mode conversation. Blank answers are skipped. */
    fun compose(answers: Map<String, String>): String {
        val sb = StringBuilder(OPENING).append("\n")
        for (s in steps) {
            val a = answers[s.key]?.trim().orEmpty()
            if (a.isBlank()) continue
            sb.append("\n").append(s.label).append(": ").append(a.replace(Regex("\\s*\\n\\s*"), " "))
        }
        return sb.toString().trim()
    }

    /** A short title for the conversation list, taken from the situation. */
    fun title(answers: Map<String, String>): String {
        val s = answers["situation"]?.trim().orEmpty().replace(Regex("\\s+"), " ")
        return if (s.isBlank()) "Decision Compass" else "Compass: " + s.take(40).trimEnd() + (if (s.length > 40) "…" else "")
    }

    fun answered(answers: Map<String, String>): Int = steps.count { !answers[it.key].isNullOrBlank() }
}
