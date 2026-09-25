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

        fun heading(text: String) {
            rows += OwSectionHeader(left, 0, w, text) to (LINE_HEIGHT + 4)
        }

        fun gap(height: Int) {
            rows += OwLabel(left, 0, w, height, "") to height
        }

        val tracked = WynnScoreboardTracker.current
        if (tracked != null && WynncraftQuests.findTracked(tracked.name) === quest) {
            line("Currently tracked", OwTheme.GOOD)
            if (tracked.nextTask.isNotBlank()) {
                line("Objective: ${tracked.nextTask}")
            }
            gap(4)
        }

        line("Level: ${levelRequirements()}")
        line("Length: ${quest.length.ifBlank { "Unknown" }}")
        if (quest.province.isNotBlank() || quest.location.isNotBlank()) {
            line("Location: ${listOfNotNull(quest.location.ifBlank { null }, quest.province.ifBlank { null }).joinToString(", ")}")
        }
        if (quest.npc.isNotBlank()) line("Starter NPC: ${quest.npc}")
        if (quest.requiredQuest.isNotBlank()) line("Requires quest: ${quest.requiredQuest}")
        if (quest.requiredItem.isNotBlank()) line("Requires item: ${quest.requiredItem}")
        if (quest.tags.isNotBlank()) line("Tags: ${quest.tags}")
        gap(4)

        heading("Rewards")
        line("${quest.emeralds} emeralds, ${quest.experience} XP")
        if (quest.rewards.isEmpty()) {
            line("(no other listed rewards)")
        } else {
            for (reward in quest.rewards) line("- $reward")
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
    }
}
