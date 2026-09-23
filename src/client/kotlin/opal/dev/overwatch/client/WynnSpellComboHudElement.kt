package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class WynnSpellComboHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Spell combo HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val combo = WynnSpellTracker.combo ?: return
        if (!WynnSpellTracker.comboVisible(System.currentTimeMillis())) return

        val font = Minecraft.getInstance().font
        val tokens = combo.map {
            when (it) {
                WynnSpellSegments.ComboInput.LEFT -> "R" to COMBO_LEFT
                WynnSpellSegments.ComboInput.RIGHT -> "L" to COMBO_RIGHT
                WynnSpellSegments.ComboInput.EMPTY -> "·" to OwTheme.TEXT_FAINT
            }
        }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, groupWidth(font, tokens) + PAD * 2, CONTENT_H)

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
        var tx = ox + (boxW - groupWidth(font, tokens)) / 2
        for (i in tokens.indices) {
            graphics.text(font, tokens[i].first, tx, oy + 4, tokens[i].second, true)
            tx += font.width(tokens[i].first)
            if (i < tokens.size - 1) {
                graphics.text(font, SEPARATOR, tx + SEP_PAD, oy + 4, OwTheme.TEXT_DIM, true)
                tx += font.width(SEPARATOR) + SEP_PAD * 2
            }
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun groupWidth(font: net.minecraft.client.gui.Font, tokens: List<Pair<String, Int>>): Int {
        var total = 0
        for (i in tokens.indices) {
            total += font.width(tokens[i].first)
            if (i < tokens.size - 1) total += font.width(SEPARATOR) + SEP_PAD * 2
        }
        return total
    }

    private companion object {
        const val ID = "spell_combo"
        const val CONTENT_H = 16
        const val PAD = 3
        const val SEP_PAD = 3
        const val SEPARATOR = ">"
        const val COMBO_LEFT = 0xFFE0B83B.toInt()
        const val COMBO_RIGHT = 0xFF7FE3FF.toInt()
    }
}
