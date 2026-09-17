package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class LootrunHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Lootrun HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        val config = OverwatchConfig.current
        if (!config.lootrunEnabled || !config.lootrunHudEnabled) return
        if (LootrunModel.state == LootrunModel.State.NOT_RUNNING) return

        val lines = ArrayList<Pair<String, Int>>()
        lines.add(stateLine() to STATE_COLOR)
        LootrunModel.timeLeftSeconds?.let {
            lines.add("Time left: %d:%02d".format(it / 60, it % 60) to TEXT_COLOR)
        }
        if (LootrunModel.challengesTotal > 0) {
            lines.add("Challenges: ${LootrunModel.challengesDone}/${LootrunModel.challengesTotal}" to TEXT_COLOR)
        }
        if (config.lootrunRecorderEnabled && LootrunRecorder.recordedPointCount > 0) {
            lines.add("Recording: ${LootrunRecorder.recordedPointCount} pts" to DIM_COLOR)
        }

        val font = Minecraft.getInstance().font
        val contentW = lines.maxOf { font.width(it.first) } + PAD * 2
        val contentH = lines.size * LINE_H + PAD * 2
        val x = MARGIN
        val y = MARGIN

        graphics.fill(x, y, x + contentW, y + contentH, BG_COLOR)
        graphics.fill(x, y, x + contentW, y + 1, BORDER_COLOR)
        graphics.fill(x, y + contentH - 1, x + contentW, y + contentH, BORDER_COLOR)
        graphics.fill(x, y, x + 1, y + contentH, BORDER_COLOR)
        graphics.fill(x + contentW - 1, y, x + contentW, y + contentH, BORDER_COLOR)

        var ly = y + PAD
        for ((text, color) in lines) {
            graphics.text(font, text, x + PAD, ly, color)
            ly += LINE_H
        }
    }

    private fun stateLine(): String = when (LootrunModel.state) {
        LootrunModel.State.CHOOSING_BEACON -> "Choose a beacon!"
        LootrunModel.State.IN_TASK -> taskLabel()
        LootrunModel.State.NOT_RUNNING -> ""
    }

    private fun taskLabel(): String = when (LootrunModel.taskType) {
        LootrunModel.TaskType.LOOT -> "Loot ${LootrunModel.lootCurrent}/${LootrunModel.lootTotal} chests!"
        LootrunModel.TaskType.SLAY -> "Slay!"
        LootrunModel.TaskType.DESTROY -> "Destroy the objective!"
        LootrunModel.TaskType.DEFEND -> "Defend!"
        LootrunModel.TaskType.UNKNOWN, null -> "In task"
    }

    private companion object {
        const val MARGIN = 6
        const val PAD = 6
        const val LINE_H = 10
        val BG_COLOR = 0xE0121016.toInt()
        val BORDER_COLOR = 0xFF4A4658.toInt()
        val STATE_COLOR = 0xFFE0C060.toInt()
        val TEXT_COLOR = 0xFFFFFFFF.toInt()
        val DIM_COLOR = 0xFF888888.toInt()
    }
}
