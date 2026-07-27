package com.smartreminder.core.nlp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpokenNumbersTest {

    private fun norm(s: String) = SpokenNumbers.normalize(s)

    @Test
    fun `single digits`() {
        assertEquals("meeting at 3 pm", norm("meeting at three pm"))
    }

    @Test
    fun `tens plus unit compound`() {
        assertEquals("in 25 minutes", norm("in twenty five minutes"))
    }

    @Test
    fun `unit then ten splits into two numbers - clock time`() {
        assertEquals("3 55 pm", norm("three fifty five pm"))
    }

    @Test
    fun `hour then round minutes`() {
        assertEquals("3 30", norm("three thirty"))
    }

    @Test
    fun `teens`() {
        assertEquals("at 15", norm("at fifteen"))
    }

    @Test
    fun `non-number words pass through`() {
        assertEquals("i have a meeting today", norm("i have a meeting today"))
    }

    @Test
    fun `existing digits untouched`() {
        assertEquals("meeting tomorrow 3pm", norm("meeting tomorrow 3pm"))
    }

    @Test
    fun `mixed sentence`() {
        assertEquals("call john today 3 55 pm", norm("call john today three fifty five pm"))
    }
}
