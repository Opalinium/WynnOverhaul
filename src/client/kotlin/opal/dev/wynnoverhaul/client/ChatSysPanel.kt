package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

object ChatSysPanel {
    private class Entry(val text: Component, val notification: Boolean) {
        var lines: List<FormattedCharSequence> = emptyList()
        var wrapWidth = -1
    }

    private const val WIDTH = 210
    private const val HEIGHT = 170
    private const val HEADER_H = 14
    private const val LINE_H = 9
    private const val PAD = 5
    private const val GAP = 6
    private const val SCREEN_MARGIN = 4
    private const val SCROLL_STEP = 3
    private const val SCROLLBAR_W = 2
    private const val WHITE = -1

    private val entries = ArrayDeque<Entry>()

    var open = false
        private set

    var unread = 0
        private set

    private var scroll = 0
    private var left = 0
    private var top = 0
    private var closeX0 = 0
    private var clearX0 = 0
    private var clearX1 = 0

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onServerMessage(message) }
    }

    private fun onServerMessage(message: Component) {
        if (!WynnOverhaulGate.onWynncraft || message.string.isBlank()) return
        if (ChatKinds.isSystemLog(ChatKinds.classify(message))) add(message, false)
    }

    fun add(text: Component, notification: Boolean) {
        entries.addLast(Entry(text, notification))
        val limit = WynnOverhaulConfig.current.chatHistoryLimit
        while (entries.size > limit) entries.removeFirst()
        if (!open) unread++
    }

    fun clear() {
        entries.clear()
        unread = 0
        scroll = 0
    }

    fun toggle() {
        open = !open
        scroll = 0
        if (open) unread = 0
    }

    fun close() {
        open = false
    }

    private fun wrap(font: Font, entry: Entry, width: Int): List<FormattedCharSequence> {
        if (entry.wrapWidth != width) {
            entry.lines = font.split(entry.text, width)
            entry.wrapWidth = width
        }
        return entry.lines
    }

    private fun place(guiW: Int, guiH: Int, barTop: Int) {
        val chat = ChatHud.chatBounds(guiW, guiH)
        var x = chat[2] + GAP
        if (x + WIDTH > guiW - SCREEN_MARGIN) x = chat[0] - GAP - WIDTH
        if (x < SCREEN_MARGIN) x = guiW - SCREEN_MARGIN - WIDTH
        left = x
        top = (barTop + ChatTabBar.HEIGHT - HEIGHT).coerceAtLeast(SCREEN_MARGIN)
    }

    private fun inside(mx: Double, my: Double): Boolean = mx >= left && mx < left + WIDTH && my >= top && my < top + HEIGHT

    fun render(g: GuiGraphicsExtractor, font: Font, barTop: Int, guiW: Int, guiH: Int, mouseX: Int, mouseY: Int) {
        if (!open) return
        place(guiW, guiH, barTop)
        HudStyle.plate(g, left, top, WIDTH, HEIGHT, OwTheme.ACCENT)
        val title = "SYSTEM"
        g.text(font, title, left + PAD + 2, top + 4, OwTheme.ACCENT, true)
        val count = entries.size.toString()
        g.text(font, count, left + PAD + 6 + font.width(title), top + 4, OwTheme.TEXT_FAINT, true)

        closeX0 = left + WIDTH - PAD - 9
        val clearLabel = "clear"
        clearX1 = closeX0 - 4
        clearX0 = clearX1 - font.width(clearLabel) - 6
        val rowTop = top + 2
        val rowBottom = top + HEADER_H
        val closeHot = mouseX in closeX0 until closeX0 + 9 && mouseY in rowTop until rowBottom
        val clearHot = mouseX in clearX0 until clearX1 && mouseY in rowTop until rowBottom
        if (clearHot) g.fill(clearX0, rowTop, clearX1, rowBottom, HudStyle.alpha(OwTheme.ACCENT, 0.18f))
        if (closeHot) g.fill(closeX0, rowTop, closeX0 + 9, rowBottom, HudStyle.alpha(OwTheme.BAD, 0.3f))
        g.text(font, clearLabel, clearX0 + 3, top + 4, if (clearHot) OwTheme.TEXT else OwTheme.TEXT_DIM, true)
        g.text(font, "x", closeX0 + 3, top + 4, if (closeHot) OwTheme.TEXT else OwTheme.TEXT_DIM, true)
        g.fill(left + PAD, top + HEADER_H, left + WIDTH - PAD, top + HEADER_H + 1, OwTheme.HAIRLINE)

        val areaTop = top + HEADER_H + 3
        val areaBottom = top + HEIGHT - PAD
        val textLeft = left + PAD + 3
        val textWidth = WIDTH - PAD * 2 - 3 - SCROLLBAR_W - 3
        val rows = (areaBottom - areaTop) / LINE_H

        val collected = ArrayList<Pair<FormattedCharSequence, Boolean>>()
        val wanted = rows + scroll + SCROLL_STEP
        var i = entries.size - 1
        while (i >= 0 && collected.size < wanted) {
            val entry = entries[i]
            val lines = wrap(font, entry, textWidth)
            var j = lines.size - 1
            while (j >= 0 && collected.size < wanted) {
                collected.add(lines[j] to entry.notification)
                j--
            }
            i--
        }
        val maxScroll = (collected.size - rows).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)

        if (collected.isEmpty()) {
            g.centeredText(font, "Nothing yet", left + WIDTH / 2, top + HEIGHT / 2 - 4, OwTheme.TEXT_FAINT)
            return
        }
        g.enableScissor(left + PAD, areaTop, left + WIDTH - PAD, areaBottom)
        val visible = collected.drop(scroll).take(rows)
        for ((k, line) in visible.withIndex()) {
            val y = areaBottom - (k + 1) * LINE_H
            if (line.second) g.fill(left + PAD, y, left + PAD + 1, y + LINE_H - 1, OwTheme.ACCENT_DIM)
            g.text(font, line.first, textLeft, y, WHITE, true)
        }
        g.disableScissor()

        if (maxScroll > 0) {
            val trackX = left + WIDTH - PAD - SCROLLBAR_W
            val trackH = areaBottom - areaTop
            val thumbH = (trackH * rows / (rows + maxScroll)).coerceAtLeast(8)
            val thumbTop = areaBottom - thumbH - ((trackH - thumbH) * scroll / maxScroll)
            g.fill(trackX, areaTop, trackX + SCROLLBAR_W, areaBottom, HudStyle.TRACK_EDGE)
            g.fill(trackX, thumbTop, trackX + SCROLLBAR_W, thumbTop + thumbH, OwTheme.ACCENT_DIM)
        }
    }

    fun mouseClicked(mx: Double, my: Double): Boolean {
        if (!open || !inside(mx, my)) return false
        if (my < top + HEADER_H) {
            if (mx >= closeX0 && mx < closeX0 + 9) close()
            else if (mx >= clearX0 && mx < clearX1) clear()
        }
        return true
    }

    fun mouseScrolled(mx: Double, my: Double, deltaY: Double): Boolean {
        if (!open || !inside(mx, my)) return false
        if (deltaY > 0) scroll += SCROLL_STEP else if (deltaY < 0) scroll = (scroll - SCROLL_STEP).coerceAtLeast(0)
        return true
    }
}
