package com.smartreminder.core.model

import java.time.LocalDateTime

/**
 * Result of running a transcript through the date/time parser.
 *
 * [eventAt] is null when nothing usable was found — the confirm screen then falls back
 * to an empty date picker instead of guessing.
 */
data class ParsedIntent(
    val title: String,
    val eventAt: LocalDateTime?,
    val confidence: Confidence,
    /** Range within the original transcript that produced [eventAt], for UI highlighting. */
    val matchedSpan: IntRange? = null,
) {
    enum class Confidence { HIGH, LOW }

    companion object {
        /** Nothing parseable: keep the transcript as the title and let the user pick a time. */
        fun unparsed(transcript: String) = ParsedIntent(
            title = transcript.trim().ifEmpty { "Reminder" },
            eventAt = null,
            confidence = Confidence.LOW,
        )
    }
}
