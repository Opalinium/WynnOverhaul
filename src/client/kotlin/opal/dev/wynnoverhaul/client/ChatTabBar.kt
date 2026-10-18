package opal.dev.wynnoverhaul.client

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Style
import kotlin.math.min

object ChatTabBar {
    const val HEIGHT = 11
    const val OFFSET = 14
    private const val PAD = 5
    private const val GAP = 3
    private const val MAX_CHIPS = 4
    private const val CHIP_TEXT_MAX = 64
    private const val HOVER_WASH = 0x1AFFFFFF

    private class Tab(val label: String, val active: Boolean, val accent: Int, val badge: Int, val action: () -> Unit)

    private class Hit(val x0: Int, val x1: Int, val action: () -> Unit)

    private val hits = ArrayList<Hit>()
    private var barY = 0

    fun top(input: EditBox): Int = input.y - OFFSET

    private fun accentOf(channel: WynnChatChannels.Channel): Int {
        if (channel.color == ChatFormatting.WHITE) return OwTheme.ACCENT
        val rgb = Style.EMPTY.withColor(channel.color).color?.value ?: return OwTheme.ACCENT
        return 0xFF000000.toInt() or rgb
    }

    private fun badgeText(count: Int): String = if (count > 99) "99+" else count.toString()

    private fun width(font: Font, tab: Tab): Int =
        PAD * 2 + font.width(tab.label) + if (tab.badge > 0) GAP + font.width(badgeText(tab.badge)) else 0

    private fun drawTab(g: GuiGraphicsExtractor, font: Font, tab: Tab, x: Int, y: Int, w: Int, hovered: Boolean) {
        if (tab.active) {
            g.fillGradient(x, y, x + w, y + HEIGHT, HudStyle.alpha(tab.accent, 0.1f), HudStyle.alpha(tab.accent, 0.34f))
            g.fill(x, y, x + w, y + 1, OwShape.HIGHLIGHT)
        } else if (hovered) {
            g.fillGradient(x, y, x + w, y + HEIGHT, 0x00FFFFFF, HOVER_WASH)
        }
        if (tab.active) g.fill(x, y + HEIGHT - 2, x + w, y + HEIGHT, tab.accent)
        val color = if (tab.active) HudStyle.lighten(tab.accent, 0.25f) else if (hovered) OwTheme.TEXT else OwTheme.TEXT_DIM
        val textY = y + (HEIGHT - 8) / 2
        g.text(font, tab.label, x + PAD, textY, color, true)
        if (tab.badge > 0) g.text(font, badgeText(tab.badge), x + PAD + font.width(tab.label) + GAP, textY, OwTheme.GOOD, true)
    }

    private fun drawGroup(g: GuiGraphicsExtractor, font: Font, tabs: List<Tab>, startX: Int, y: Int, mouseX: Int, mouseY: Int): Int {
        val widths = tabs.map { width(font, it) }
        val total = widths.sum()
        OwShape.shadow(g, startX, y, total, HEIGHT, 1, 2)
        OwShape.gradient(g, startX, y, total, HEIGHT, 1, HudStyle.lighten(HudStyle.GLASS, 0.08f), HudStyle.darken(HudStyle.GLASS, 0.2f))
        OwShape.frame(g, startX, y, total, HEIGHT, 1, HudStyle.TRACK_EDGE)
        var x = startX
        for ((index, tab) in tabs.withIndex()) {
            val w = widths[index]
            val hovered = mouseX in x until x + w && mouseY in y until y + HEIGHT
            drawTab(g, font, tab, x, y, w, hovered)
            hits.add(Hit(x, x + w, tab.action))
            x += w
        }
        return x
    }

    fun render(g: GuiGraphicsExtractor, font: Font, input: EditBox, mouseX: Int, mouseY: Int, guiW: Int, guiH: Int) {
        hits.clear()
        barY = top(input)
        val right = min(input.x + input.width, ChatHud.chatBounds(guiW, guiH)[2])
        val direct = WynnChatChannels.directTarget
        val current = WynnChatChannels.current

        val channelTabs = WynnChatChannels.Channel.entries.map { channel ->
            val active = direct == null && current == channel
            val unread = if (active || channel == WynnChatChannels.Channel.ALL) 0 else ChatActivity.unread(channel)
            Tab(channel.label, active, accentOf(channel), unread) {
                WynnChatChannels.select(channel)
                WynnDirectMessages.applyView(true)
            }
        }
        var x = drawGroup(g, font, channelTabs, input.x, barY, mouseX, mouseY) + GAP

        val sys = Tab("SYS", ChatSysPanel.open, OwTheme.ACCENT, if (ChatSysPanel.open) 0 else ChatSysPanel.unread) { ChatSysPanel.toggle() }
        val sysW = width(font, sys)
        val sysX = right - sysW
        val limit = sysX - GAP

        val at = Tab("@", false, OwTheme.GOOD, 0) {
            input.value = "@"
            input.setFocused(true)
        }
        val atW = width(font, at)
        if (x + atW <= limit) x = drawGroup(g, font, listOf(at), x, barY, mouseX, mouseY) + GAP

        val names = ArrayList<String>()
        if (direct != null) names.add(direct)
        for (convo in WynnDirectMessages.recent()) {
            if (names.none { it.equals(convo.name, ignoreCase = true) }) names.add(convo.name)
            if (names.size >= MAX_CHIPS) break
        }
        for (name in names) {
            val unread = WynnDirectMessages.recent().firstOrNull { it.name.equals(name, ignoreCase = true) }?.unread ?: 0
            val active = direct != null && direct.equals(name, ignoreCase = true)
            val label = trim(font, name)
            val chip = Tab(label, active, OwTheme.GOOD, if (active) 0 else unread) {
                WynnChatChannels.selectDirect(name, false)
            }
            if (x + width(font, chip) > limit) break
            x = drawGroup(g, font, listOf(chip), x, barY, mouseX, mouseY) + GAP
        }

        if (sysX >= input.x) drawGroup(g, font, listOf(sys), sysX, barY, mouseX, mouseY)
    }

    private fun trim(font: Font, name: String): String {
        if (font.width(name) <= CHIP_TEXT_MAX) return name
        var out = name
        while (out.length > 1 && font.width("$out...") > CHIP_TEXT_MAX) out = out.dropLast(1)
        return "$out..."
    }

    fun mouseClicked(mx: Double, my: Double): Boolean {
        if (my < barY || my >= barY + HEIGHT) return false
        val hit = hits.firstOrNull { mx >= it.x0 && mx < it.x1 } ?: return false
        hit.action()
        return true
    }
}
