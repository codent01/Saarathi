package com.saarathi.app.rag

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Offline life-situation lexicon loaded from assets/lexicon.json. */
class Lexicon(
    val themes: Map<String, Theme>,
    val casual: List<String>,
    val scriptureQuestion: List<String>,
    val crisis: List<String>,
) {
    data class Theme(val key: String, val label: String, val triggers: List<String>)

    private val wordRegexCache = HashMap<String, Regex>()

    private fun regexFor(trigger: String): Regex = wordRegexCache.getOrPut(trigger) {
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(trigger) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}'\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Returns theme -> score for the message. Multi-word triggers count more than single words. */
    fun themeScores(text: String): Map<String, Double> {
        val norm = normalize(text)
        if (norm.isEmpty()) return emptyMap()
        val scores = HashMap<String, Double>()
        for ((key, theme) in themes) {
            var s = 0.0
            for (t in theme.triggers) {
                val hits = regexFor(t).findAll(norm).count()
                if (hits > 0) s += hits.coerceAtMost(3) * (if (t.contains(' ')) 1.6 else 1.0)
            }
            if (s > 0) scores[key] = s
        }
        return scores
    }

    fun matchesAny(text: String, triggers: List<String>): Boolean {
        val norm = normalize(text)
        return triggers.any { regexFor(it).containsMatchIn(norm) }
    }

    fun matchedTriggers(text: String, triggers: List<String>): List<String> {
        val norm = normalize(text)
        return triggers.filter { regexFor(it).containsMatchIn(norm) }
    }

    companion object {
        fun load(context: Context): Lexicon {
            val json = context.assets.open("lexicon.json").bufferedReader().readText()
            val root = Json.parseToJsonElement(json).jsonObject
            val themes = LinkedHashMap<String, Theme>()
            for ((key, el) in root["themes"]!!.jsonObject) {
                if (key.startsWith("_")) continue
                val o = el.jsonObject
                themes[key] = Theme(key, o["label"]?.jsonPrimitive?.content ?: key, o["triggers"]!!.jsonArray.map { it.jsonPrimitive.content.lowercase() })
            }
            fun list(name: String) = root[name]?.jsonArray?.map { it.jsonPrimitive.content.lowercase() } ?: emptyList()
            return Lexicon(themes, list("casual"), list("scripture_question"), list("crisis"))
        }
    }
}
