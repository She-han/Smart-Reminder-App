package com.smartreminder.core.nlp

import com.smartreminder.core.model.ParsedIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * All cases resolve against a fixed clock so results are deterministic.
 * Reference "now" = Thursday 2026-07-23 10:00.
 */
class ReminderParserTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val clock: Clock = Clock.fixed(
        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
        zone,
    )

    // Uses only the regex rule layer (no platform extractor), which is the offline fallback
    // path and must stand on its own.
    private val parser = ReminderParser(
        extractor = DateTimeEntityExtractor.NONE,
        clock = clock,
        zone = zone,
    )

    private fun parse(text: String) = parser.parse(text)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = LocalDateTime.of(y, mo, d, h, mi)

    @Test
    fun `tomorrow 3pm`() {
        val r = parse("i have a meeting tomorrow 3pm")
        assertEquals(at(2026, 7, 24, 15, 0), r.eventAt)
        assertEquals(ParsedIntent.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `title strips the date phrase and filler`() {
        val r = parse("i have a meeting tomorrow 3pm")
        assertEquals("Meeting", r.title)
    }

    @Test
    fun `bare hour biases to daytime - at 3 means 3pm`() {
        val r = parse("call John at 3")
        assertEquals(at(2026, 7, 23, 15, 0), r.eventAt)
    }

    @Test
    fun `bare hour 9 in the morning window stays today evening if already past`() {
        // 09:00 already passed (now is 10:00), and bare "9" biases PM anyway -> 21:00 today.
        val r = parse("call John at 9")
        assertEquals(at(2026, 7, 23, 21, 0), r.eventAt)
    }

    @Test
    fun `explicit am is respected`() {
        val r = parse("standup at 9am tomorrow")
        assertEquals(at(2026, 7, 24, 9, 0), r.eventAt)
    }

    @Test
    fun `next friday with time`() {
        val r = parse("dentist next Friday 10:30am")
        assertEquals(at(2026, 7, 31, 10, 30), r.eventAt)
    }

    @Test
    fun `plain weekday resolves to the coming occurrence`() {
        // Next Monday after Thu Jul 23 is Jul 27.
        val r = parse("gym on Monday at 6pm")
        assertEquals(at(2026, 7, 27, 18, 0), r.eventAt)
    }

    @Test
    fun `relative offset in hours`() {
        val r = parse("remind me in 2 hours")
        assertEquals(at(2026, 7, 23, 12, 0), r.eventAt)
    }

    @Test
    fun `relative offset in minutes`() {
        val r = parse("check the oven in 45 minutes")
        assertEquals(at(2026, 7, 23, 10, 45), r.eventAt)
    }

    @Test
    fun `day of month with noon`() {
        val r = parse("party on the 25th at noon")
        assertEquals(at(2026, 7, 25, 12, 0), r.eventAt)
    }

    @Test
    fun `tonight resolves to this evening`() {
        val r = parse("dinner tonight at 8")
        assertEquals(at(2026, 7, 23, 20, 0), r.eventAt)
    }

    @Test
    fun `today with explicit time`() {
        val r = parse("submit report today at 5pm")
        assertEquals(at(2026, 7, 23, 17, 0), r.eventAt)
    }

    @Test
    fun `no date or time yields null event and low confidence`() {
        val r = parse("remind me about the thing")
        assertNull(r.eventAt)
        assertEquals(ParsedIntent.Confidence.LOW, r.confidence)
        assertTrue(r.title.isNotBlank())
    }

    @Test
    fun `a past explicit time is rejected as low confidence`() {
        val r = parse("meeting yesterday 3pm")
        assertNull(r.eventAt)
        assertEquals(ParsedIntent.Confidence.LOW, r.confidence)
    }

    @Test
    fun `half past and quarter phrasing via colon`() {
        val r = parse("call at 14:15 tomorrow")
        assertEquals(at(2026, 7, 24, 14, 15), r.eventAt)
    }

    @Test
    fun `noon and midnight keywords alone`() {
        assertEquals(at(2026, 7, 24, 0, 0), parse("flight at midnight tomorrow").eventAt)
        assertEquals(at(2026, 7, 23, 12, 0), parse("lunch at noon").eventAt)
    }

    @Test
    fun `matched span points at the date phrase`() {
        val text = "meeting tomorrow 3pm"
        val r = parse(text)
        val span = r.matchedSpan!!
        val matched = text.substring(span.first, span.last + 1).lowercase()
        assertTrue(matched.contains("tomorrow") || matched.contains("3pm"))
    }

    @Test
    fun `in a week`() {
        val r = parse("dentist in a week at 9am")
        assertEquals(at(2026, 7, 30, 9, 0), r.eventAt)
    }

    @Test
    fun `title falls back when only a time is present`() {
        val r = parse("3pm tomorrow")
        assertEquals(at(2026, 7, 24, 15, 0), r.eventAt)
        assertTrue(r.title.isNotBlank())
    }

    // --- Spoken numbers (what on-device STT actually produces) ---

    @Test
    fun `spoken hour with pm`() {
        val r = parse("meeting tomorrow at three pm")
        assertEquals(at(2026, 7, 24, 15, 0), r.eventAt)
        assertEquals(ParsedIntent.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `spoken hour and minutes with pm - the reported case`() {
        val r = parse("i have a meeting today three fifty five pm")
        assertEquals(at(2026, 7, 23, 15, 55), r.eventAt)
        assertEquals(ParsedIntent.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `spoken hour and round minutes`() {
        val r = parse("call tomorrow at three thirty pm")
        assertEquals(at(2026, 7, 24, 15, 30), r.eventAt)
    }

    @Test
    fun `spoken am is respected`() {
        val r = parse("standup tomorrow at nine am")
        assertEquals(at(2026, 7, 24, 9, 0), r.eventAt)
    }

    @Test
    fun `half past spoken`() {
        val r = parse("meeting tomorrow half past four")
        assertEquals(at(2026, 7, 24, 16, 30), r.eventAt)
    }

    @Test
    fun `quarter to spoken`() {
        val r = parse("call tomorrow quarter to five")
        assertEquals(at(2026, 7, 24, 16, 45), r.eventAt)
    }

    @Test
    fun `o'clock spoken`() {
        val r = parse("meeting tomorrow at ten o'clock")
        assertEquals(at(2026, 7, 24, 22, 0), r.eventAt)
    }

    @Test
    fun `spoken relative offset`() {
        val r = parse("remind me in two hours")
        assertEquals(at(2026, 7, 23, 12, 0), r.eventAt)
    }

    @Test
    fun `digit inputs still work unchanged after normalization`() {
        assertEquals(at(2026, 7, 24, 15, 0), parse("meeting tomorrow 3pm").eventAt)
        assertEquals(at(2026, 7, 24, 14, 15), parse("call at 14:15 tomorrow").eventAt)
    }
}
