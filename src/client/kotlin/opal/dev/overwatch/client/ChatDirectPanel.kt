package opal.dev.overwatch.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox

object ChatDirectPanel {
    private class Row(val name: String?, val group: String)

    private const val PANEL_W = 190
    private const val ROW_H = 12
    private const val PAD = 4
    private const val ANCHOR_GAP = 22
    private const val KEY_ENTER = 257
    private const val KEY_KP_ENTER = 335
    private const val KEY_TAB = 258
    private const val KEY_UP = 265
    private const val KEY_DOWN = 264

    private var rows: List<Row> = emptyList()
    private var selectable: List<WynnDirectMessages.Suggestion> = emptyList()
    private var selected = 0
    private var left = 0
    private var top = 0
    private var width = 0
    private var height = 0
    private var active = false
    private var lastFilter: String? = null

    private fun trigger(value: String): String? = if (value.startsWith("@")) value.substring(1) else null

    private fun rebuild(filter: String) {
        val list = WynnDirectMessages.suggestions(filter)
        selectable = list
        val built = ArrayList<Row>()
        var group: String? = null
        for (item in list) {
            if (item.group != group) {
                group = item.group
                built.add(Row(null, item.group))
            }
            built.add(Row(item.name, item.group))
        }
        rows = built
        if (filter != lastFilter) selected = 0
        lastFilter = filter
        selected = selected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    fun render(g: GuiGraphicsExtractor, font: Font, input: EditBox, mouseX: Int, mouseY: Int) {
        val filter = trigger(input.value)
        if (filter == null) {
            active = false
            lastFilter = null
            return
        }
        active = true
        WynnDirectMessages.requestGuildMembers()
        rebuild(filter)

        val rowCount = rows.size.coerceAtLeast(1)
        width = PANEL_W
        height = rowCount * ROW_H + PAD * 2
        left = input.x
        top = input.y - ANCHOR_GAP - height
        HudStyle.plate(g, left, top, width, height, OwTheme.ACCENT)

        if (rows.isEmpty()) {
            g.text(font, "No players match", left + PAD + 2, top + PAD + 2, OwTheme.TEXT_DIM, true)
            return
        }
        var y = top + PAD
        var selectableIndex = 0
        for (row in rows) {
            if (row.name == null) {
                g.text(font, row.group.uppercase(), left + PAD + 2, y + 2, OwTheme.ACCENT_DIM, true)
                HudStyle.fadeRule(g, left + PAD + 4 + font.width(row.group.uppercase()), y + ROW_H / 2, width - PAD * 2 - font.width(row.group.uppercase()) - 8, OwTheme.HAIRLINE)
            } else {
                val hovered = mouseX in left until left + width && mouseY in y until y + ROW_H
                if (hovered) selected = selectableIndex
                if (selectableIndex == selected) {
                    g.fill(left + 2, y, left + width - 2, y + ROW_H, HudStyle.alpha(OwTheme.ACCENT, 0.2f))
                    g.fill(left + 2, y, left + 4, y + ROW_H, OwTheme.ACCENT)
                }
                val color = if (selectableIndex == selected) OwTheme.ACCENT else OwTheme.TEXT
                g.text(font, row.name, left + PAD + 6, y + 2, color, true)
                val unread = WynnDirectMessages.recent().firstOrNull { it.name.equals(row.name, ignoreCase = true) }?.unread ?: 0
                if (unread > 0) {
                    val badge = unread.toString()
                    g.text(font, badge, left + width - PAD - font.width(badge) - 4, y + 2, OwTheme.GOOD, true)
                }
                selectableIndex++
            }
            y += ROW_H
        }
    }

    fun keyPressed(key: Int, input: EditBox): Boolean {
        if (!active || trigger(input.value) == null) return false
        if (selectable.isEmpty()) return false
        when (key) {
            KEY_UP -> selected = (selected - 1 + selectable.size) % selectable.size
            KEY_DOWN -> selected = (selected + 1) % selectable.size
            KEY_TAB, KEY_ENTER, KEY_KP_ENTER -> choose(selectable[selected].name, input)
            else -> return false
        }
        return true
    }

    fun mouseClicked(mx: Double, my: Double, input: EditBox): Boolean {
        if (!active || trigger(input.value) == null) return false
        if (mx < left || mx >= left + width || my < top || my >= top + height) return false
        var y = top + PAD
        var index = 0
        for (row in rows) {
            if (row.name != null) {
                if (my >= y && my < y + ROW_H) {
                    choose(selectable[index].name, input)
                    return true
                }
                index++
            }
            y += ROW_H
        }
        return true
    }

    private fun choose(name: String, input: EditBox) {
        WynnChatChannels.selectDirect(name)
        input.value = ""
        active = false
    }
}
