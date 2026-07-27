package com.smartreminder.core.ui.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dateWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** "Today 15:00", "Tomorrow 15:00", "24 Jul 15:00", "24 Jul 2027 15:00". */
fun Instant.formatEventTime(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val target = atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val date = target.toLocalDate()
    val time = timeFormatter.format(target)

    return when {
        date == today -> "Today $time"
        date == today.plusDays(1) -> "Tomorrow $time"
        date == today.minusDays(1) -> "Yesterday $time"
        date.year == today.year -> "${dateFormatter.format(target)} $time"
        else -> "${dateWithYearFormatter.format(target)} $time"
    }
}

fun Instant.formatTimeOnly(zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormatter.format(atZone(zone))

/** "in 30 min", "in 2 h 15 min", "in 3 days", "5 min ago". */
fun Instant.formatRelativeTo(now: Instant = Instant.now()): String {
    val duration = Duration.between(now, this)
    val past = duration.isNegative
    val abs = duration.abs()

    val text = when {
        abs.toMinutes() < 1L -> "now"
        abs.toMinutes() < 60L -> "${abs.toMinutes()} min"
        abs.toHours() < 24L -> {
            val minutes = abs.toMinutes() % 60
            if (minutes == 0L) "${abs.toHours()} h" else "${abs.toHours()} h ${minutes} min"
        }

        else -> "${abs.toDays()} day${if (abs.toDays() == 1L) "" else "s"}"
    }

    return when {
        text == "now" -> "now"
        past -> "$text ago"
        else -> "in $text"
    }
}

/** "Rings 30 min before" — the lead-time label used on list rows and the confirm screen. */
fun formatLeadTime(leadMinutes: Int): String = when {
    leadMinutes <= 0 -> "At the event time"
    leadMinutes < 60 -> "$leadMinutes min before"
    leadMinutes % 60 == 0 -> "${leadMinutes / 60} h before"
    else -> "${leadMinutes / 60} h ${leadMinutes % 60} min before"
}

/** Groups a reminder under a date header in the list. */
fun Instant.toLocalDateIn(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    atZone(zone).toLocalDate()

internal fun Duration.absMinutes(): Long = abs(toMinutes())
