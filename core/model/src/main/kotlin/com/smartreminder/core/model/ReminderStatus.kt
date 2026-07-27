package com.smartreminder.core.model

/**
 * Lifecycle of a reminder's "call".
 *
 * ```
 * SCHEDULED ──alarm fires──▶ RINGING ──answered──▶ ANSWERED ──mark done──▶ DONE
 *     ▲                         │
 *     │                    declined/timeout
 *     │                         ▼
 *     └──redial scheduled── MISSED ──attempts exhausted──▶ MISSED (terminal)
 *     │
 *     └──────snooze────── SNOOZED
 * ```
 */
enum class ReminderStatus {
    SCHEDULED,
    RINGING,
    ANSWERED,
    SNOOZED,
    MISSED,
    DONE,
    CANCELLED,
    ;

    /** Whether an alarm should currently be pending for a reminder in this state. */
    val isActive: Boolean
        get() = this == SCHEDULED || this == SNOOZED || this == RINGING

    val isTerminal: Boolean
        get() = this == DONE || this == CANCELLED
}
