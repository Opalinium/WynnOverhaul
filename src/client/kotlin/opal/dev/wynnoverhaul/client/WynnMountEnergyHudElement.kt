package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class WynnMountEnergyHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Mount energy HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return
        val now = System.currentTimeMillis()
        if (!WynnMountEnergyTracker.energyVisible(now)) return
        val energy = WynnMountEnergyTracker.energy ?: return
        val label = "Mount Energy  $energy/${WynnMountEnergyTracker.MAX_ENERGY}"

        val font = Minecraft.getInstance().font

        val contentW = font.width(label) + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)
        val (ox, oy) = HudLayoutManager.resolveFlat(ID, graphics.guiWidth(), graphics.guiHeight())

        HudBars.drawBar(graphics, font, ox, oy, boxW, boxH, energy.toFloat() / WynnMountEnergyTracker.MAX_ENERGY, ENERGY_FILL, label)
    }

    private companion object {
        const val ID = "mount_energy"
        const val CONTENT_H = 15
        const val PAD = 3
        const val ENERGY_FILL = 0xFF6FD66F.toInt()
    }
}
