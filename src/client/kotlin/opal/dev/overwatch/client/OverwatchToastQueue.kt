package opal.dev.overwatch.client

import java.util.ArrayDeque

object OverwatchToastQueue {

    data class Toast(val title: String, val subtitle: String, val colorArgb: Int)

    private class Active(val toast: Toast, val shownAtMillis: Long)

    private val pending = ArrayDeque<Toast>()

    @Volatile
    private var active: Active? = null

    fun show(toast: Toast) {
        pending.add(toast)
    }

    fun current(): Toast? {
        val now = System.currentTimeMillis()
        val current = active
        if (current != null) {
            if (now - current.shownAtMillis < DISPLAY_MILLIS) return current.toast
            active = null
        }
        val next = pending.poll() ?: return null
        active = Active(next, now)
        return next
    }

    fun elapsedFraction(): Float {
        val current = active ?: return 0f
        val elapsed = System.currentTimeMillis() - current.shownAtMillis
        return (elapsed.toFloat() / DISPLAY_MILLIS).coerceIn(0f, 1f)
    }

    private const val DISPLAY_MILLIS = 4000L
}
