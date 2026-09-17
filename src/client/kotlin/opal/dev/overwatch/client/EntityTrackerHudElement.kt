package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class EntityTrackerHudElement : HudElement {

    private var loggedError = false

    private var cachedRows: List<EntityTrackerHudState.Row>? = null
    private var cachedShowDistance = false
    private var cachedTexts: List<String> = emptyList()
    private var cachedHidden = 0
    private var cachedContentW = 0
    private var cachedContentH = 0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Entity tracker HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        val config = OverwatchConfig.current
        if (!config.trackerEnabled) return
        val rows = EntityTrackerHudState.rows
        if (rows.isEmpty()) {
            cachedRows = null
            return
        }

        val font = Minecraft.getInstance().font
        val shown = rows.take(MAX_ROWS)

        if (rows !== cachedRows || config.trackerHudShowDistance != cachedShowDistance) {
            cachedRows = rows
            cachedShowDistance = config.trackerHudShowDistance
            cachedHidden = rows.size - shown.size

            val texts = ArrayList<String>(shown.size + 2)
            texts.add("Tracking (${rows.size})")
            for (row in shown) {
                texts.add(if (config.trackerHudShowDistance) "${row.label}  ${"%.0f".format(row.distance)}m" else row.label)
            }
            if (cachedHidden > 0) texts.add("+$cachedHidden more")
            cachedTexts = texts
            cachedContentW = texts.maxOf { font.width(it) } + CHIP_SIZE + 4
            cachedContentH = LINE_HEIGHT * texts.size
        }
        val texts = cachedTexts
        val hidden = cachedHidden

        val scale = config.trackerHudScale.toFloat().coerceIn(0.5f, 2.5f)
        val lineH = LINE_HEIGHT
        val contentW = cachedContentW
        val contentH = cachedContentH

        val corner = config.trackerHudCorner
        val right = corner.endsWith("RIGHT")
        val bottom = corner.startsWith("BOTTOM")
        val baseX = if (right) graphics.guiWidth() - MARGIN - (contentW * scale).toInt() else MARGIN
        val baseY = if (bottom) graphics.guiHeight() - MARGIN - (contentH * scale).toInt() else MARGIN

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        var y = oy
        graphics.text(font, texts[0], ox + CHIP_SIZE + 4, y, HEADER_COLOR)
        y += lineH
        for (i in shown.indices) {
            graphics.fill(ox, y + 1, ox + CHIP_SIZE, y + 1 + CHIP_SIZE, shown[i].colorArgb)
            graphics.text(font, texts[i + 1], ox + CHIP_SIZE + 4, y, ROW_COLOR)
            y += lineH
        }
        if (hidden > 0) graphics.text(font, texts.last(), ox + CHIP_SIZE + 4, y, ROW_COLOR)

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val MARGIN = 4
        const val LINE_HEIGHT = 10
        const val CHIP_SIZE = 7
        const val MAX_ROWS = 12
        const val HEADER_COLOR = 0xFFFFFFFF.toInt()
        const val ROW_COLOR = 0xFFDDDDDD.toInt()
    }
}
