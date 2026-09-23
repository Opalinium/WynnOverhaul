package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class WynnSpellCastHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Spell cast HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val cast = WynnSpellTracker.lastCast ?: return
        if (!WynnSpellTracker.castVisible(System.currentTimeMillis())) return

        val font = Minecraft.getInstance().font
        val parts = ArrayList<Pair<String, Int>>(1 + cast.costs.size)
        parts.add(cast.name to OwTheme.TEXT)
        for (cost in cast.costs) {
            val label = if (cost.mana) "Mana" else "HP"
            val color = if (cost.mana) MANA_TEXT else HP_TEXT
            parts.add("  -${cost.amount} $label" to color)
        }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, parts.sumOf { font.width(it.first) } + PAD * 2, CONTENT_H)

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
        var tx = ox + PAD
        for ((text, color) in parts) {
            graphics.text(font, text, tx, oy + 3, color, true)
            tx += font.width(text)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "spell_cast"
        const val CONTENT_H = 15
        const val PAD = 3
        const val MANA_TEXT = 0xFF7FE3FF.toInt()
        const val HP_TEXT = 0xFFD64545.toInt()
    }
}
