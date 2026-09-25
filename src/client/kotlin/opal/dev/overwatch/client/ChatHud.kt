package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.min

object ChatHud {
    const val ID = "chat"
    const val CLASSIC = "CLASSIC"
    const val GLASS = "GLASS"
    const val FADE = "FADE"
    const val MINIMAL = "MINIMAL"

    val STYLES = listOf(CLASSIC, GLASS, FADE, MINIMAL)

    private const val VANILLA_BOTTOM = 40
    private const val VANILLA_INPUT_GAP = 28
    private const val MIN_INPUT_Y = 84
    private const val MIN_WIDTH = 60
    private const val MIN_HEIGHT = 30
    private const val FADE_SLICES = 12
    private var lastSignature = 0L

    fun label(style: String): String = when (style) {
        GLASS -> "Glass"
        FADE -> "Fade band"
        MINIMAL -> "No backdrop"
        else -> "Classic"
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    @JvmStatic
    fun active(): Boolean {
        val config = OverwatchConfig.current
        return OverwatchGate.inGame && config.customHudEnabled && config.chatHudEnabled
    }

    private fun style(): String = OverwatchConfig.current.chatStyle

    private fun signature(scale: Double): Long {
        val (w, h) = HudLayoutManager.boxSize(ID)
        return (w.toLong() shl 40) xor (h.toLong() shl 20) xor (scale * 1000).toLong() xor (if (active()) 1L else 0L)
    }

    private fun tick(client: Minecraft) {
        val scale = client.options.chatScale().get()
        val sig = signature(scale)
        if (sig == lastSignature) return
        lastSignature = sig
        if (client.level != null) client.gui.hud.chat.rescaleChat()
    }

    @JvmStatic
    fun width(scale: Double): Int = (HudLayoutManager.boxSize(ID).first / scale).toInt().coerceAtLeast(MIN_WIDTH)

    @JvmStatic
    fun height(scale: Double): Int = (HudLayoutManager.boxSize(ID).second / scale).toInt().coerceAtLeast(MIN_HEIGHT)

    @JvmStatic
    fun offsetX(): Float {
        val window = Minecraft.getInstance().window
        return HudLayoutManager.bounds(ID, window.guiScaledWidth, window.guiScaledHeight)[0].toFloat()
    }

    @JvmStatic
    fun offsetY(): Float {
        val window = Minecraft.getInstance().window
        val bounds = HudLayoutManager.bounds(ID, window.guiScaledWidth, window.guiScaledHeight)
        return (bounds[3] - (window.guiScaledHeight - VANILLA_BOTTOM)).toFloat()
    }

    @JvmStatic
    fun inputX(): Int {
        val window = Minecraft.getInstance().window
        return HudLayoutManager.bounds(ID, window.guiScaledWidth, window.guiScaledHeight)[0] + 4
    }

    @JvmStatic
    fun inputWidth(): Int = (HudLayoutManager.boxSize(ID).first - 4).coerceAtLeast(MIN_WIDTH)

    @JvmStatic
    fun inputY(guiH: Int): Int {
        val window = Minecraft.getInstance().window
        val bottom = HudLayoutManager.bounds(ID, window.guiScaledWidth, window.guiScaledHeight)[3]
        val max = guiH - 12
        return (bottom + VANILLA_INPUT_GAP).coerceAtMost(max).coerceAtLeast(minOf(MIN_INPUT_Y, max))
    }

    @JvmStatic
    fun virtualHeight(guiH: Int): Int = inputY(guiH) + 12

    @JvmStatic
    fun drawInputBar(g: GuiGraphicsExtractor, guiH: Int, color: Int) {
        val x0 = inputX() - 2
        val y0 = inputY(guiH) - 2
        val x1 = inputX() + inputWidth() - 2
        val y1 = y0 + 12
        if (style() == GLASS || style() == FADE) {
            HudStyle.plate(g, x0, y0, x1 - x0, y1 - y0, OwTheme.ACCENT)
        } else {
            g.fill(x0, y0, x1, y1, color)
        }
    }

    @JvmStatic
    fun drawBackdrop(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int): Boolean {
        if (!active()) return false
        val style = style()
        if (style == CLASSIC || style !in STYLES) return false
        if ((color and 0xFFFFFF) != 0) return false
        val a = (color ushr 24) / 255f
        when (style) {
            GLASS -> {
                g.fill(x0, y0, x1, y1, HudStyle.alpha(HudStyle.GLASS, min(1f, a * 1.9f)))
                g.fill(x0, y0, x0 + 1, y1, HudStyle.alpha(OwTheme.ACCENT_DIM, min(1f, a * 2.2f)))
            }
            FADE -> {
                val sliceW = (x1 - x0).toFloat() / FADE_SLICES
                for (i in 0 until FADE_SLICES) {
                    val t = (i + 0.5f) / FADE_SLICES
                    val alpha = min(1f, a * 1.8f) * (1f - t * t)
                    val sx0 = x0 + (i * sliceW).toInt()
                    val sx1 = x0 + ((i + 1) * sliceW).toInt()
                    g.fill(sx0, y0, sx1, y1, HudStyle.alpha(0xFF14100B.toInt(), alpha))
                }
                g.fill(x0, y0, x0 + 1, y1, HudStyle.alpha(OwTheme.ACCENT, min(1f, a * 1.6f)))
            }
        }
        return true
    }
}
