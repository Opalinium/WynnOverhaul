package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class PinListHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Pin list HUD failed", t)
            }
        }
    }

    private class Row(val label: String, val color: Int, val primary: Boolean, val x: Int, val y: Int, val z: Int)

    private fun render(graphics: GuiGraphicsExtractor) {
        val config = WynnOverhaulConfig.current
        if (!WynnOverhaulGate.inGame || !config.pinsEnabled || !config.pinListEnabled) return
        val designer = isHudDesignerOpen()
        val player = Minecraft.getInstance().player ?: return
        val primaryId = PinBoard.primary?.id
        val rows = if (designer && PinBoard.pins.isEmpty()) DEMO else PinBoard.pins.take(MAX_ROWS).map {
            Row(it.label, it.colorArgb, it.id == primaryId, it.x, it.y, it.z)
        }
        if (rows.isEmpty()) return

        val font = Minecraft.getInstance().font
        val distances = rows.map { "${Math.sqrt(player.distanceToSqr(it.x + 0.5, it.y.toDouble(), it.z + 0.5)).toInt()}m" }
        val contentW = rows.indices.maxOf { font.width(rows[it].label) + font.width(distances[it]) + GAP } + PAD * 2 + MARK_W
        val (boxW, _) = HudLayoutManager.stableSize(ID, contentW, rows.size * ROW_H + PAD * 2 + font.lineHeight + 3)

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

        val height = rows.size * ROW_H + PAD * 2 + font.lineHeight + 3
        OwTheme.hudPanel(graphics, ox, oy, boxW, height)
        var y = oy + PAD
        HudStyle.header(graphics, font, ox + PAD, y, boxW - PAD * 2, "Waypoints")
        y += font.lineHeight + 3
        for ((i, row) in rows.withIndex()) {
            val textColor = if (row.primary) OwTheme.TEXT else OwTheme.TEXT_DIM
            if (row.primary) HudStyle.diamond(graphics, ox + PAD + 2, y + font.lineHeight / 2, 2, row.color)
            else graphics.fill(ox + PAD + 1, y + font.lineHeight / 2 - 1, ox + PAD + 4, y + font.lineHeight / 2 + 2, HudStyle.alpha(row.color, 0.6f))
            graphics.text(font, row.label, ox + PAD + MARK_W, y, textColor, true)
            graphics.text(font, distances[i], ox + boxW - PAD - font.width(distances[i]), y, OwTheme.TEXT_DIM, true)
            y += ROW_H
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "pins"
        const val PAD = 5
        const val GAP = 10
        const val MARK_W = 10
        const val ROW_H = 11
        const val MAX_ROWS = 8
        val DEMO = listOf(
            Row("Silent Expanse", 0xFFFFD866.toInt(), true, 0, 0, 0),
            Row("Cinfras Entrance", 0xFF66E0FF.toInt(), false, 0, 0, 0),
        )
    }
}
