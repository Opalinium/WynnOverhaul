package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class WynnOverhaulToastHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Toast HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        val toast = WynnOverhaulToastQueue.current()
        val designer = isHudDesignerOpen()
        val title: String
        val subtitle: String
        val detail: String
        val colorArgb: Int
        val style: ToastStyle
        if (toast != null) {
            title = toast.title
            subtitle = toast.subtitle
            detail = toast.detail
            colorArgb = toast.colorArgb
            style = toast.style
        } else if (designer) {
            title = "Level Up!"
            subtitle = "You reached combat level 100!"
            detail = ""
            colorArgb = 0xFFFFD700.toInt()
            style = ToastStyle.CLASSIC
        } else {
            return
        }
        val fraction = if (toast != null) WynnOverhaulToastQueue.elapsedFraction() else 0.5f
        when (style) {
            ToastStyle.CLASSIC -> renderClassic(graphics, title, subtitle, detail, colorArgb, fraction)
            ToastStyle.SOULS -> renderSouls(graphics, title, subtitle, detail, colorArgb, fraction)
        }
    }

    private fun renderClassic(graphics: GuiGraphicsExtractor, title: String, subtitle: String, detail: String, colorArgb: Int, fraction: Float) {
        val settings = WynnOverhaulConfig.current.toast(WynnOverhaulToastQueue.Kind.LEVEL_UP)
        val alpha = (
            when {
                fraction < FADE_IN_END -> 255 * (fraction / FADE_IN_END)
                fraction > FADE_OUT_START -> 255 * (1f - (fraction - FADE_OUT_START) / (1f - FADE_OUT_START))
                else -> 255f
            } * settings.opacity.toFloat()
        ).toInt().coerceIn(0, 255)
        if (alpha <= 0) return

        val font = Minecraft.getInstance().font
        val guiW = graphics.guiWidth()
        val guiH = graphics.guiHeight()
        val textScale = settings.scale.toFloat().coerceIn(0.5f, 4f)
        val total = HudLayoutManager.scale(ID) * textScale
        val subtitleText = if (detail.isBlank()) subtitle else "$subtitle  ·  $detail"
        val (floorW, floorH) = HudLayoutManager.boxSize(ID)
        val boxW = max((floorW / textScale).toInt(), max(font.width(title), font.width(subtitleText)) + PAD * 2)
        val boxH = max((floorH / textScale).toInt(), LINE_HEIGHT * 2 + PAD * 2)

        val configured = HudLayoutManager.bounds(ID, guiW, guiH)
        val centerX = (configured[0] + configured[2]) / 2f
        val centerY = (configured[1] + configured[3]) / 2f
        val drawnW = boxW * total
        val drawnH = boxH * total
        val originX = (centerX - drawnW / 2f).coerceIn(0f, (guiW - drawnW).coerceAtLeast(0f))
        val originY = (centerY - drawnH / 2f).coerceIn(0f, (guiH - drawnH).coerceAtLeast(0f))

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(originX, originY)
        pose.scale(total)
        HudStyle.plate(graphics, 0, 0, boxW, boxH, colorArgb, alpha / 255f)
        HudStyle.fadeRule(graphics, 4, 2, boxW - 8, HudStyle.withAlpha(colorArgb, alpha), leftSolid = true)
        graphics.text(font, title, (boxW - font.width(title)) / 2, PAD, HudStyle.withAlpha(colorArgb, alpha), true)
        graphics.text(font, subtitleText, (boxW - font.width(subtitleText)) / 2, PAD + LINE_HEIGHT, HudStyle.withAlpha(OwTheme.TEXT, alpha), true)
        pose.popMatrix()
    }

    private fun renderSouls(graphics: GuiGraphicsExtractor, title: String, subtitle: String, detail: String, colorArgb: Int, fraction: Float) {
        val settings = WynnOverhaulConfig.current.toast(WynnOverhaulToastQueue.Kind.LEVEL_UP)
        val af = soulsAlpha(fraction) * settings.opacity.toFloat()
        if (af <= 0.02f) return

        val font = Minecraft.getInstance().font
        val guiW = graphics.guiWidth()
        val guiH = graphics.guiHeight()
        val total = HudLayoutManager.scale(ID) * settings.scale.toFloat().coerceIn(0.5f, 4f)

        val kicker = title.uppercase()
        val name = subtitle
        val nameSpacing = NAME_SPACING_START + fraction * NAME_SPACING_GROWTH
        val nameW = spacedWidth(font, name, nameSpacing) * NAME_SCALE
        val kickerW = spacedWidth(font, kicker, KICKER_SPACING)
        val detailW = if (detail.isBlank()) 0f else spacedWidth(font, detail, DETAIL_SPACING)
        val contentW = max(nameW, max(kickerW, detailW))
        val bandW = max(contentW + BAND_PAD * 2, MIN_BAND_W)

        val configured = HudLayoutManager.bounds(ID, guiW, guiH)
        val drawnW = bandW * total
        val centerX = ((configured[0] + configured[2]) / 2f).coerceIn(drawnW / 2f, (guiW - drawnW / 2f).coerceAtLeast(drawnW / 2f))
        val centerY = (configured[1] + configured[3]) / 2f

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(centerX, centerY)
        pose.scale(total)

        fadeBand(graphics, -bandW / 2f, -BAND_H / 2f, bandW, BAND_H, 0.62f * af)

        val accent = colorArgb
        drawSpaced(graphics, font, kicker, -kickerW / 2f, KICKER_Y, KICKER_SPACING, withAlphaF(accent, 0.85f * af), false, 1f)

        pose.pushMatrix()
        pose.translate(0f, NAME_Y)
        pose.scale(NAME_SCALE)
        drawSpaced(graphics, font, name, -(nameW / NAME_SCALE) / 2f, 0f, nameSpacing, withAlphaF(NAME_COLOR, af), true, 1f)
        pose.popMatrix()

        val ruleW = max(contentW * 0.95f, MIN_RULE_W)
        fadeBand(graphics, -ruleW / 2f, RULE_Y, ruleW, 1, 0.9f * af, withRgb(accent))
        val diamondColor = withAlphaF(accent, af)
        if ((diamondColor ushr 24) >= MIN_TEXT_ALPHA) HudStyle.diamond(graphics, 0, RULE_Y.toInt(), DIAMOND_R, diamondColor)

        if (detail.isNotBlank()) {
            drawSpaced(graphics, font, detail, -detailW / 2f, DETAIL_Y, DETAIL_SPACING, withAlphaF(DETAIL_COLOR, 0.8f * af), false, 1f)
        }
        pose.popMatrix()
    }

    private fun soulsAlpha(fraction: Float): Float {
        val fadeIn = HudStyle.smoothstep((fraction / SOULS_FADE_IN_END).coerceIn(0f, 1f))
        val fadeOut = HudStyle.smoothstep(((1f - fraction) / (1f - SOULS_FADE_OUT_START)).coerceIn(0f, 1f))
        return minOf(fadeIn, fadeOut)
    }

    private fun spacedWidth(font: Font, text: String, spacing: Float): Float {
        var w = 0f
        for (c in text) w += font.width(c.toString())
        return w + spacing * (text.length - 1).coerceAtLeast(0)
    }

    private fun drawSpaced(
        graphics: GuiGraphicsExtractor,
        font: Font,
        text: String,
        startX: Float,
        y: Float,
        spacing: Float,
        color: Int,
        shadow: Boolean,
        scale: Float,
    ) {
        if ((color ushr 24) < MIN_TEXT_ALPHA) return
        val pose = graphics.pose()
        var x = startX
        for (c in text) {
            val s = c.toString()
            pose.pushMatrix()
            pose.translate(x, y)
            if (scale != 1f) pose.scale(scale)
            graphics.text(font, s, 0, 0, color, shadow)
            pose.popMatrix()
            x += font.width(s) + spacing
        }
    }

    private fun fadeBand(graphics: GuiGraphicsExtractor, x: Float, y: Float, w: Float, h: Int, maxAlpha: Float, rgb: Int = 0) {
        val slices = SLICES
        val sliceW = w / slices
        for (i in 0 until slices) {
            val t = (i + 0.5f) / slices * 2f - 1f
            val a = (maxAlpha * (1f - abs(t).pow(FALLOFF)) * 255f).roundToInt()
            if (a <= 0) continue
            val x0 = (x + i * sliceW).roundToInt()
            val x1 = (x + (i + 1) * sliceW).roundToInt()
            graphics.fill(x0, y.roundToInt(), x1, y.roundToInt() + h, (a shl 24) or rgb)
        }
    }

    private fun withAlphaF(argb: Int, alpha: Float): Int = HudStyle.withAlpha(argb, (alpha.coerceIn(0f, 1f) * 255f).roundToInt())

    private fun withRgb(argb: Int): Int = argb and 0xFFFFFF

    private companion object {
        const val ID = "toast"
        const val PAD = 6
        const val LINE_HEIGHT = 11
        const val FADE_IN_END = 0.08f
        const val FADE_OUT_START = 0.85f

        const val SOULS_FADE_IN_END = 0.22f
        const val SOULS_FADE_OUT_START = 0.68f
        const val SLICES = 56
        const val FALLOFF = 2.2f
        const val BAND_H = 72
        const val BAND_PAD = 56f
        const val MIN_BAND_W = 260f
        const val MIN_RULE_W = 120f
        const val KICKER_Y = -27f
        const val NAME_Y = -14f
        const val RULE_Y = 15f
        const val DETAIL_Y = 22f
        const val NAME_SCALE = 1.7f
        const val NAME_SPACING_START = 0.6f
        const val NAME_SPACING_GROWTH = 2.4f
        const val KICKER_SPACING = 2.6f
        const val DETAIL_SPACING = 1.4f
        const val DIAMOND_R = 2
        const val MIN_TEXT_ALPHA = 6
        const val NAME_COLOR = 0xFFF2E6C4.toInt()
        const val DETAIL_COLOR = 0xFFC9BC9A.toInt()
    }
}
