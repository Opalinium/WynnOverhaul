package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class SearchHelpScreen(private val kind: Kind, parent: Screen) : OwScreen(Component.literal(kind.title), parent) {
    enum class Kind(val title: String, val intro: String, val rows: List<Pair<String, String>>, val examples: List<String>) {
        SETTINGS(
            "Settings search",
            "Type any words to find settings across every page. A setting shows if each word appears in its name, its description or the section it sits in. Results are the real controls, so you can change them right there.",
            listOf(
                "chat" to "Everything that mentions chat.",
                "fade hud" to "Words can be in any order, and all of them have to match.",
                "toast" to "Notification and toast options.",
            ),
            listOf("cooldown", "damage numbers", "camera"),
        ),
        ITEMS(
            "Item search",
            "Type words to match item names and descriptions. Every word has to match. Add filters with key:value, and put a minus in front of anything to exclude it.",
            listOf(
                "tier:legendary" to "Rarity. Normal, unique, rare, legendary, fabled or mythic. A prefix such as leg works.",
                "type:weapon" to "Category. Weapons, armor, accessories, consumables, materials, quest or misc.",
                "lvl:60-80" to "Combat level. Also lvl>=60, lvl<=60, lvl>60, lvl<60 or an exact number.",
                "name:spear" to "Only look in the item name.",
                "lore:thorns" to "Only look in the item description and stats.",
                "-tier:normal" to "A minus in front excludes matches. Works on any filter or word.",
                "\"fire sword\"" to "Quotes keep a phrase together.",
            ),
            listOf("tier:fabled type:weapon", "lvl:100-105 -type:armor", "thorns -tier:normal"),
        ),
        JOURNAL(
            "Journal search",
            "Type words to match activity names and descriptions. Every word has to match. Add filters with key:value, and put a minus in front of anything to exclude it.",
            listOf(
                "type:cave" to "Activity type, such as quest, cave, dungeon, raid, discovery or boss altar.",
                "status:available" to "Started, available, unavailable or completed.",
                "dist:near" to "Distance. Very near, near, medium or far.",
                "len:short" to "Length. Short, medium or long.",
                "diff:easy" to "Difficulty. Easy, medium or hard.",
                "lvl<=40" to "Level requirement. Also lvl>=40, lvl:30-50 or an exact number.",
                "desc:dragon" to "Only look in the description.",
                "-status:completed" to "A minus in front excludes matches.",
            ),
            listOf("type:cave dist:near", "-status:completed lvl<=60", "type:quest status:available"),
        ),
    }

    override fun init() {
        super.init()
        val left = contentLeft
        val w = contentWidth
        val rows = ArrayList<Pair<AbstractWidget, Int>>()
        fun line(text: String, color: Int = OwTheme.TEXT_DIM) {
            for (wrapped in HudStyle.wrap(font, text, w)) rows += OwLabel(left, 0, w, LINE_H, wrapped, color) to LINE_H
        }
        line(kind.intro)
        line("")
        for ((syntax, meaning) in kind.rows) {
            rows += OwLabel(left, 0, w, LINE_H, syntax, OwTheme.ACCENT) to LINE_H
            for (wrapped in HudStyle.wrap(font, meaning, w - INDENT)) rows += OwLabel(left + INDENT, 0, w - INDENT, LINE_H, wrapped, OwTheme.TEXT_DIM) to LINE_H
        }
        line("")
        rows += OwLabel(left, 0, w, LINE_H, "Examples", OwTheme.TEXT) to LINE_H
        for (example in kind.examples) rows += OwLabel(left + INDENT, 0, w - INDENT, LINE_H, example, OwTheme.ACCENT) to LINE_H
        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    companion object {
        private const val LINE_H = 12
        private const val INDENT = 10

        fun button(x: Int, y: Int, height: Int, kind: Kind, from: Screen): OwButton =
            OwButton(x, y, height + 4, height, Component.literal("?")) {
                Minecraft.getInstance().setScreenAndShow(SearchHelpScreen(kind, from))
            }
    }
}
