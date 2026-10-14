package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

object LowHealthEffect {
    enum class Style(val label: String) {
        OFF("Off"),
        VIGNETTE("Vignette"),
        HEARTBEAT("Heartbeat"),
        BORDER("Pulsing border"),
        DRIP("Dripping blood");

        companion object {
            fun of(id: String): Style = entries.firstOrNull { it.name == id } ?: VIGNETTE
        }
    }

    private const val RED = 0x00B00A0A
    private const val SLICES = 14
    private const val DRIPS = 22

    private var level = 0f
    private var lastNanos = 0L

    fun draw(graphics: GuiGraphicsExtractor) {
        val config = WynnOverhaulConfig.current
        val style = Style.of(config.lowHealthStyle)
        val health = WynnVitalsTracker.health
        val max = WynnVitalsTracker.maxHealth
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
        lastNanos = now
        var target = 0f
        if (style != Style.OFF && WynnOverhaulGate.inGame && health != null && max != null && max > 0) {
            val fraction = health.toFloat() / max
            val threshold = config.lowHealthThreshold / 100f
            if (fraction < threshold) target = ((threshold - fraction) / threshold).coerceIn(0.15f, 1f)
        }
        level += (target - level) * (1f - exp(-dt * if (target > level) 6f else 3f))
        if (level < 0.01f) return
        val strength = level * config.lowHealthIntensity / 100f
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        val seconds = now / 1_000_000_000.0
        when (style) {
            Style.VIGNETTE -> vignette(graphics, w, h, strength * 0.75f)
            Style.HEARTBEAT -> vignette(graphics, w, h, strength * (0.3f + 0.7f * heartbeat(seconds, level)))
            Style.BORDER -> border(graphics, w, h, strength * (0.55f + 0.45f * (0.5f + 0.5f * sin(seconds * (3.0 + 4.0 * level)).toFloat())))
            Style.DRIP -> drip(graphics, w, h, strength, seconds)
            Style.OFF -> {}
        }
    }

    private fun color(alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or RED

    private fun heartbeat(seconds: Double, level: Float): Float {
        val period = 1.15 - 0.55 * level
        val phase = (seconds / period) % 1.0
        fun bump(centre: Double, width: Double): Float = exp(-((phase - centre) / width).let { it * it }).toFloat()
        return maxOf(bump(0.08, 0.06), 0.65f * bump(0.3, 0.07))
    }

    private fun vignette(graphics: GuiGraphicsExtractor, w: Int, h: Int, strength: Float) {
        val depthY = (h * 0.22f).toInt()
        val depthX = (w * 0.16f).toInt()
        graphics.fillGradient(0, 0, w, depthY, color(strength), color(0f))
        graphics.fillGradient(0, h - depthY, w, h, color(0f), color(strength))
        for (i in 0 until SLICES) {
            val t = 1f - i.toFloat() / SLICES
            val alpha = strength * t * t
            val x0 = depthX * i / SLICES
            val x1 = depthX * (i + 1) / SLICES
            graphics.fill(x0, 0, x1, h, color(alpha))
            graphics.fill(w - x1, 0, w - x0, h, color(alpha))
        }
    }

    private fun border(graphics: GuiGraphicsExtractor, w: Int, h: Int, strength: Float) {
        val thick = 4
        val c = color(strength)
        graphics.fill(0, 0, w, thick, c)
        graphics.fill(0, h - thick, w, h, c)
        graphics.fill(0, thick, thick, h - thick, c)
        graphics.fill(w - thick, thick, w, h - thick, c)
        val glow = color(strength * 0.35f)
        graphics.fill(thick, thick, w - thick, thick * 2, glow)
        graphics.fill(thick, h - thick * 2, w - thick, h - thick, glow)
        graphics.fill(thick, thick * 2, thick * 2, h - thick * 2, glow)
        graphics.fill(w - thick * 2, thick * 2, w - thick, h - thick * 2, glow)
    }

    private fun drip(graphics: GuiGraphicsExtractor, w: Int, h: Int, strength: Float, seconds: Double) {
        graphics.fillGradient(0, 0, w, (h * 0.05f).toInt(), color(strength * 0.7f), color(0f))
        for (k in 0 until DRIPS) {
            val seed = k * 0.6180339887
            val x = ((seed % 1.0) * w).toInt()
            val width = 2 + (k % 3)
            val base = 0.08f + 0.3f * (((k * 37) % 17) / 17f)
            val sway = 0.75f + 0.25f * sin(seconds * (0.7 + (k % 5) * 0.23) + k).toFloat()
            val length = (h * base * sway * (0.4f + 0.6f * level)).toInt()
            graphics.fillGradient(x, 0, x + width, length, color(strength * 0.85f), color(0f))
            graphics.fill(x - 1, length - 3, x + width + 1, length, color(strength * 0.4f * abs(sway)))
        }
    }
}
