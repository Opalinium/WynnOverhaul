package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul

class AbilityCooldownHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Ability cooldown HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        val config = WynnOverhaulConfig.current
        if (!config.abilityCooldownHudEnabled) return
        val cooldowns = WynnStatusEffectTracker.activeCooldowns.sortedBy { it.displaySeconds() }
        if (cooldowns.isEmpty()) return

        val font = Minecraft.getInstance().font

        val (boxW, boxH) = HudLayoutManager.stableSize(ID, cooldowns.maxOf { font.width(it.name) + font.width(timerText(it)) + 14 } + BAR_PAD * 2, ROW_HEIGHT * cooldowns.size)

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
        for (cooldown in cooldowns) {
            HudStyle.bar(graphics, ox, y, boxW, ROW_HEIGHT - 2, cooldown.displayFraction(), COOLDOWN_FILL, notches = false)
            val timer = timerText(cooldown)
            graphics.text(font, cooldown.name, ox + BAR_PAD + 1, y + TEXT_Y_OFFSET, OwTheme.TEXT, true)
            graphics.text(font, timer, ox + boxW - font.width(timer) - BAR_PAD - 1, y + TEXT_Y_OFFSET, OwTheme.TEXT_DIM, true)
            y += ROW_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun timerText(cooldown: WynnStatusEffectTracker.Cooldown): String {
        val seconds = cooldown.displaySeconds()
        return if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else "${seconds}s"
    }

    private companion object {
        const val ID = "ability_cooldowns"
        const val ROW_HEIGHT = 14
        const val BAR_PAD = 3
        const val TEXT_Y_OFFSET = 3
        const val COOLDOWN_FILL = 0xFF9C7A22.toInt()
    }
}
