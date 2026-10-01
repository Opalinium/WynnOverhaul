package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class ClassBuffHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Class buff HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.classBuffHudEnabled) return
        val buffs = WynnClassBuffTracker.buffs.takeIf { it.isNotEmpty() } ?: if (isHudDesignerOpen()) DEMO else return

        val font = Minecraft.getInstance().font
        val rightWidths = buffs.map { rightWidth(it, font::width) }
        val contentW = buffs.indices.maxOf { font.width(buffs[it].label) + rightWidths[it] + PAD * 2 + GAP }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, maxOf(MIN_W, contentW), ROW_HEIGHT * buffs.size)

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
        for (buff in buffs) {
            val timed = buff.seconds >= 0
            HudStyle.bar(graphics, ox, y, boxW, ROW_HEIGHT - 2, if (timed) buff.fraction else 1f, buff.color, notches = false)
            graphics.text(font, buff.label, ox + PAD + 1, y + TEXT_Y, OwTheme.TEXT, true)
            var rx = ox + boxW - PAD - 1
            if (timed) {
                val text = "${buff.seconds}s"
                rx -= font.width(text)
                graphics.text(font, text, rx, y + TEXT_Y, OwTheme.TEXT, true)
                rx -= GAP
            }
            if (buff.count > 0 || buff.broken > 0) rx = drawCount(graphics, font, buff, rx, y)
            if (buff.note.isNotEmpty()) {
                rx -= font.width(buff.note)
                graphics.text(font, buff.note, rx, y + TEXT_Y, OwTheme.TEXT_DIM, true)
            }
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun usesPips(buff: WynnClassBuffTracker.Buff): Boolean = buff.seconds < 0 && (buff.count + buff.broken) in 1..MAX_PIPS

    private fun rightWidth(buff: WynnClassBuffTracker.Buff, width: (String) -> Int): Int {
        var w = 0
        if (buff.seconds >= 0) w += width("${buff.seconds}s") + GAP
        if (buff.count > 0 || buff.broken > 0) {
            w += if (usesPips(buff)) (buff.count + buff.broken) * PIP_STEP + GAP else width(countText(buff)) + GAP
        }
        if (buff.note.isNotEmpty()) w += width(buff.note) + GAP
        return w
    }

    private fun countText(buff: WynnClassBuffTracker.Buff): String =
        if (buff.broken > 0) "${buff.count}+${buff.broken}" else "x${buff.count}"

    private fun drawCount(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, buff: WynnClassBuffTracker.Buff, right: Int, y: Int): Int {
        if (!usesPips(buff)) {
            val text = countText(buff)
            val x = right - font.width(text)
            graphics.text(font, text, x, y + TEXT_Y, OwTheme.TEXT, true)
            return x - GAP
        }
        var x = right
        val cy = y + (ROW_HEIGHT - 2) / 2
        repeat(buff.broken) {
            x -= PIP_STEP
            HudStyle.diamond(graphics, x + PIP_R, cy, PIP_R, BROKEN_PIP)
        }
        repeat(buff.count) {
            x -= PIP_STEP
            HudStyle.diamond(graphics, x + PIP_R, cy, PIP_R + 1, 0xCC000000.toInt())
            HudStyle.diamond(graphics, x + PIP_R, cy, PIP_R, OwTheme.TEXT)
        }
        return x - GAP
    }

    private companion object {
        const val ID = "class_buffs"
        const val ROW_HEIGHT = 14
        const val PAD = 3
        const val GAP = 4
        const val TEXT_Y = 3
        const val MIN_W = 150
        const val PIP_R = 3
        const val PIP_STEP = 9
        const val MAX_PIPS = 8
        const val BROKEN_PIP = 0xFFE05B5B.toInt()
        val DEMO = listOf(
            WynnClassBuffTracker.Buff("Mantle", 0xFF5B7FE0.toInt(), 4, 2),
            WynnClassBuffTracker.Buff("Guardian Angels", 0xFFE8D26A.toInt(), 3),
            WynnClassBuffTracker.Buff("Hound", 0xFFC99A5B.toInt(), 1, seconds = 12, fraction = 0.6f),
        )
    }
}
