package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class TotemHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Totem HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.totemHudEnabled) return
        val totems = WynnTotemTracker.totems.takeIf { it.isNotEmpty() } ?: if (isHudDesignerOpen()) {
            listOf(
                WynnTotemTracker.Totem(-1, 14, 20),
                WynnTotemTracker.Totem(-2, 6, 20),
            )
        } else {
            return
        }

        val font = Minecraft.getInstance().font

        val (boxW, boxH) = HudLayoutManager.stableSize(ID, TITLE_W, ROW_HEIGHT * totems.size)

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
        totems.forEachIndexed { i, totem ->
            val fraction = if (totem.maxSeconds <= 0) 0f else (totem.seconds.toFloat() / totem.maxSeconds).coerceIn(0f, 1f)
            HudStyle.bar(graphics, ox, y, boxW, ROW_HEIGHT - 2, fraction, TOTEM_FILL, notches = false)
            val name = "Totem ${i + 1}"
            val timer = "${totem.seconds}s"
            graphics.text(font, name, ox + BAR_PAD + 1, y + TEXT_Y_OFFSET, OwTheme.TEXT, true)
            graphics.text(font, timer, ox + boxW - font.width(timer) - BAR_PAD - 1, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "totems"
        const val TITLE_W = 140
        const val ROW_HEIGHT = 14
        const val BAR_PAD = 3
        const val TEXT_Y_OFFSET = 3
        const val TOTEM_FILL = 0xFF3FA7A3.toInt()
    }
}
