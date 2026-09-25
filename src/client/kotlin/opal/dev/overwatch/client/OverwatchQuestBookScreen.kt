package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class OverwatchQuestBookScreen(parent: Screen) : OwScreen(Component.literal("Overwatch - Quest Book"), parent) {
    private var query: String = ""

    override fun init() {
        super.init()
        val left = contentLeft
        val w = contentWidth
        var y = contentTop

        val trackedQuest = WynnScoreboardTracker.current?.let { WynncraftQuests.findTracked(it.name) }
        if (trackedQuest != null) {
            addRenderableWidget(
                OwButton(left, y, w, OwTheme.ROW_H - 2, Component.literal("Tracked: ${trackedQuest.name}"), accent = true) {
                    Minecraft.getInstance().setScreenAndShow(OverwatchQuestDetailScreen(trackedQuest, this))
                },
            )
            y += OwTheme.ROW_H
        }

        val searchBox = OwTextField(font, left, y, w, OwTheme.ROW_H - 4)
        searchBox.setMaxLength(64)
        searchBox.setValue(query)
        searchBox.setHint(Component.literal("Search quest name..."))
        searchBox.setResponder {
            query = it
            rebuildWidgets()
        }
        addRenderableWidget(searchBox)
        setInitialFocus(searchBox)
        y += OwTheme.ROW_H

        val results = WynncraftQuests.search(query)
        addRenderableWidget(OwLabel(left, y, w, 10, statusText(results.size), OwTheme.TEXT_DIM))
        y += 14

        val rows = mutableListOf<Pair<AbstractWidget, Int>>()
        for (quest in results) {
            rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("${quest.name}  (Lv. ${quest.combatLevel})")) {
                Minecraft.getInstance().setScreenAndShow(OverwatchQuestDetailScreen(quest, this))
            } to OwTheme.ROW_H
        }

        installScrollList(rows, left, y, w, contentBottom - y)
    }

    private fun statusText(count: Int): String =
        if (WynncraftQuests.all.isEmpty()) "No bundled quest data found" else "$count quest${if (count == 1) "" else "s"} match"
}
