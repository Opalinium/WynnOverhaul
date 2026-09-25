package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class HpHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("HP HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val health = WynnVitalsTracker.health ?: return
        val maxHealth = WynnVitalsTracker.maxHealth ?: return
        val label = "HP  $health/$maxHealth"

        val font = Minecraft.getInstance().font
        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        val fraction = if (maxHealth > 0) health.toFloat() / maxHealth else 0f
        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, fraction, HEALTH_FILL, label)
    }

    private companion object {
        const val ID = "hp"
        const val CONTENT_H = 15
        const val PAD = 3
        const val HEALTH_FILL = 0xFFD64545.toInt()
    }
}
