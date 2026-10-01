package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class WynnOverhaulQuestDetailScreen(
    private val quest: WynncraftQuests.Quest,
    parent: Screen,
) : OwScreen(Component.literal(quest.name), parent) {
    override fun init() {
        super.init()
        val left = contentLeft
        val w = contentWidth
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun line(text: String, color: Int = OwTheme.TEXT) {
            rows += OwLabel(left, 0, w, LINE_HEIGHT, text, color) to LINE_HEIGHT
        }

        var statIndex = 0
        fun stat(label: String, value: String, color: Int = OwTheme.TEXT) {
            rows += OwStatRow(left, 0, w, STAT_HEIGHT, label, value, color, stripe = statIndex++ % 2 == 0) to STAT_HEIGHT
        }

        fun heading(text: String) {
            rows += OwSectionHeader(left, 0, w, text) to (LINE_HEIGHT + 4)
        }

        fun gap(height: Int) {
            rows += OwLabel(left, 0, w, height, "") to height
        }

        val tracked = WynnScoreboardTracker.current
        if (tracked != null && WynncraftQuests.findTracked(tracked.name) === quest) {
            heading("Currently tracked")
            if (tracked.nextTask.isNotBlank()) stat("Objective", tracked.nextTask, OwTheme.GOOD)
            gap(4)
            statIndex = 0
        }

        val playerLevel = WynnLevelTracker.level
        val levelColor = when {
            playerLevel == null -> OwTheme.TEXT
            playerLevel >= quest.combatLevel -> OwTheme.GOOD
            else -> OwTheme.BAD
        }
        heading("Requirements")
        stat("Level", levelRequirements(), levelColor)
        stat("Length", quest.length.ifBlank { "Unknown" })
        if (quest.province.isNotBlank() || quest.location.isNotBlank()) {
            stat("Location", listOfNotNull(quest.location.ifBlank { null }, quest.province.ifBlank { null }).joinToString(", "))
        }
        if (quest.npc.isNotBlank()) stat("Starter NPC", quest.npc)
        if (quest.requiredQuest.isNotBlank()) stat("Requires quest", quest.requiredQuest)
        if (quest.requiredItem.isNotBlank()) stat("Requires item", quest.requiredItem)
        if (quest.tags.isNotBlank()) stat("Tags", quest.tags)
        gap(6)

        heading("Rewards")
        statIndex = 0
        stat("Emeralds", "${quest.emeralds}", OwTheme.GOOD)
        stat("Experience", "${quest.experience} XP")
        if (quest.rewards.isEmpty()) {
            line("(no other listed rewards)", OwTheme.TEXT_DIM)
        } else {
            for (reward in quest.rewards) line("• $reward")
        }
        gap(8)

        line("Reference data from wynncraft.wiki.gg -- may drift from the live game.", OwTheme.TEXT_FAINT)

        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    private fun levelRequirements(): String {
        val parts = ArrayList<String>()
        parts.add("Combat ${quest.combatLevel}")
        if (quest.miningLevel > 0) parts.add("Mining ${quest.miningLevel}")
        if (quest.woodcuttingLevel > 0) parts.add("Woodcutting ${quest.woodcuttingLevel}")
        if (quest.farmingLevel > 0) parts.add("Farming ${quest.farmingLevel}")
        if (quest.fishingLevel > 0) parts.add("Fishing ${quest.fishingLevel}")
        return parts.joinToString(", ")
    }

    private companion object {
        const val LINE_HEIGHT = 12
        const val STAT_HEIGHT = 14
    }
}
