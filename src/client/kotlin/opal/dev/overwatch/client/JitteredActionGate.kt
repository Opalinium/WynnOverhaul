package opal.dev.overwatch.client

class JitteredActionGate {
    private var nextAtNanos: Long = 0L
    private var firedSinceReset: Boolean = false

    fun reset() {
        nextAtNanos = 0L
        firedSinceReset = false
    }

    fun isReady(now: Long, immediateFirst: Boolean = false, delayNanos: () -> Long): Boolean {
        if (nextAtNanos == 0L) {
            nextAtNanos = if (immediateFirst && !firedSinceReset) now else now + delayNanos()
        }
        return now >= nextAtNanos
    }

    fun markFired() {
        nextAtNanos = 0L
        firedSinceReset = true
    }
}
