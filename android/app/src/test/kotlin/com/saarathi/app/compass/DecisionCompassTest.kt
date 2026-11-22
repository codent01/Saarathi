package com.saarathi.app.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionCompassTest {
    @Test fun hasSevenStepsEndingInReflection() {
        assertEquals(7, DecisionCompass.steps.size)
        assertEquals(listOf("situation", "motivations", "fears", "responsibilities", "consequences", "principles", "next"), DecisionCompass.steps.map { it.key })
        val last = DecisionCompass.steps.last()
        assertTrue(last.hint.contains("not a verdict"))
        assertTrue(last.hint.contains("decision stays yours"))
    }

    @Test fun composedMessageKeepsOrderAndSkipsBlanks() {
        val msg = DecisionCompass.compose(mapOf(
            "situation" to "Leave my job for a startup offer\nor stay.",
            "fears" to "  ", "principles" to "Honesty with my family",
            "next" to "I notice the fear is mostly about what my father will say",
        ))
        assertTrue(msg.startsWith(DecisionCompass.OPENING))
        val iSituation = msg.indexOf("The decision: Leave my job for a startup offer or stay.")
        val iPrinciples = msg.indexOf("Principles I want to honour: Honesty with my family")
        val iNext = msg.indexOf("What I notice now: I notice the fear")
        assertTrue(iSituation in 0 until iPrinciples)
        assertTrue(iPrinciples < iNext)
        assertFalse(msg.contains("My fears and assumptions"))
        assertTrue(msg.contains("The decision is mine to make"))
    }

    @Test fun titleComesFromTheSituation() {
        assertEquals("Decision Compass", DecisionCompass.title(emptyMap()))
        assertEquals("Compass: Move to Pune", DecisionCompass.title(mapOf("situation" to "  Move to   Pune ")))
        assertTrue(DecisionCompass.title(mapOf("situation" to "a".repeat(80))).endsWith("…"))
        assertEquals(2, DecisionCompass.answered(mapOf("situation" to "x", "next" to "y", "fears" to "")))
    }
}
