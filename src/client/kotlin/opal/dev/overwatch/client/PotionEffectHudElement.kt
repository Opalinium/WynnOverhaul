package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import opal.dev.overwatch.Overwatch

class PotionEffectHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Potion effect HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame) return
        val config = OverwatchConfig.current
        if (!config.customPotionHudEnabled) return
        val player = Minecraft.getInstance().player

        val rows = ArrayList<Row>()
        if (player != null) {
            for (instance in player.activeEffects.filter { it.showIcon() }.sortedDescending()) {
                rows.add(formatEffectRow(instance))
            }
        }
        for (buff in WynnBuffTracker.active.sortedByDescending { it.remainingSeconds }) {
            rows.add(formatBuffRow(buff))
        }
        if (rows.isEmpty()) return

        val font = Minecraft.getInstance().font
        val (boxW, _) = HudLayoutManager.stableSize(ID, rows.maxOf { font.width(it.text) + font.width(it.timer) + TIMER_GAP } + ICON_SIZE + 6, LINE_HEIGHT * rows.size)

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
        for (row in rows) {
            graphics.fill(ox, y + 1, ox + boxW, y + LINE_HEIGHT - 1, ROW_BG)
            graphics.fill(ox, y + 1, ox + 2, y + LINE_HEIGHT - 1, row.color)
            if (row.sprite != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite, ox + 3, y, ICON_SIZE, ICON_SIZE)
            }
            graphics.text(font, row.text, ox + ICON_SIZE + 6, y + TEXT_Y_OFFSET, row.color, true)
            graphics.text(font, row.timer, ox + boxW - font.width(row.timer) - 4, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += LINE_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private data class Row(val text: String, val timer: String, val color: Int, val sprite: Identifier?)

    private fun formatEffectRow(instance: MobEffectInstance): Row {
        val effect = instance.effect.value()
        val amplifier = instance.amplifier
        val label = if (amplifier > 0) "${effect.displayName.string} ${romanNumeral(amplifier)}" else effect.displayName.string
        val duration = if (instance.isInfiniteDuration) INFINITE_SYMBOL else formatDuration(instance.duration / 20)
        val color = when (effect.category) {
            MobEffectCategory.BENEFICIAL -> COLOR_BENEFICIAL
            MobEffectCategory.HARMFUL -> COLOR_HARMFUL
            else -> COLOR_NEUTRAL
        }
        return Row(label, duration, color, Hud.getMobEffectSprite(instance.effect))
    }

    private fun formatBuffRow(buff: WynnBuffTracker.Buff): Row {
        val sign = if (buff.amount >= 0) "+" else ""
        val color = if (buff.amount >= 0) COLOR_BENEFICIAL else COLOR_HARMFUL
        return Row("$sign${buff.amount} ${buff.name}", formatDuration(buff.remainingSeconds), color, null)
    }

    private fun romanNumeral(amplifier: Int): String = ROMAN.getOrElse(amplifier) { (amplifier + 1).toString() }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val ID = "potion_effects"
        const val LINE_HEIGHT = 18
        const val ICON_SIZE = 18
        const val TEXT_Y_OFFSET = 5
        const val TIMER_GAP = 12
        const val ROW_BG = 0x6E0E0B08
        const val INFINITE_SYMBOL = "∞"
        val COLOR_BENEFICIAL = OwTheme.GOOD
        val COLOR_HARMFUL = OwTheme.BAD
        val COLOR_NEUTRAL = OwTheme.TEXT_DIM
        val ROMAN = listOf("", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
    }
}
