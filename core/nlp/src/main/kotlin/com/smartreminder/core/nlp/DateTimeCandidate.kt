package com.smartreminder.core.nlp

import java.time.LocalDateTime

/**
 * A date/time found in text by a [DateTimeEntityExtractor], before the rule layer
 * resolves ambiguity (bare hours, missing dates) against the reference clock.
 *
 * @param dateKnown whether the source text actually named a day. When false, only [base]'s
 *   time-of-day is trustworthy and the rule layer picks the day (today/tomorrow).
 * @param timeKnown whether the source text named a time of day.
 * @param hourWasBare true when the hour appeared with no am/pm marker (e.g. "at 3"),
 *   which the rule layer disambiguates with a daytime bias.
 */
data class DateTimeCandidate(
    val base: LocalDateTime,
    val span: IntRange,
    val dateKnown: Boolean,
    val timeKnown: Boolean,
    val hourWasBare: Boolean = false,
)

/**
 * Extracts date/time entities from free text. The Android implementation wraps
 * `android.view.textclassifier.TextClassifier`; [core:nlp] stays a pure java-library
 * so its rule logic is JVM-testable. Implementations return candidates in text order.
 */
fun interface DateTimeEntityExtractor {
    fun extract(text: String): List<DateTimeCandidate>

    companion object {
        /** No platform extractor available; the regex rule layer handles everything. */
        val NONE = DateTimeEntityExtractor { emptyList() }
    }
}
