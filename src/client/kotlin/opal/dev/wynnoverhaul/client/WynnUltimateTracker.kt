package opal.dev.wynnoverhaul.client

import net.minecraft.network.chat.Component

object WynnUltimateTracker {
    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    private var seenAtMillis: Long = 0L

    fun update(value: Component?) {
        if (value == null) return
        if (value.string.any { it == READY_GLYPH }) {
            ready = true
            seenAtMillis = System.currentTimeMillis()
        }
    }

    fun visible(now: Long): Boolean =
        ready && now - seenAtMillis < VISIBLE_TTL_MILLIS

    fun clear() {
        ready = false
        seenAtMillis = 0L
    }

    private fun c(hex: String): Char = hex.toInt(16).toChar()

    private val READY_GLYPH = c("E4E0")

    private const val VISIBLE_TTL_MILLIS = 3000L
}
