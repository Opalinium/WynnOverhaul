package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class OverwatchToastHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Toast HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame) return
        val toast = OverwatchToastQueue.current() ?: return
        val font = Minecraft.getInstance().font

        val fraction = OverwatchToastQueue.elapsedFraction()
        val alpha = when {
            fraction < FADE_IN_END -> (255 * (fraction / FADE_IN_END)).toInt()
            fraction > FADE_OUT_START -> (255 * (1f - (fraction - FADE_OUT_START) / (1f - FADE_OUT_START))).toInt()
            else -> 255
        }.coerceIn(0, 255)
        if (alpha <= 0) return

        val title = toast.title
        val subtitle = toast.subtitle
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, maxOf(font.width(title), font.width(subtitle)) + PAD * 2, LINE_HEIGHT * 2 + PAD * 2)
        val (storedW, storedH) = HudLayoutManager.boxSize(ID)
        val (anchorX, anchorY) = HudLayoutManager.resolveForSize(ID, graphics.guiWidth(), graphics.guiHeight(), storedW, storedH)
        val x = (anchorX + storedW / 2 - boxW / 2).coerceIn(0, (graphics.guiWidth() - boxW).coerceAtLeast(0))
        val y = (anchorY + storedH / 2 - boxH / 2).coerceIn(0, (graphics.guiHeight() - boxH).coerceAtLeast(0))

        OwTheme.hudPanel(graphics, x, y, boxW, boxH, alpha)
        graphics.fill(x, y, x + boxW, y + 2, withAlpha(toast.colorArgb, alpha))
        graphics.text(font, title, x + (boxW - font.width(title)) / 2, y + PAD, withAlpha(toast.colorArgb, alpha), true)
        graphics.text(font, subtitle, x + (boxW - font.width(subtitle)) / 2, y + PAD + LINE_HEIGHT, withAlpha(TEXT_COLOR, alpha), true)
    }

    private fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0xFFFFFF) or (alpha shl 24)

    private companion object {
        const val ID = "toast"
        const val PAD = 6
        const val LINE_HEIGHT = 11
        const val FADE_IN_END = 0.08f
        const val FADE_OUT_START = 0.85f
        val TEXT_COLOR = OwTheme.TEXT
    }
}
