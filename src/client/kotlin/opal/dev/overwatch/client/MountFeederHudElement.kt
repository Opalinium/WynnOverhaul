package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import opal.dev.overwatch.Overwatch
import opal.dev.overwatch.mixin.client.ContainerScreenHoveredSlotAccessor

class MountFeederHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Mount feeder HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        val config = OverwatchConfig.current
        if (!config.mountFeederHudEnabled) return
        val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!screen.title.string.contains(MOUNT_FEEDER_TITLE_MARKER)) return
        val hoveredSlot = (screen as ContainerScreenHoveredSlotAccessor).`overwatch$getHoveredSlot`() ?: return
        val reading = MountTooltipParser.parse(hoveredSlot.item) ?: return
        val result = MountFeedingSummary.compute(reading) ?: return

        val font = Minecraft.getInstance().font
        val contentW = PANEL_W - PAD * 2
        val wrapped = ArrayList<Pair<String, Int>>()

        fun addLine(text: String, color: Int) {
            for (w in wrap(font, text, contentW)) wrapped.add(w to color)
        }

        addLine(reading.name, HEADER_COLOR)
        when {
            result.allMaxed -> addLine("All stats maxed!", GREEN)
            result.noMaterialsAvailable -> addLine("Train to at least level 1 first", GRAY)
            else -> {
                for (phase in result.phases) {
                    if (phase.isTraining) {
                        addLine(phase.label, GOLD)
                        continue
                    }
                    for ((name, count) in phase.feedCounts.entries.sortedByDescending { it.value }) {
                        val mat = MountMaterials.ALL.first { it.name == name }
                        val raises = mat.points.indices.filter { mat.points[it] > 0 }
                            .joinToString(", ") { "${MountMaterials.STATS[it]} +${mat.points[it]}" }
                        addLine("$name x$count", WHITE)
                        addLine("  $raises", DARK_GRAY)
                    }
                }
                addLine("Total: ${result.grandTotal} feeds", YELLOW)
                if (result.unsolvable.isNotEmpty()) {
                    addLine("Can't max: ${result.unsolvable.joinToString(", ") { MountMaterials.STATS[it] }}", RED)
                }
            }
        }

        val panelX = graphics.guiWidth() - PANEL_W - MARGIN
        val panelY = MARGIN
        val panelH = wrapped.size * LINE_H + PAD * 2

        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, BG_COLOR)
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, BORDER_COLOR)
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, BORDER_COLOR)
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER_COLOR)
        graphics.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, BORDER_COLOR)

        var y = panelY + PAD
        for ((text, color) in wrapped) {
            graphics.text(font, text, panelX + PAD, y, color)
            y += LINE_H
        }
    }

    private fun wrap(font: Font, text: String, maxWidth: Int): List<String> {
        if (font.width(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private companion object {
        const val MOUNT_FEEDER_TITLE_MARKER = "󏿭"
        const val PANEL_W = 230
        const val MARGIN = 6
        const val PAD = 6
        const val LINE_H = 10
        val BG_COLOR = 0xE0121016.toInt()
        val BORDER_COLOR = 0xFF4A4658.toInt()
        val HEADER_COLOR = 0xFFE0C060.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val GRAY = 0xFFAAAAAA.toInt()
        val DARK_GRAY = 0xFF777777.toInt()
        val GREEN = 0xFF55FF55.toInt()
        val YELLOW = 0xFFFFFF55.toInt()
        val GOLD = 0xFFE0C060.toInt()
        val RED = 0xFFFF5555.toInt()
    }
}
