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
        val component = WynnUltimateTracker.active() ?: return

        val font = Minecraft.getInstance().font
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, font.width(component) + PAD * 2, font.lineHeight + PAD * 2)
        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(baseX.toFloat(), baseY.toFloat())
        if (scale != 1f) pose.scale(scale)
        OwTheme.hudPanel(graphics, 0, 0, boxW, boxH)
        graphics.text(font, component, (boxW - font.width(component)) / 2, (boxH - font.lineHeight) / 2 + 1, OwTheme.TEXT, true)
        pose.popMatrix()
    }

    companion object {
        const val ID = "ultimates"
        private const val PAD = 3
    }
}
