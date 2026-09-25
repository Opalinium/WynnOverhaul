package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch
import kotlin.math.sin

class WynnDialogueHudElement : HudElement {
    private var loggedError = false
    private var wasVisible = false
    private var shownSince = 0L

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

    private class Choice(val number: Int, val lines: List<String>, val selected: Boolean) {
        val height: Int get() = lines.size * LINE_H + CHOICE_PAD * 2
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val now = System.currentTimeMillis()
        if (!WynnDialogueTracker.dialogueVisible(now)) {
            wasVisible = false
            return
        }
        val body = WynnDialogueTracker.body
        val rawChoices = WynnDialogueTracker.choices
        val speaker = WynnDialogueTracker.speaker
        if (body.isEmpty() && rawChoices.isEmpty()) return
        if (!wasVisible) {
            wasVisible = true
            shownSince = now
            HudLayoutManager.forgetSize(ID)
        }
        val fade = HudStyle.smoothstep(((now - shownSince) / FADE_MS).coerceIn(0f, 1f))
        val lift = ((1f - fade) * LIFT).toInt()

        val font = Minecraft.getInstance().font
        val boxW = HudLayoutManager.boxSize(ID).first
        val textW = boxW - PAD_X * 2
        val bodyLines = HudStyle.wrap(font, body, textW)
        val choices = rawChoices.mapIndexed { index, c ->
            Choice(index + 1, HudStyle.wrap(font, c.text, textW - CHIP_W - 6).ifEmpty { listOf("") }, c.selected)
        }

        val hint = when {
            WynnDialogueTracker.hasChoices -> "Confirm"
            WynnDialogueTracker.requiresShift -> "Continue"
            else -> ""
        }

        var contentH = PAD_Y * 2
        if (!speaker.isNullOrEmpty()) contentH += HEADER_H + 3
        contentH += bodyLines.size * LINE_H
        if (choices.isNotEmpty()) contentH += CHOICE_GAP + choices.sumOf { it.height + CHOICE_SPACING } - CHOICE_SPACING
        if (hint.isNotEmpty()) contentH += FOOTER_GAP + font.lineHeight + 3
        val (w, h) = HudLayoutManager.stableSize(ID, boxW, contentH)

        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(baseX.toFloat(), (baseY + lift).toFloat())
        if (scale != 1f) pose.scale(scale)

        HudStyle.plate(graphics, 0, 0, w, h, OwTheme.ACCENT, fade)
        var y = PAD_Y
        if (!speaker.isNullOrEmpty()) {
            HudStyle.header(graphics, font, PAD_X, y, w - PAD_X * 2, speaker, "", OwTheme.ACCENT, fade)
            y += HEADER_H + 3
        }
        for (line in bodyLines) {
            graphics.text(font, line, PAD_X, y + 1, HudStyle.alpha(BODY_COLOR, fade), true)
            y += LINE_H
        }
        if (choices.isNotEmpty()) {
            y += CHOICE_GAP
            for (choice in choices) {
                drawChoice(graphics, font, PAD_X, y, w - PAD_X * 2, choice, fade)
                y += choice.height + CHOICE_SPACING
            }
            y -= CHOICE_SPACING
        }
        if (hint.isNotEmpty()) {
            y += FOOTER_GAP
            drawFooter(graphics, font, w - PAD_X, y, hint, fade, now)
        }
        pose.popMatrix()
    }

    private fun drawChoice(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, choice: Choice, fade: Float) {
        val h = choice.height
        if (choice.selected) {
            g.fill(x, y, x + w, y + h, HudStyle.alpha(OwTheme.ACCENT, 0.2f * fade))
            g.fill(x, y, x + 2, y + h, HudStyle.alpha(OwTheme.ACCENT, fade))
        } else {
            g.fill(x, y, x + w, y + h, HudStyle.alpha(0x14FFFFFF, fade))
        }
        val chipX = x + 6
        val chipY = y + CHOICE_PAD
        val chipColor = if (choice.selected) OwTheme.ACCENT else 0xFF2A2116.toInt()
        val edge = HudStyle.alpha(OwTheme.ACCENT_DIM, fade)
        g.fill(chipX, chipY, chipX + CHIP_W, chipY + CHIP_W, HudStyle.alpha(chipColor, fade))
        g.fill(chipX, chipY, chipX + CHIP_W, chipY + 1, edge)
        g.fill(chipX, chipY + CHIP_W - 1, chipX + CHIP_W, chipY + CHIP_W, edge)
        g.fill(chipX, chipY, chipX + 1, chipY + CHIP_W, edge)
        g.fill(chipX + CHIP_W - 1, chipY, chipX + CHIP_W, chipY + CHIP_W, edge)
        val number = choice.number.toString()
        val numberColor = if (choice.selected) 0xFF1A1208.toInt() else OwTheme.TEXT
        g.text(font, number, chipX + (CHIP_W - font.width(number)) / 2 + 1, chipY + (CHIP_W - font.lineHeight) / 2 + 1, HudStyle.alpha(numberColor, fade), false)
        val textColor = if (choice.selected) OwTheme.ACCENT else OwTheme.TEXT
        var ty = y + CHOICE_PAD + 1
        for (line in choice.lines) {
            g.text(font, line, chipX + CHIP_W + 6, ty, HudStyle.alpha(textColor, fade), true)
            ty += LINE_H
        }
    }

    private fun drawFooter(g: GuiGraphicsExtractor, font: Font, right: Int, y: Int, hint: String, fade: Float, now: Long) {
        val bob = (sin(now / 260.0) + 1.0).toInt()
        val arrowCx = right - 3
        val arrowY = y + font.lineHeight / 2 - 1 + bob
        for (i in 0..2) {
            g.fill(arrowCx - 2 + i, arrowY + i, arrowCx + 3 - i, arrowY + i + 1, HudStyle.alpha(OwTheme.ACCENT, fade))
        }
        var x = right - 10 - font.width(hint)
        g.text(font, hint, x, y + 1, HudStyle.alpha(OwTheme.TEXT_DIM, fade), true)
        x -= 5
        val capW = font.width("SHIFT") + 8
        HudStyle.keycap(g, font, x - capW, y - 1, "SHIFT", fade)
    }

    private companion object {
        const val ID = "dialogue"
        const val PAD_X = 10
        const val PAD_Y = 8
        const val LINE_H = 11
        const val HEADER_H = 12
        const val CHOICE_GAP = 6
        const val CHOICE_SPACING = 3
        const val CHOICE_PAD = 3
        const val CHIP_W = 11
        const val FOOTER_GAP = 6
        const val FADE_MS = 160f
        const val LIFT = 6f
        val BODY_COLOR = 0xFFF0E6CF.toInt()
    }
}
