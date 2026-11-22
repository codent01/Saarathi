package com.saarathi.app.karma

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KarmaStepsTest {
    @Test fun numberedListIsExtracted() {
        val reply = """
            I hear you, Vats. Try this for the next few days:
            1. Write down the one task you keep avoiding.
            2. Do only the first ten minutes of it, then stop.
            3. Tell one person what you did.
            The result is not yours to control, only the effort.
        """.trimIndent()
        assertEquals(listOf("Write down the one task you keep avoiding", "Do only the first ten minutes of it, then stop", "Tell one person what you did"), KarmaSteps.extract(reply))
    }

    @Test fun dashedAndBulletListsAreExtracted() {
        val dashed = "Two small things:\n- Walk for ten minutes before you reply to him\n- Write the message, but send it tomorrow\n"
        assertEquals(listOf("Walk for ten minutes before you reply to him", "Write the message, but send it tomorrow"), KarmaSteps.extract(dashed))
        val bullets = "• Breathe slowly three times.\n• Say what you need in one sentence."
        assertEquals(2, KarmaSteps.extract(bullets).size)
    }

    @Test fun inlineNumberedListInOneParagraphIsExtracted() {
        val reply = "Here is what I would do: 1. Sleep before midnight tonight 2. Eat breakfast away from the phone 3. Call your mother. Then we talk again."
        val steps = KarmaSteps.extract(reply)
        assertEquals(3, steps.size)
        assertEquals("Sleep before midnight tonight", steps[0])
        assertTrue(steps[2].startsWith("Call your mother"))
    }

    @Test fun proseWithoutAListGivesNothing() {
        assertTrue(KarmaSteps.extract("Arjuna, fear is not a verdict. Do your part today and let the fruit come when it comes.").isEmpty())
        assertTrue(KarmaSteps.extract("This is what I meant in Gita 2.47 when I spoke of action without attachment.").isEmpty())
        assertTrue(KarmaSteps.extract("").isEmpty())
    }

    @Test fun aSingleItemIsNotAList() {
        assertTrue(KarmaSteps.extract("One thing:\n1. Sleep.").isEmpty())
    }

    @Test fun listsAreCappedAndCleaned() {
        val long = (1..10).joinToString("\n") { "$it. **Step $it** of the plan." }
        val steps = KarmaSteps.extract(long)
        assertEquals(KarmaSteps.MAX_STEPS, steps.size)
        assertEquals("Step 1 of the plan", steps[0])
    }
}
