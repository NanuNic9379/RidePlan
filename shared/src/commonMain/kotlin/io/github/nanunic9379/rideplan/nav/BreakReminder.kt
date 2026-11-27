package io.github.nanunic9379.rideplan.nav

/** Movement-only riding time. Short stops pause the clock; stops over 15 minutes reset it. */
class BreakReminder(private val intervalMs: Long) {
    private var lastAt: Long? = null
    private var wasMoving = false
    private var ridingMs = 0L
    private var stoppedMs = 0L
    var due: Boolean = false
        private set

    fun update(at: Long, moving: Boolean): Boolean {
        val previous = lastAt
        val elapsed = previous?.let { (at - it).coerceAtLeast(0) } ?: 0L
        if (moving) {
            if (stoppedMs > LONG_STOP_MS || elapsed > LONG_STOP_MS) {
                ridingMs = 0L
                due = false
            } else if (wasMoving) {
                ridingMs += elapsed
                if (ridingMs >= intervalMs && !due) due = true
            }
            stoppedMs = 0L
        } else {
            stoppedMs += elapsed
            if (stoppedMs > LONG_STOP_MS) {
                ridingMs = 0L
                due = false
            }
        }
        lastAt = at
        wasMoving = moving
        return due
    }

    fun dismiss() { due = false; ridingMs = 0L }

    private companion object { const val LONG_STOP_MS = 15 * 60 * 1000L }
}
