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
                WynnTotemTracker.Totem(-1, "You", 14, 20),
                WynnTotemTracker.Totem(-2, "Ally", 150, 300, WynnTotemTracker.Kind.MOB),
                WynnTotemTracker.Totem(-3, "Friend", 95, 300, WynnTotemTracker.Kind.GATHERING),
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
            HudStyle.bar(graphics, ox, y, boxW, ROW_HEIGHT - 2, fraction, fillFor(totem.kind), notches = false)
            val timer = timerText(totem.seconds)
            val label = totem.owner + totem.kind.tag
            val name = truncateToWidth(font, label, boxW - BAR_PAD * 2 - 2 - font.width(timer) - GAP)
            graphics.text(font, name, ox + BAR_PAD + 1, y + TEXT_Y_OFFSET, OwTheme.TEXT, true)
            graphics.text(font, timer, ox + boxW - font.width(timer) - BAR_PAD - 1, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun fillFor(kind: WynnTotemTracker.Kind): Int = when (kind) {
        WynnTotemTracker.Kind.CLASS -> TOTEM_FILL
        WynnTotemTracker.Kind.MOB -> MOB_FILL
        WynnTotemTracker.Kind.GATHERING -> GATHERING_FILL
    }

    private fun timerText(seconds: Int): String = if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"

    private companion object {
        const val ID = "totems"
        const val TITLE_W = 140
        const val ROW_HEIGHT = 14
        const val BAR_PAD = 3
        const val GAP = 4
        const val TEXT_Y_OFFSET = 3
        const val TOTEM_FILL = 0xFF3FA7A3.toInt()
        const val MOB_FILL = 0xFFA0C84B.toInt()
        const val GATHERING_FILL = 0xFFC9A23C.toInt()
    }
}
