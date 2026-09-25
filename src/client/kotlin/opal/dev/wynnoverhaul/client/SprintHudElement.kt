package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class SprintHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Sprint HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return

        if (WynnMountEnergyTracker.energyVisible(System.currentTimeMillis())) return
        val meter = WynnSprintTracker.meter ?: return
        val label = when (meter.action) {
            "BREATH" -> "Breath  ${meter.step}/${WynnSprintTracker.ACTION_STEPS}"
            else -> "Sprint  ${meter.step}/${WynnSprintTracker.ACTION_STEPS}"
        }

        val font = Minecraft.getInstance().font

        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, meter.fraction, SPRINT_FILL, label)
    }

    private companion object {
        const val ID = "sprint"
        const val CONTENT_H = 15
        const val PAD = 3
        const val SPRINT_FILL = 0xFFE0B83B.toInt()
    }
}
