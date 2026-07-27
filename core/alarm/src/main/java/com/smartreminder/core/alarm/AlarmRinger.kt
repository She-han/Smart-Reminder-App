package com.smartreminder.core.alarm

/**
 * Handles a reminder alarm actually firing. Decoupled from the scheduler so the module that
 * owns the call UI (feature:call) can provide the real "ring like a phone call" behavior,
 * while the scheduler stays in core with no upward dependency.
 *
 * Phase 6 binds a notification-based implementation; Phase 7 replaces it with one that starts
 * the incoming-call foreground service.
 */
fun interface AlarmRinger {
    /** Invoked on the main thread from the alarm receiver. Implementations must return quickly. */
    fun ring(reminderId: Long)
}
