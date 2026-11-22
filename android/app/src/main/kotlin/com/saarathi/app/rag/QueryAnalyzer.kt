package com.saarathi.app.rag

enum class Intent { CASUAL, SUPPORT, SCRIPTURE, GENERAL, CRISIS }

data class QueryAnalysis(
    val intent: Intent,
    /** theme key -> score, strongest first */
    val themes: List<Pair<String, Double>>,
    val keywords: List<String>,
    /** explicit chapter/verse mentions like "2.47" or "chapter 2 verse 47" */
    val explicitRefs: List<Pair<Int, Int>>,
    val characterNames: List<String>,
    val wordCount: Int,
) {
    val topThemes: List<String> get() = themes.take(4).map { it.first }
}

/**
 * Rule-based understanding of the user's message. Deliberately simple and fully offline:
 * the small language model gets a much better retrieval context this way than from asking it
 * to classify the message itself, and it costs nothing in latency.
 */
class QueryAnalyzer(private val lexicon: Lexicon) {

    private val refPatterns = listOf(
        Regex("""chapter\s*(\d{1,2})\D{0,15}?(?:verse|shlok|sloka|shloka|text)\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""(?:gita|geeta|bg)\s*(\d{1,2})[.:](\d{1,2})""", RegexOption.IGNORE_CASE),
        Regex("""(?<![\d.])(\d{1,2})[.:](\d{1,2})(?![\d.])"""),
    )

    private val characters = listOf(
        "arjuna", "arjun", "karna", "karn", "bhishma", "bheeshma", "drona", "dronacharya", "duryodhana", "duryodhan",
        "yudhishthira", "yudhishthir", "yudhisthira", "bhima", "bheem", "nakula", "sahadeva", "draupadi", "kunti",
        "pandavas", "pandav", "kauravas", "kaurav", "abhimanyu", "ekalavya", "eklavya", "shakuni", "vidura", "vidur",
        // "krishna" is deliberately absent: the user addresses Krishna in almost every message.
        "gandhari", "dhritarashtra", "sanjaya", "sanjay", "vyasa", "vyas", "radha", "balarama", "ghatotkacha",
        "ashwatthama", "aswatthaman", "shikhandi", "parashurama", "drupada", "satyavati", "pandu", "amba", "jayadratha",
        "sudama", "uddhava", "subhadra", "hidimba", "kripa", "shalya", "yudhishtira"
    )

    private val stopwords = setOf(
        "i", "me", "my", "myself", "we", "our", "you", "your", "he", "she", "it", "they", "them", "his", "her", "its",
        "am", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "a", "an", "the",
        "and", "but", "if", "or", "because", "as", "of", "at", "by", "for", "with", "about", "to", "from", "in", "out",
        "on", "off", "so", "than", "too", "very", "can", "will", "just", "should", "could", "would", "not", "no", "nor",
        "what", "which", "who", "whom", "this", "that", "these", "those", "there", "here", "when", "where", "why", "how",
        "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "only", "own", "same", "then", "also",
        "krishna", "hai", "hain", "ho", "hun", "hoon", "mera", "meri", "mere", "mujhe", "main", "mai", "kya", "ki", "ka", "ke",
        "ko", "se", "par", "pe", "aur", "ya", "nahi", "nahin", "bhi", "toh", "to", "yeh", "ye", "woh", "wo", "kuch", "bahut",
        "bohot", "please", "tell", "help", "feel", "feeling", "like", "want", "know", "think", "really", "get", "got", "im",
        "dont", "cant", "thing", "things", "something", "someone", "always", "never", "ever", "much", "many", "lot",
    )

    fun analyze(text: String, previousThemes: List<String> = emptyList()): QueryAnalysis {
        val words = lexicon.normalize(text).split(' ').filter { it.isNotBlank() }
        val wordCount = words.size

        val explicitRefs = refPatterns.flatMap { p ->
            p.findAll(text).mapNotNull { m ->
                val c = m.groupValues[1].toIntOrNull(); val v = m.groupValues[2].toIntOrNull()
                if (c != null && v != null && c in 1..18 && v in 1..78) c to v else null
            }.toList()
        }.distinct()

        val names = lexicon.matchedTriggers(text, characters)
        val themeScores = lexicon.themeScores(text).toMutableMap()
        // Short follow-ups ("but what if I fail?") inherit the thread's themes at half strength.
        if (wordCount <= 8) for (t in previousThemes.take(3)) themeScores[t] = (themeScores[t] ?: 0.0) + 0.5
        val themes = themeScores.entries.sortedByDescending { it.value }.map { it.key to it.value }

        val keywords = words.filter { it.length > 2 && it !in stopwords }.distinct().take(12)

        val intent = when {
            lexicon.matchesAny(text, lexicon.crisis) -> Intent.CRISIS
            explicitRefs.isNotEmpty() -> Intent.SCRIPTURE
            // greetings like "Hare Krishna" or "Jai Shri Krishna" are small talk, not scripture questions
            wordCount <= 5 && themes.isEmpty() && lexicon.matchesAny(text, lexicon.casual) -> Intent.CASUAL
            lexicon.matchesAny(text, lexicon.scriptureQuestion) && (names.isNotEmpty() || themes.isEmpty() || wordCount <= 12) -> Intent.SCRIPTURE
            names.isNotEmpty() && themes.isEmpty() -> Intent.SCRIPTURE
            themes.isNotEmpty() && (themes.first().second >= 1.0 || wordCount > 6) -> Intent.SUPPORT
            wordCount <= 5 && lexicon.matchesAny(text, lexicon.casual) -> Intent.CASUAL
            wordCount <= 3 -> Intent.CASUAL
            else -> Intent.GENERAL
        }
        return QueryAnalysis(intent, themes, keywords, explicitRefs, names, wordCount)
    }
}
