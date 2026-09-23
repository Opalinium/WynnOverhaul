package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class WynnDialogueHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Dialogue HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        if (!WynnDialogueTracker.dialogueVisible(System.currentTimeMillis())) return
        val body = WynnDialogueTracker.body
        val choices = WynnDialogueTracker.choices
        val speaker = WynnDialogueTracker.speaker
        if (body.isEmpty() && choices.isEmpty()) return

        val hint = when {
            WynnDialogueTracker.hasChoices -> "Choose an option  (SHIFT)"
            WynnDialogueTracker.requiresShift -> "SHIFT to continue"
            else -> ""
        }

        val font = Minecraft.getInstance().font
        val lineH = font.lineHeight + 1
        val textW = HudLayoutManager.boxSize(ID).first - PAD * 2
        val rows = ArrayList<Pair<String, Int>>()
        if (!speaker.isNullOrEmpty()) rows.add(speaker to OwTheme.ACCENT)
        for (line in wrap(font, body, textW)) rows.add(line to OwTheme.TEXT)
        choices.forEachIndexed { index, choice ->
            val color = if (choice.selected) OwTheme.ACCENT else OwTheme.TEXT
            val wrapped = wrap(font, choice.text, textW - CHOICE_INDENT_W)
            wrapped.forEachIndexed { lineIndex, line ->
                val prefix = if (lineIndex == 0) "${index + 1}. " else "   "
                rows.add(prefix + line to color)
            }
        }
        if (rows.isEmpty()) return
        var contentH = PAD * 2 + rows.size * lineH
        if (hint.isNotEmpty()) contentH += HINT_GAP + lineH
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, HudLayoutManager.boxSize(ID).first, contentH)

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

        graphics.fill(ox, oy, ox + boxW, oy + boxH, OwTheme.PANEL)
        graphics.outline(ox, oy, boxW, boxH, OwTheme.BORDER)
        var ty = oy + PAD
        for ((line, color) in rows) {
            graphics.text(font, line, ox + PAD, ty, color, true)
            ty += lineH
        }
        if (hint.isNotEmpty()) {
            ty += HINT_GAP
            graphics.text(font, hint, ox + PAD, ty, OwTheme.TEXT_FAINT, true)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun wrap(font: Font, text: String, maxW: Int): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                current.append(word)
                continue
            }
            val candidate = "$current $word"
            if (font.width(candidate) <= maxW) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private companion object {
        const val ID = "dialogue"
        const val PAD = 4
        const val HINT_GAP = 2
        const val CHOICE_INDENT_W = 14
    }
}
