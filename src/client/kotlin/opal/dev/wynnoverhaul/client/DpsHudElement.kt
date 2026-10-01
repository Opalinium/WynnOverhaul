package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class DpsHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("DPS HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.dpsHudEnabled) return
        val stats = WynnDamageTracker.stats ?: if (isHudDesignerOpen()) DEMO else return

        val font = Minecraft.getInstance().font
        val big = compact(stats.dps)
        val bigW = (font.width(big) * BIG_SCALE).toInt()
        val detail = "Total ${compact(stats.total.toDouble())}  Peak ${compact(stats.peak)}"
        val contentW = maxOf(bigW + font.width(UNIT) + 6, font.width(detail)) + PAD * 2
        val contentH = PAD + HEADER_H + BIG_H + font.lineHeight + 3 + BAR_H + PAD
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, maxOf(MIN_W, contentW), contentH)

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
        var y = oy + PAD
        HudStyle.header(graphics, font, ox + PAD, y, boxW - PAD * 2, "Damage", "${stats.seconds}s")
        y += HEADER_H

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((ox + PAD).toFloat(), y.toFloat())
        pose.scale(BIG_SCALE)
        graphics.text(font, big, 0, 0, OwTheme.TEXT, true)
        pose.popMatrix()
        graphics.text(font, UNIT, ox + PAD + bigW + 4, y + BIG_H - font.lineHeight - 1, OwTheme.TEXT_DIM, true)
        y += BIG_H

        graphics.text(font, detail, ox + PAD, y, OwTheme.TEXT_DIM, true)
        y += font.lineHeight + 3

        val total = stats.elements.values.sum().coerceAtLeast(1L)
        var bx = ox + PAD
        val barW = boxW - PAD * 2
        graphics.fill(bx - 1, y - 1, bx + barW + 1, y + BAR_H + 1, OwTheme.HAIRLINE)
        graphics.fill(bx, y, bx + barW, y + BAR_H, BAR_BACK)
        val elements = WynnDamageTracker.Element.entries.filter { (stats.elements[it] ?: 0L) > 0L }
        var used = 0
        for ((i, element) in elements.withIndex()) {
            val share = stats.elements.getValue(element)
            val segW = if (i == elements.lastIndex) barW - used else (barW * share / total).toInt()
            if (segW > 0) graphics.fill(bx + used, y, bx + used + segW, y + BAR_H, 0xFF000000.toInt() or element.rgb)
            used += segW
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun compact(value: Double): String = when {
        value >= 1_000_000_000 -> "%.2fB".format(value / 1_000_000_000)
        value >= 1_000_000 -> "%.2fM".format(value / 1_000_000)
        value >= 10_000 -> "%.1fk".format(value / 1_000)
        else -> "%,d".format(value.toLong())
    }

    private companion object {
        const val ID = "dps"
        const val UNIT = "DPS"
        const val PAD = 6
        const val MIN_W = 150
        const val HEADER_H = 12
        const val BIG_H = 16
        const val BIG_SCALE = 1.6f
        const val BAR_H = 3
        const val BAR_BACK = 0x40000000
        val DEMO = WynnDamageTracker.Stats(
            12_480.0, 18_900.0, 84_200L, 14,
            mapOf(
                WynnDamageTracker.Element.NEUTRAL to 30_000L,
                WynnDamageTracker.Element.FIRE to 34_000L,
                WynnDamageTracker.Element.THUNDER to 20_200L,
            ),
        )
    }
}
