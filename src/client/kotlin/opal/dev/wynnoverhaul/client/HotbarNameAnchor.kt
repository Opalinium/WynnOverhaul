package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object HotbarNameAnchor {
    private const val VANILLA_BOTTOM = 59
    private const val VANILLA_NO_HEALTH_SHIFT = 14
    private const val NAME_GAP = 12
    private const val EDGE_MARGIN = 60
    private var active = false

    @JvmStatic
    fun begin(graphics: GuiGraphicsExtractor) {
        active = false
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.isSpectator) return
        val guiW = graphics.guiWidth()
        val guiH = graphics.guiHeight()
        val bounds = HudLayoutManager.bounds(HotbarHudElement.ID, guiW, guiH)
        val centerX = ((bounds[0] + bounds[2]) / 2f).coerceIn(EDGE_MARGIN.toFloat(), (guiW - EDGE_MARGIN).toFloat())
        val targetY = (bounds[1] - NAME_GAP).coerceAtLeast(2)
        var vanillaY = guiH - VANILLA_BOTTOM
        if (client.gameMode?.canHurtPlayer() != true) vanillaY += VANILLA_NO_HEALTH_SHIFT
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX - guiW / 2f, (targetY - vanillaY).toFloat())
        active = true
    }

    @JvmStatic
    fun end(graphics: GuiGraphicsExtractor) {
        if (!active) return
        active = false
        graphics.pose().popMatrix()
    }
}
