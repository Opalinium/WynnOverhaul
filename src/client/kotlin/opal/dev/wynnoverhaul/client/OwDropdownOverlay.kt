package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object OwDropdownOverlay {
    class Option(val id: String, val label: String)

    private var open = false
    private var anchorX = 0
    private var anchorY = 0
    private var anchorW = 0
    private var anchorH = 0
    private var options: List<Option> = emptyList()
    private var selectedId = ""
    private var stillValid: () -> Boolean = { true }
    private var onSelect: (String) -> Unit = {}
    private var scroll = 0

    val isOpen: Boolean get() = open

    fun open(x: Int, y: Int, w: Int, h: Int, options: List<Option>, selectedId: String, valid: () -> Boolean = { true }, onSelect: (String) -> Unit) {
        this.anchorX = x
        this.anchorY = y
        this.anchorW = w
        this.anchorH = h
        this.options = options
        this.selectedId = selectedId
        this.stillValid = valid
        this.onSelect = onSelect
        this.scroll = 0
        this.open = true
    }

    fun close() {
        open = false
        options = emptyList()
    }

    private fun visibleRows(): Int = options.size.coerceAtMost(MAX_ROWS)

    private fun listWidth(): Int {
        val font = Minecraft.getInstance().font
        val widest = options.maxOfOrNull { font.width(it.label) } ?: 0
        return maxOf(anchorW, widest + PAD * 2 + SCROLL_GUTTER)
    }

    private fun listHeight(): Int = visibleRows() * ROW_H + 2

    private fun listY(screenH: Int): Int {
        val below = anchorY + anchorH
        return if (below + listHeight() <= screenH || anchorY - listHeight() < 0) below else anchorY - listHeight()
    }

    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screenH: Int) {
        if (!open) return
        if (!stillValid()) {
            close()
            return
        }
        val font = Minecraft.getInstance().font
        val w = listWidth()
        val h = listHeight()
        val y0 = listY(screenH)
        graphics.fill(anchorX, y0, anchorX + w, y0 + h, LIST_BG)
        graphics.outline(anchorX, y0, w, h, OwTheme.BORDER_BRIGHT)
        val rows = visibleRows()
        for (row in 0 until rows) {
            val index = row + scroll
            if (index >= options.size) break
            val option = options[index]
            val ry = y0 + 1 + row * ROW_H
            val hovered = mouseX >= anchorX && mouseX < anchorX + w - SCROLL_GUTTER && mouseY >= ry && mouseY < ry + ROW_H
            if (hovered) graphics.fill(anchorX + 1, ry, anchorX + w - 1, ry + ROW_H, OwTheme.TILE_HOVER)
            val selected = option.id == selectedId
            graphics.text(font, truncateToWidth(font, option.label, w - PAD * 2 - SCROLL_GUTTER), anchorX + PAD, ry + (ROW_H - 8) / 2, if (selected) OwTheme.ACCENT else OwTheme.TEXT)
        }
        if (options.size > rows) {
            val trackX = anchorX + w - 4
            val trackH = h - 2
            val thumbH = (trackH * rows / options.size).coerceAtLeast(6)
            val maxScroll = options.size - rows
            val thumbY = y0 + 1 + (trackH - thumbH) * scroll / maxScroll
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, OwTheme.BORDER_BRIGHT)
        }
    }

    fun covers(mouseX: Int, mouseY: Int, screenH: Int): Boolean {
        if (!open) return false
        val w = listWidth()
        val y0 = listY(screenH)
        return mouseX >= anchorX && mouseX < anchorX + w && mouseY >= y0 && mouseY < y0 + listHeight()
    }

    const val HIDDEN_MOUSE = -10000

    fun mouseClicked(mouseX: Int, mouseY: Int, screenH: Int): Boolean {
        if (!open) return false
        val w = listWidth()
        val y0 = listY(screenH)
        val inside = mouseX >= anchorX && mouseX < anchorX + w && mouseY >= y0 && mouseY < y0 + listHeight()
        if (inside) {
            val row = (mouseY - y0 - 1) / ROW_H
            val index = row + scroll
            if (row >= 0 && row < visibleRows() && index in options.indices) {
                val chosen = options[index].id
                val callback = onSelect
                close()
                callback(chosen)
            }
            return true
        }
        close()
        return true
    }

    fun mouseScrolled(mouseX: Int, mouseY: Int, scrollY: Double, screenH: Int): Boolean {
        if (!open) return false
        val w = listWidth()
        val y0 = listY(screenH)
        val inside = mouseX >= anchorX && mouseX < anchorX + w && mouseY >= y0 && mouseY < y0 + listHeight()
        if (!inside) {
            close()
            return false
        }
        val maxScroll = (options.size - visibleRows()).coerceAtLeast(0)
        scroll = (scroll - scrollY.toInt().coerceIn(-1, 1)).coerceIn(0, maxScroll)
        return true
    }

    private const val ROW_H = 16
    private const val MAX_ROWS = 8
    private const val PAD = 6
    private const val SCROLL_GUTTER = 6
    private const val LIST_BG = 0xFF14100A.toInt()
}
