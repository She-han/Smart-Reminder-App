package com.smartreminder.core.nlp

/**
 * Converts spelled-out numbers to digits, because on-device speech recognition emits words
 * ("three fifty five pm") not digits ("3:55pm"). Runs before the date/time grammar so the
 * regex layer only ever deals with digits.
 *
 * Handles ones (0–19), tens (twenty–ninety), and tens+unit compounds (e.g. "twenty five" → 25).
 * A unit followed by a ten starts a new number ("three fifty" → "3 50"), which is what spoken
 * clock times sound like ("three fifty five" → "3 55" → 3:55).
 */
object SpokenNumbers {

    private val ONES = mapOf(
        "zero" to 0, "oh" to 0,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19,
    )

    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )

    fun normalize(text: String): String {
        val tokens = text.split(WHITESPACE)
        val out = StringBuilder()
        var current: Int? = null // number being accumulated

        fun flush() {
            current?.let {
                if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                out.append(it)
                out.append(' ')
            }
            current = null
        }

        for (raw in tokens) {
            if (raw.isEmpty()) continue
            val lower = raw.lowercase()
            val ten = TENS[lower]
            val one = ONES[lower]

            when {
                ten != null -> {
                    // A ten always begins a fresh number ("three fifty" → 3, then 50).
                    if (current != null) flush()
                    current = ten
                }

                one != null -> {
                    val c = current
                    if (c != null && c in 20..90 && c % 10 == 0) {
                        // tens + unit compound: "twenty" + "five" = 25
                        current = c + one
                        flush()
                    } else {
                        if (c != null) flush()
                        current = one
                    }
                }

                else -> {
                    flush()
                    if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                    out.append(raw)
                }
            }
        }
        flush()
        return out.toString().replace(WHITESPACE, " ").trim()
    }

    private val WHITESPACE = Regex("\\s+")
}
