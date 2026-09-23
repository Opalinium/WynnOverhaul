package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class AbilityCooldownHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Ability cooldown HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame) return
        val config = OverwatchConfig.current
        if (!config.abilityCooldownHudEnabled) return
        val cooldowns = WynnStatusEffectTracker.activeCooldowns.sortedBy { it.displaySeconds() }
        if (cooldowns.isEmpty()) return

        val font = Minecraft.getInstance().font
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, cooldowns.maxOf { font.width(rowText(it)) } + BAR_PAD * 2, ROW_HEIGHT * cooldowns.size)

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
        var y = oy
        for (cooldown in cooldowns) {
            val text = rowText(cooldown)
            if (OverwatchConfig.current.hudPanelsEnabled) {
                graphics.fill(ox, y, ox + boxW, y + ROW_HEIGHT - 2, BG_COLOR)
            }
            val fraction = cooldown.displayFraction()
            val barWidth = ((boxW - BAR_PAD * 2) * fraction).toInt().coerceAtLeast(0)
            if (barWidth > 0) {
                graphics.fill(ox + BAR_PAD, y, ox + BAR_PAD + barWidth, y + ROW_HEIGHT - 2, BAR_COLOR)
            }
            if (OverwatchConfig.current.hudPanelsEnabled) {
                graphics.outline(ox, y, boxW, ROW_HEIGHT - 2, OwTheme.BORDER)
            }
            graphics.text(font, text, ox + BAR_PAD, y + TEXT_Y_OFFSET, OwTheme.TEXT, true)
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun rowText(cooldown: WynnStatusEffectTracker.Cooldown): String {
        val seconds = cooldown.displaySeconds()
        val timer = if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else "${seconds}s"
        return "${cooldown.name}  $timer"
    }

    private companion object {
        const val ID = "ability_cooldowns"
        const val ROW_HEIGHT = 14
        const val BAR_PAD = 3
        const val TEXT_Y_OFFSET = 3
        val BG_COLOR = OwTheme.PANEL
        const val BAR_COLOR = 0x80C9A227.toInt()
    }
}
