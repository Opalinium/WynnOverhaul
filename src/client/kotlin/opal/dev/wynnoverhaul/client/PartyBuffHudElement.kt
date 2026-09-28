package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class PartyBuffHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Party buff HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        if (!WynnOverhaulConfig.current.partyBuffTrackerEnabled) return
        val now = System.currentTimeMillis()
        val heals = PartyBuffTracker.heals.take(MAX_FEEDS)
        val notices = PartyBuffTracker.notices.takeLast(MAX_NOTICES)
        if (heals.isEmpty() && notices.isEmpty()) return

        val font = Minecraft.getInstance().font
        val contentW = maxOf(
            heals.maxOfOrNull { font.width(it.giver) + font.width("+%,d (×%d)".format(it.total, it.hits)) + 14 } ?: 0,
            notices.maxOfOrNull { font.width(it.text) + 14 } ?: 0,
        )
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW + BAR_PAD * 2, ROW_HEIGHT * (heals.size + notices.size))

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

        var y = oy
        for (feed in heals) {
            HudStyle.bar(graphics, ox, y, boxW, ROW_HEIGHT - 2, feed.recencyFraction(now), HEAL_FILL, notches = false)
            val right = "+%,d (×%d)".format(feed.total, feed.hits)
            graphics.text(font, feed.giver, ox + BAR_PAD + 1, y + TEXT_Y_OFFSET, OwTheme.TEXT, true)
            graphics.text(font, right, ox + boxW - font.width(right) - BAR_PAD - 1, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += ROW_HEIGHT
        }
        for (notice in notices) {
            graphics.text(font, notice.text, ox + BAR_PAD + 1, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "party_buffs"
        const val ROW_HEIGHT = 14
        const val BAR_PAD = 3
        const val TEXT_Y_OFFSET = 3
        const val MAX_FEEDS = 6
        const val MAX_NOTICES = 3
        const val HEAL_FILL = 0xFF58B368.toInt()
    }
}
