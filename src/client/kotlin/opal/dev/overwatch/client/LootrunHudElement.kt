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
        if (!OverwatchGate.inGame) return
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
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, lines.maxOf { font.width(it.first) } + PAD * 2, lines.size * LINE_H + PAD * 2)
        val (x, y) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        OwTheme.hudPanel(graphics, x, y, boxW, boxH)

        var ly = y + PAD
        for ((text, color) in lines) {
            graphics.text(font, text, x + PAD, ly, color, true)
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
        const val ID = "lootrun"
        const val PAD = 6
        const val LINE_H = 10
        val STATE_COLOR = OwTheme.ACCENT
        val TEXT_COLOR = OwTheme.TEXT
        val DIM_COLOR = OwTheme.TEXT_DIM
    }
}
