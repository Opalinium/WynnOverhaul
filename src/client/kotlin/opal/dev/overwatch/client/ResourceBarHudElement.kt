package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class ResourceBarHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Resource bar HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val bar = WynnResourceBarTracker.active ?: return
        val label = if (bar.displayText.isEmpty()) bar.kind.displayName else "${bar.kind.displayName}  ${bar.displayText}"

        val font = Minecraft.getInstance().font
        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, bar.fraction, bar.kind.colorArgb, label)
    }

    private companion object {
        const val ID = "resource_bar"
        const val CONTENT_H = 15
        const val PAD = 3
    }
}
