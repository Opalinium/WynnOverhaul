package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.sin

class OwFade(private val durationNanos: Long = DEFAULT_NANOS) {
    private var start = System.nanoTime()

    fun restart() {
        start = System.nanoTime()
    }

    fun progress(): Float = ((System.nanoTime() - start).toFloat() / durationNanos).coerceIn(0f, 1f)

    fun overlay(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int = OwTheme.PANEL) {
        val t = ((System.nanoTime() - start).toFloat() / durationNanos).coerceIn(0f, 1f)
        if (t >= 1f) return
        val remaining = (1f - t) * (1f - t) * (1f - t)
        val alpha = (remaining * 255f).toInt().coerceIn(0, 255)
        graphics.fill(x, y, x + w, y + h, (alpha shl 24) or (color and 0xFFFFFF))
    }

    companion object {
        const val DEFAULT_NANOS = 200_000_000L
    }
}

object OwSkeleton {
    private val WIDTHS = floatArrayOf(1f, 0.78f, 0.92f, 0.64f, 0.86f, 0.7f)

    private fun pulse(): Float = 0.5f + 0.5f * sin(System.nanoTime() / 220_000_000.0).toFloat()

    fun bars(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, rows: Int, rowH: Int, gap: Int = 3, strength: Float = 1f) {
        val alpha = ((0x12 + pulse() * 0x16) * strength).toInt()
        val color = (alpha shl 24) or 0xFFFFFF
        for (i in 0 until rows) {
            val rowW = (w * WIDTHS[i % WIDTHS.size]).toInt()
            graphics.fill(x, y + i * (rowH + gap), x + rowW, y + i * (rowH + gap) + rowH, color)
        }
    }
}
