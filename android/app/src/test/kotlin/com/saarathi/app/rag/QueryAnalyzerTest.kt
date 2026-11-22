package com.saarathi.app.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QueryAnalyzerTest {
    private val lexicon: Lexicon by lazy {
        val file = listOf("src/main/assets/lexicon.json", "app/src/main/assets/lexicon.json").map { File(it) }.first { it.exists() }
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val themes = LinkedHashMap<String, Lexicon.Theme>()
        for ((key, el) in root["themes"]!!.jsonObject) {
            if (key.startsWith("_")) continue
            val o = el.jsonObject
            themes[key] = Lexicon.Theme(key, o["label"]!!.jsonPrimitive.content, o["triggers"]!!.jsonArray.map { it.jsonPrimitive.content.lowercase() })
        }
        fun list(name: String) = root[name]!!.jsonArray.map { it.jsonPrimitive.content.lowercase() }
        Lexicon(themes, list("casual"), list("scripture_question"), list("crisis"))
    }
    private val analyzer by lazy { QueryAnalyzer(lexicon) }

    @Test fun emotionalMessageIsSupportWithThemes() {
        val a = analyzer.analyze("Krishna, I am working very hard but nothing is going right. I feel like giving up.")
        assertEquals(Intent.SUPPORT, a.intent)
        assertTrue(a.topThemes.contains("depression") || a.topThemes.contains("failure") || a.topThemes.contains("work"))
    }

    @Test fun hinglishFearIsDetected() {
        val a = analyzer.analyze("mujhe bahut dar lagta hai exam ka")
        assertEquals(Intent.SUPPORT, a.intent)
        assertTrue(a.topThemes.contains("fear"))
    }

    @Test fun greetingIsCasual() {
        assertEquals(Intent.CASUAL, analyzer.analyze("Hare Krishna!").intent)
        assertEquals(Intent.CASUAL, analyzer.analyze("hi").intent)
    }

    @Test fun explicitVerseReferenceIsScripture() {
        val a = analyzer.analyze("What does chapter 2 verse 47 mean?")
        assertEquals(Intent.SCRIPTURE, a.intent)
        assertEquals(listOf(2 to 47), a.explicitRefs)
        val b = analyzer.analyze("explain gita 18.66")
        assertEquals(listOf(18 to 66), b.explicitRefs)
    }

    @Test fun characterQuestionIsScripture() {
        val a = analyzer.analyze("Why did Karna fight for Duryodhana?")
        assertEquals(Intent.SCRIPTURE, a.intent)
        assertTrue(a.characterNames.containsAll(listOf("karna", "duryodhana")))
    }

    @Test fun crisisIsFlagged() {
        assertEquals(Intent.CRISIS, analyzer.analyze("I don't want to live anymore").intent)
        assertEquals(Intent.CRISIS, analyzer.analyze("mujhe jeena nahi chahta").intent)
    }

    @Test fun shortFollowUpInheritsThemes() {
        val a = analyzer.analyze("but what if I fail?", previousThemes = listOf("work", "fear"))
        assertTrue(a.topThemes.contains("failure"))
        assertTrue(a.topThemes.contains("work") || a.topThemes.contains("fear"))
    }
}
