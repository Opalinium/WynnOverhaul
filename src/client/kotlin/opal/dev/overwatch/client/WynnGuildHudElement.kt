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
        val sub = "Lv ${guild.level}  ${guild.xpPercent}%"
        val lineH = font.lineHeight + 3

        val contentW = maxOf(font.width(guild.name) + font.width(sub) + 24, MIN_W) + PAD * 2
        val contentH = PAD * 2 + lineH + BAR_H + 2
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
        HudStyle.header(graphics, font, ox + PAD, oy + PAD, boxW - PAD * 2, guild.name, sub)
        HudStyle.bar(graphics, ox + PAD, oy + PAD + lineH, boxW - PAD * 2, BAR_H, guild.xpPercent / 100f, OwTheme.GOOD, notches = false)

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "guild"
        const val PAD = 3
        const val BAR_H = 5
        const val MIN_W = 90
    }
}
