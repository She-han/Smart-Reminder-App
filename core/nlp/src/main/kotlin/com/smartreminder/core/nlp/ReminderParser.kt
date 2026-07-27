package com.smartreminder.core.nlp

import com.smartreminder.core.model.ParsedIntent
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns a transcript into a [ParsedIntent] with a resolved date/time and a cleaned title.
 *
 * Two layers:
 *  1. A regex rule grammar (below) that handles the common phrasings fully offline. This is
 *     the primary path and the only one covered by JVM tests.
 *  2. A platform [DateTimeEntityExtractor] (Android's TextClassifier) consulted only when the
 *     rules find nothing, to catch phrasings the grammar misses.
 *
 * All time math goes through the injected [clock] so results are deterministic in tests.
 */
class ReminderParser(
    private val extractor: DateTimeEntityExtractor,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun parse(rawText: String): ParsedIntent {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ParsedIntent.unparsed(rawText)

        // Speech recognition emits number words; convert to digits before matching.
        // Spans/titles are computed against this normalized text; the highlight is only
        // returned when normalization didn't change the text (see spanOrNull below).
        val text = SpokenNumbers.normalize(trimmed)
        val normalizationChanged = !text.equals(trimmed, ignoreCase = true)

        val now = LocalDateTime.now(clock.withZone(zone))
        val today = now.toLocalDate()

        // `work` is the lowercased text; consuming a match masks it with spaces so later
        // regexes can't re-match the same characters. Indices stay aligned with `text`.
        val work = StringBuilder(text.lowercase())
        val consumed = mutableListOf<IntRange>()

        fun mask(range: IntRange) {
            for (i in range) work[i] = ' '
            consumed += range
        }

        // 1. Relative minute/hour offset — a full datetime on its own.
        RELATIVE_TIME.find(work)?.let { m ->
            val amount = m.groupValues[1].toLong()
            val unit = m.groupValues[2]
            val dt = if (unit.startsWith("h")) now.plusHours(amount) else now.plusMinutes(amount)
            mask(m.range)
            return finish(text, consumed, dt, dateKnown = true, timeKnown = true, spanValid = !normalizationChanged)
        }

        // 2. Relative day/week offset — sets the date; an explicit time may still follow.
        var date: LocalDate? = null
        var dateKnown = false
        RELATIVE_DATE.find(work)?.let { m ->
            val amount = if (m.groupValues[1].startsWith("a")) 1L else m.groupValues[1].toLong()
            val days = if (m.groupValues[2].startsWith("w")) amount * 7 else amount
            date = today.plusDays(days)
            dateKnown = true
            mask(m.range)
        }

        // Time keywords / explicit times.
        var time: LocalTime? = null
        var timeKnown = false
        var eveningContext = false

        fun setTime(value: LocalTime, range: IntRange) {
            time = value
            timeKnown = true
            mask(range)
        }

        NOON_MIDNIGHT.find(work)?.let { m ->
            setTime(if (m.groupValues[1] == "midnight") LocalTime.MIDNIGHT else LocalTime.NOON, m.range)
        }
        // "half past three" / "quarter past three" / "quarter to four" (spoken clock forms).
        if (!timeKnown) HALF_PAST.find(work)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let {
                setTime(LocalTime.of(biasHour(it), 30), m.range)
            }
        }
        if (!timeKnown) QUARTER_PAST.find(work)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let {
                setTime(LocalTime.of(biasHour(it), 15), m.range)
            }
        }
        if (!timeKnown) QUARTER_TO.find(work)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let {
                val prevHour = if (it == 0) 23 else it - 1
                setTime(LocalTime.of(biasHour(prevHour), 45), m.range)
            }
        }
        // Two spoken numbers + am/pm: "three fifty five pm" -> "3 55 pm" -> 15:55.
        if (!timeKnown) HOUR_MIN_AMPM.find(work)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 1..12 && min in 0..59) {
                setTime(LocalTime.of(to24(h, m.groupValues[3]), min), m.range)
            }
        }
        if (!timeKnown) COLON_TIME.find(work)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            val ampm = m.groupValues[3]
            val hour = if (ampm.isNotEmpty()) to24(h, ampm) else h
            if (hour in 0..23 && min in 0..59) {
                setTime(LocalTime.of(hour, min), m.range)
            }
        }
        // "at three fifty five" -> "at 3 55" (no am/pm), daytime-biased.
        if (!timeKnown) HOUR_MIN_AT.find(work)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                setTime(LocalTime.of(biasHour(h), min), m.range)
            }
        }
        if (!timeKnown) AMPM_TIME.find(work)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                setTime(LocalTime.of(to24(h, m.groupValues[2]), 0), m.range)
            }
        }
        // "ten o'clock" -> 10, daytime-biased like a bare hour.
        if (!timeKnown) OCLOCK.find(work)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 }?.let {
                setTime(LocalTime.of(biasHour(it), 0), m.range)
            }
        }

        // 3. Day of month ("the 25th"). Requires an ordinal or "the", so it won't grab times.
        if (!dateKnown) DAY_OF_MONTH.find(work)?.let { m ->
            val dom = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
            if (dom != null && dom in 1..31) {
                date = nextDayOfMonth(today, dom)
                dateKnown = true
                mask(m.range)
            }
        }

        // 4. Weekday, optionally prefixed with "next".
        if (!dateKnown) WEEKDAY.find(work)?.let { m ->
            val day = WEEKDAYS[m.groupValues[2]]
            if (day != null) {
                date = nextWeekday(today, day, next = m.groupValues[1].isNotBlank())
                dateKnown = true
                mask(m.range)
            }
        }

        // 5. today / tonight / tomorrow / yesterday.
        if (!dateKnown) TODAY_FAMILY.find(work)?.let { m ->
            date = when (m.groupValues[1]) {
                "today", "tonight" -> today
                "tomorrow" -> today.plusDays(1)
                "yesterday" -> today.minusDays(1)
                else -> today
            }
            eveningContext = m.groupValues[1] == "tonight"
            dateKnown = true
            mask(m.range)
        } else if (TONIGHT_HINT.containsMatchIn(work)) {
            eveningContext = true
        }

        // 6. Bare hour ("at 3") — resolved last, after explicit times are consumed.
        if (!timeKnown) BARE_HOUR.find(work)?.let { m ->
            val h = m.groupValues[1].toInt()
            val bare = bareHourToTime(h, eveningContext)
            if (bare != null) {
                time = bare
                timeKnown = true
                mask(m.range)
            }
        }

        if (!dateKnown && !timeKnown) {
            return fallbackToExtractor(rawText, text, now, spanValid = !normalizationChanged)
        }

        // Explicit past date (e.g. "yesterday") is never a valid future reminder.
        if (date != null && date!! < today) {
            return ParsedIntent(
                title = buildTitle(text, consumed),
                eventAt = null,
                confidence = ParsedIntent.Confidence.LOW,
            )
        }

        var dt = LocalDateTime.of(date ?: today, time ?: DEFAULT_TIME)
        // A time-only reference that already passed today rolls to tomorrow.
        if (!dateKnown && timeKnown && dt.isBefore(now)) {
            dt = dt.plusDays(1)
        }

        return finish(text, consumed, dt, dateKnown, timeKnown, spanValid = !normalizationChanged)
    }

    private fun finish(
        text: String,
        consumed: List<IntRange>,
        dt: LocalDateTime,
        dateKnown: Boolean,
        timeKnown: Boolean,
        spanValid: Boolean,
    ): ParsedIntent = ParsedIntent(
        title = buildTitle(text, consumed),
        eventAt = dt,
        confidence = if (dateKnown || timeKnown) ParsedIntent.Confidence.HIGH else ParsedIntent.Confidence.LOW,
        matchedSpan = if (spanValid) consumed.enclosingSpan() else null,
    )

    private fun fallbackToExtractor(
        rawText: String,
        text: String,
        now: LocalDateTime,
        spanValid: Boolean,
    ): ParsedIntent {
        val candidate = extractor.extract(text).firstOrNull()
            ?: return ParsedIntent.unparsed(rawText)

        var dt = candidate.base
        if (candidate.hourWasBare) {
            bareHourToTime(dt.hour, eveningContext = false)?.let { dt = dt.with(it) }
        }
        if (dt.isBefore(now) && !candidate.dateKnown) dt = dt.plusDays(1)
        if (dt.toLocalDate() < now.toLocalDate()) return ParsedIntent.unparsed(rawText)

        return ParsedIntent(
            title = buildTitle(text, listOf(candidate.span)),
            eventAt = dt,
            confidence = ParsedIntent.Confidence.HIGH,
            matchedSpan = if (spanValid) candidate.span else null,
        )
    }

    private fun buildTitle(rawText: String, consumed: List<IntRange>): String {
        val chars = rawText.toCharArray()
        for (range in consumed) for (i in range) if (i in chars.indices) chars[i] = ' '
        var s = String(chars).replace(WHITESPACE, " ").trim()

        var changed = true
        while (changed) {
            changed = false
            for (filler in LEADING_FILLERS) {
                if (s.length >= filler.length &&
                    s.substring(0, filler.length).equals(filler, ignoreCase = true) &&
                    (s.length == filler.length || s[filler.length] == ' ')
                ) {
                    s = s.substring(filler.length).trim()
                    changed = true
                    break
                }
            }
        }

        val tokens = s.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.first().lowercase() in EDGE_WORDS) tokens.removeAt(0)
        while (tokens.isNotEmpty() && tokens.last().lowercase() in EDGE_WORDS) tokens.removeAt(tokens.size - 1)

        val cleaned = tokens.joinToString(" ").trim()
        return cleaned.ifBlank { "Reminder" }.replaceFirstChar { it.uppercaseChar() }
    }

    private fun to24(hour12: Int, ampm: String): Int = when {
        ampm.equals("am", true) -> hour12 % 12
        else -> (hour12 % 12) + 12
    }

    /** Bare hour with a daytime bias: 1–11 → PM, 12 → noon, 13–23 → literal 24h, 0 → midnight. */
    private fun bareHourToTime(h: Int, eveningContext: Boolean): LocalTime? =
        if (h in 0..23) LocalTime.of(biasHour(h), 0) else null

    /** Daytime bias applied to an am/pm-less hour: 1–11 → PM (+12), 12 → noon, 0/13–23 → literal. */
    private fun biasHour(h: Int): Int = when {
        h in 1..11 -> h + 12
        else -> h // 0, 12, 13..23 stay as-is
    }

    private fun nextWeekday(today: LocalDate, target: DayOfWeek, next: Boolean): LocalDate {
        var days = (target.value - today.dayOfWeek.value + 7) % 7
        if (days == 0) days = 7 // "Monday" on a Monday means the next one, not today
        val coming = today.plusDays(days.toLong())
        return if (next) coming.plusWeeks(1) else coming
    }

    private fun nextDayOfMonth(today: LocalDate, dom: Int): LocalDate {
        val thisMonth = runCatching { today.withDayOfMonth(dom) }.getOrNull()
        return if (thisMonth != null && !thisMonth.isBefore(today)) {
            thisMonth
        } else {
            val nm = today.plusMonths(1)
            nm.withDayOfMonth(minOf(dom, nm.lengthOfMonth()))
        }
    }

    private fun List<IntRange>.enclosingSpan(): IntRange? {
        if (isEmpty()) return null
        return minOf { it.first }..maxOf { it.last }
    }

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
        val WHITESPACE = Regex("\\s+")

        val RELATIVE_TIME = Regex("\\bin\\s+(\\d+)\\s+(minutes?|mins?|hours?|hrs?)\\b")
        val RELATIVE_DATE = Regex("\\bin\\s+(a|an|\\d+)\\s+(days?|weeks?)\\b")
        val NOON_MIDNIGHT = Regex("\\b(noon|midday|midnight)\\b")
        val COLON_TIME = Regex("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b")
        val AMPM_TIME = Regex("\\b(\\d{1,2})\\s*(am|pm)\\b")
        // Spoken clock forms (after number-word normalization).
        val HALF_PAST = Regex("\\bhalf past (\\d{1,2})\\b")
        val QUARTER_PAST = Regex("\\bquarter past (\\d{1,2})\\b")
        val QUARTER_TO = Regex("\\bquarter to (\\d{1,2})\\b")
        val HOUR_MIN_AMPM = Regex("\\b(\\d{1,2})\\s+(\\d{1,2})\\s*(am|pm)\\b")
        val HOUR_MIN_AT = Regex("\\bat\\s+(\\d{1,2})\\s+(\\d{1,2})\\b")
        val OCLOCK = Regex("\\b(\\d{1,2})\\s*o'?clock\\b")
        val DAY_OF_MONTH = Regex("\\bthe\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b|\\b(\\d{1,2})(?:st|nd|rd|th)\\b")
        val WEEKDAY = Regex(
            "\\b(next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                "mon|tue|tues|wed|weds|thu|thur|thurs|fri|sat|sun)\\b",
        )
        val TODAY_FAMILY = Regex("\\b(today|tonight|tomorrow|yesterday)\\b")
        val TONIGHT_HINT = Regex("\\btonight\\b")
        val BARE_HOUR = Regex("\\bat\\s+(\\d{1,2})\\b")

        val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
            "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
            "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
        )

        // Longest first so greedy stripping removes the fullest phrase.
        val LEADING_FILLERS = listOf(
            "don't forget to", "dont forget to", "remind me about", "remind me to",
            "i have an", "i have a", "i need to", "i want to", "remind me",
            "i have", "please", "let me",
        )
        val EDGE_WORDS = setOf("at", "on", "about", "to", "for", "of", "in", "a", "an")
    }
}
