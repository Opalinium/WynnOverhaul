package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class XpHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("XP HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val fraction = WynnCombatXpTracker.progressFraction ?: return
        val percent = (fraction * 100).toInt()
        val level = WynnLevelTracker.level
        val label = if (level != null) "Lv $level  $percent%" else "XP  $percent%"
        renderBar(graphics, Minecraft.getInstance().font, label, fraction)
    }

    private fun renderBar(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, label: String, fraction: Float) {
        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, fraction, XP_FILL, label)
    }

    private companion object {
        const val ID = "xp_bar"
        const val CONTENT_H = 15
        const val PAD = 3
        const val XP_FILL = 0xFF8FBF5C.toInt()
    }
}
