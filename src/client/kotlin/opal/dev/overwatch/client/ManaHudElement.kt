package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class ManaHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Mana HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val mana = WynnVitalsTracker.mana ?: return
        val maxMana = WynnVitalsTracker.maxMana ?: return
        val label = "Mana  $mana/$maxMana"

        val font = Minecraft.getInstance().font
        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        val fraction = if (maxMana > 0) mana.toFloat() / maxMana else 0f
        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, fraction, MANA_FILL, label)
    }

    private companion object {
        const val ID = "mana"
        const val CONTENT_H = 15
        const val PAD = 3
        const val MANA_FILL = 0xFF45A3D6.toInt()
    }
}
