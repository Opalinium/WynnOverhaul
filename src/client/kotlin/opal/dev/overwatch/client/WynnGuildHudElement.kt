package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class WynnGuildHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Guild HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val guild = WynnGuildBarTracker.active ?: return

        val font = Minecraft.getInstance().font
        val sub = "Lv ${guild.level}  ${guild.xpPercent}% XP"
        val lineH = font.lineHeight + 1
        val contentW = maxOf(font.width(guild.name), font.width(sub)) + PAD * 2
        val contentH = PAD * 2 + lineH * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, contentH)

        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        OwTheme.hudPanel(graphics, ox, oy, boxW, boxH)
        graphics.text(font, guild.name, ox + PAD, oy + PAD, OwTheme.ACCENT, true)
        graphics.text(font, sub, ox + PAD, oy + PAD + lineH, OwTheme.TEXT_DIM, true)

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "guild"
        const val PAD = 3
    }
}
