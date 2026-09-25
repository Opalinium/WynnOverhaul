package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.mixin.client.ContainerScreenHoveredSlotAccessor

class MountFeederHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Mount feeder HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        val config = WynnOverhaulConfig.current
        if (!config.mountFeederHudEnabled) return
        val screen = Minecraft.getInstance().gui.screen() as? AbstractContainerScreen<*> ?: return
        if (!screen.title.string.contains(MOUNT_FEEDER_TITLE_MARKER)) return

        val hoveredSlot = (screen as ContainerScreenHoveredSlotAccessor).`wynnoverhaul$getHoveredSlot`()
        val readings = LinkedHashMap<String, MountReading>()
        hoveredSlot?.item?.takeIf { !it.isEmpty }?.let { MountTooltipParser.parse(it) }?.let {
            readings[MountRegistry.keyOf(it.typeName, it.name, it.potential)] = it
        }
        Minecraft.getInstance().player?.containerMenu?.slots?.forEach { slot ->
            if (slot.item.isEmpty) return@forEach
            val parsed = MountTooltipParser.parse(slot.item) ?: return@forEach
            readings.putIfAbsent(MountRegistry.keyOf(parsed.typeName, parsed.name, parsed.potential), parsed)
        }
        if (readings.isEmpty()) return
        readings.values.forEach { MountRegistry.note(it) }

        val font = Minecraft.getInstance().font

        val textW = HudLayoutManager.boxSize(ID).first - PAD * 2
        val wrapped = ArrayList<Pair<String, Int>>()

        fun addLine(text: String, color: Int) {
            for (w in HudStyle.wrap(font, text, textW)) wrapped.add(w to color)
        }

        readings.values.forEachIndexed { index, reading ->
            if (index > 0) addLine("", WHITE)
            val result = MountFeedingSummary.compute(reading) ?: return@forEachIndexed
            addLine(reading.name, HEADER_COLOR)
            when {
                result.maxUnknown -> {
                    if (result.trainable.isNotEmpty()) {
                        addLine("Trainable by riding: ${result.trainable.joinToString(", ")}", GRAY)
                    }
                    addLine("Feeding plan needs max values", DARK_GRAY)
                }
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

            val limits = IntArray(8) { reading.stats.getValue(MountMaterials.STATS[it]).limit }
            val avgExact = limits.sum() / 8.0
            val avgUp = kotlin.math.ceil(avgExact).toInt()
            val feedTime = MountFeedingData.feedTimeFor(avgUp)
            addLine("Feed time: ~${feedTime.third} each (avg limit $avgUp)", GRAY)
            if (avgExact >= MountFeedingData.BREEDING_AVERAGE_LIMIT) {
                addLine("Breeding-ready (avg limit ${"%.1f".format(avgExact)})", GREEN)
            } else {
                addLine("Avg limit ${"%.1f".format(avgExact)} / ${MountFeedingData.BREEDING_AVERAGE_LIMIT} for breeding", GRAY)
            }
        }

        val (boxW, boxH) = HudLayoutManager.stableSize(ID, HudLayoutManager.boxSize(ID).first, wrapped.size * LINE_H + PAD * 2)
        val (panelX, panelY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        OwTheme.hudPanel(graphics, panelX, panelY, boxW, boxH)

        var y = panelY + PAD
        for ((text, color) in wrapped) {
            graphics.text(font, text, panelX + PAD, y, color, true)
            y += LINE_H
        }
    }

    private companion object {
        const val MOUNT_FEEDER_TITLE_MARKER = "󏿭"
        const val ID = "mount_feeder"
        const val PAD = 6
        const val LINE_H = 10
        val HEADER_COLOR = OwTheme.ACCENT
        val WHITE = OwTheme.TEXT
        val GRAY = OwTheme.TEXT_DIM
        val DARK_GRAY = OwTheme.TEXT_FAINT
        val GREEN = OwTheme.GOOD
        val YELLOW = OwTheme.WARN
        val GOLD = OwTheme.ACCENT
        val RED = OwTheme.BAD
    }
}
