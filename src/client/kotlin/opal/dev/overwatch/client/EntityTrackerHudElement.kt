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
        if (!OverwatchGate.inGame) return
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
        }
        val texts = cachedTexts
        val hidden = cachedHidden

        val scale = HudLayoutManager.scale(ID)
        val lineH = LINE_HEIGHT
        HudLayoutManager.stableSize(ID, maxOf(cachedTexts.maxOf { font.width(it) } + CHIP_SIZE + 4, HEADER_MIN_W), lineH * cachedTexts.size + 3)

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
        HudStyle.header(graphics, font, ox, y, HudLayoutManager.peekSize(ID).first, "Tracking", "${rows.size}")
        y += lineH + 3
        for (i in shown.indices) {
            HudStyle.diamond(graphics, ox + CHIP_SIZE / 2, y + font.lineHeight / 2, CHIP_SIZE / 2, shown[i].colorArgb)
            graphics.text(font, texts[i + 1], ox + CHIP_SIZE + 4, y, ROW_COLOR, true)
            y += lineH
        }
        if (hidden > 0) graphics.text(font, texts.last(), ox + CHIP_SIZE + 4, y, ROW_COLOR, true)

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "tracker"
        const val LINE_HEIGHT = 10
        const val CHIP_SIZE = 7
        const val HEADER_MIN_W = 110
        const val MAX_ROWS = 12
        val HEADER_COLOR = OwTheme.ACCENT
        val ROW_COLOR = OwTheme.TEXT
    }
}
