package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import opal.dev.wynnoverhaul.WynnOverhaul

class WynnSpellComboHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Spell combo HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return
        val liveGlyphs = WynnSpellTracker.comboComponents
        val liveArrow = WynnSpellTracker.comboArrow
        val liveVisible = WynnSpellTracker.comboVisible(System.currentTimeMillis())
        val glyphs: List<Component>
        val arrow: Component
        if (liveGlyphs != null && liveArrow != null && liveVisible) {
            glyphs = liveGlyphs
            arrow = liveArrow
        } else if (isHudDesignerOpen()) {
            val left = Component.literal("L")
            val right = Component.literal("R")
            val empty = Component.literal(" ")
            glyphs = listOf(left, empty, right, empty, left)
            arrow = Component.literal("\u2192")
        } else {
            return
        }

        val font = Minecraft.getInstance().font
        val parts = ArrayList<Component>(glyphs.size * 2 - 1)
        for (i in glyphs.indices) {
            if (i > 0) parts.add(arrow)
            parts.add(glyphs[i])
        }
        val contentW = parts.sumOf { font.width(it) } + SEP_PAD * 2 * (glyphs.size - 1)
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW + PAD * 2, CONTENT_H)

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
        HudStyle.fadeRule(graphics, ox, oy + boxH - 2, boxW, OwTheme.ACCENT_DIM, leftSolid = true)
        HudStyle.diamond(graphics, ox + boxW / 2, oy + boxH - 2, 2, OwTheme.ACCENT)
        var tx = ox + (boxW - contentW) / 2
        for (i in parts.indices) {
            graphics.text(font, parts[i], tx, oy + 4, 0xFFFFFFFF.toInt(), true)
            tx += font.width(parts[i])
            if (i % 2 == 0 && i < parts.size - 1) tx += SEP_PAD
            else if (i % 2 == 1) tx += SEP_PAD
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private companion object {
        const val ID = "spell_combo"
        const val CONTENT_H = 16
        const val PAD = 3
        const val SEP_PAD = 3
    }
}