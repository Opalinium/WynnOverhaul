package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

object MountTooltipFeature {

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val config = OverwatchConfig.current
            if (config.mountTooltipEnabled && OverwatchGate.inGame) {
                val reading = MountTooltipParser.parse(stack)
                if (reading != null) {
                    MountRegistry.note(reading)
                    val result = MountFeedingSummary.compute(reading)
                    if (result != null) lines.addAll(format(result))
                }
            }
        }
    }

    private fun format(result: MountShoppingList): List<Component> {
        val lines = ArrayList<Component>()
        lines.add(Component.literal(""))
        if (result.maxUnknown) {
            if (result.trainable.isNotEmpty()) {
                lines.add(Component.literal("Trainable by riding: ${result.trainable.joinToString(", ")}").withStyle(ChatFormatting.GRAY))
            }
            lines.add(Component.literal("Open in the feeder for the feeding plan").withStyle(ChatFormatting.DARK_GRAY))
            return lines
        }
        if (result.allMaxed) {
            lines.add(Component.literal("All stats maxed!").withStyle(ChatFormatting.GREEN))
            return lines
        }
        if (result.noMaterialsAvailable) {
            lines.add(Component.literal("Train to at least level 1 to see feeding info").withStyle(ChatFormatting.GRAY))
            return lines
        }
        lines.add(Component.literal("Optimal Feeding:").withStyle(ChatFormatting.GOLD))
        val merged = MountFeedingSummary.mergedFeedCounts(result)
        for ((name, count) in merged.take(4)) {
            lines.add(Component.literal(" $name x$count").withStyle(ChatFormatting.WHITE))
        }
        if (merged.size > 4) {
            lines.add(Component.literal(" +${merged.size - 4} more material${if (merged.size - 4 == 1) "" else "s"}").withStyle(ChatFormatting.DARK_GRAY))
        }
        lines.add(Component.literal("Total: ${result.grandTotal} feeds").withStyle(ChatFormatting.YELLOW))
        val trainPhase = result.phases.firstOrNull { it.isTraining }
        if (trainPhase != null) lines.add(Component.literal(trainPhase.label).withStyle(ChatFormatting.DARK_GRAY))
        if (result.unsolvable.isNotEmpty()) {
            val blocked = result.unsolvable.joinToString(", ") { MountMaterials.STATS[it] }
            lines.add(Component.literal("Can't max: $blocked").withStyle(ChatFormatting.RED))
        }
        return lines
    }
}
