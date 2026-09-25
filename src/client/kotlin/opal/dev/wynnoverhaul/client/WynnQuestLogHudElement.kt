package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class WynnQuestLogHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Quest log HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.questLogHudEnabled) return
        val title = WynnScoreboardTracker.sidebarTitle
        val lines = WynnScoreboardTracker.sidebarLines
        if (title.isEmpty() && lines.isEmpty()) return

        val font = Minecraft.getInstance().font
        val lineH = font.lineHeight + 1

        var contentW = 0
        if (title.isNotEmpty()) contentW = maxOf(contentW, font.width(title) + PAD * 2 + 24)
        for (line in lines) contentW = maxOf(contentW, font.width(line) + PAD * 2)
        val contentH = PAD * 2 + (if (title.isNotEmpty()) font.lineHeight + 3 + TITLE_GAP else 0) + lines.size * lineH
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
        var ty = oy + PAD
        if (title.isNotEmpty()) {
            HudStyle.header(graphics, font, ox + PAD, ty, boxW - PAD * 2, title)
            ty += font.lineHeight + 3 + TITLE_GAP
        }
        for (line in lines) {
            graphics.text(font, line, ox + PAD, ty, OwTheme.TEXT, true)
            ty += lineH
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "quest_log"
        const val PAD = 4
        const val TITLE_GAP = 2
    }
}
