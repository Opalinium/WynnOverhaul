package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class UltimateHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Ultimate HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        val config = WynnOverhaulConfig.current
        if (!config.customHudEnabled || !config.ultimateHudEnabled) return
        if (!WynnUltimateTracker.visible(System.currentTimeMillis()) && !isHudDesignerOpen()) return
        val label = "Ultimate READY"

        val font = Minecraft.getInstance().font
        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, 1f, ULTIMATE_FILL, label)
    }

    companion object {
        const val ID = "ultimates"
        private const val CONTENT_H = 15
        private const val PAD = 3
        private const val ULTIMATE_FILL = 0xFFD4AF37.toInt()
    }
}
