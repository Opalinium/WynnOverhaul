package opal.dev.overwatch.client

import net.minecraft.client.gui.components.AbstractWidget

class OwPanelList(private val addWidget: (AbstractWidget) -> Unit) {
    private var scrollPanel: OwScrollPanel? = null

    fun install(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, width: Int, height: Int) {
        val panel = OwScrollPanel(x, y, width, height)
        scrollPanel = panel
        panel.totalContentHeight = rows.sumOf { it.second }
        val reserved = OwTheme.SCROLLBAR_W + OwTheme.GAP
        for ((widget, _) in rows) {
            val rightEdge = widget.x + widget.width
            if (rightEdge > x + width - reserved) widget.width = (x + width - reserved - widget.x).coerceAtLeast(1)
        }
        panel.onReposition = {
            val offset = panel.scrollAmount().toInt()
            var cy = y
            for ((widget, rowHeight) in rows) {
                val wy = cy - offset
                widget.y = wy
                widget.visible = wy + widget.height > y && wy < y + height
                cy += rowHeight
            }
        }
        addWidget(panel)
        rows.forEach { addWidget(it.first) }
    }

    fun handleMouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val panel = scrollPanel
        if (panel != null && panel.visible && mouseX >= panel.x && mouseX < panel.x + panel.width && mouseY >= panel.y && mouseY < panel.y + panel.height) {
            return panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        return false
    }
}
