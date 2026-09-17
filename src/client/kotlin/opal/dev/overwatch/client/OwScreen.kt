package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class OwScreen(
    title: Component,
    protected val parent: Screen?,
) : Screen(title) {

    protected open val panelWidth: Int get() = (width * 0.7).toInt().coerceIn(320, 520)
    protected open val panelHeight: Int get() = (height * 0.82).toInt().coerceIn(240, 420)
    protected val panelLeft: Int get() = (width - panelWidth) / 2
    protected val panelTop: Int get() = (height - panelHeight) / 2

    protected val contentLeft: Int get() = panelLeft + OwTheme.PAD
    protected val contentTop: Int get() = panelTop + OwTheme.TITLE_BAR_H + OwTheme.PAD
    protected val contentWidth: Int get() = panelWidth - OwTheme.PAD * 2
    protected val contentBottom: Int get() = panelTop + panelHeight - OwTheme.PAD

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun init() {
        addRenderableOnly(Renderable { graphics, _, _, _ -> drawChrome(graphics) })
        if (parent != null) {
            addRenderableWidget(
                OwButton(panelLeft + OwTheme.PAD, panelTop + 6, 52, 16, Component.literal("< Back"), onPress = { onClose() }),
            )
        }
    }

    private fun drawChrome(graphics: GuiGraphicsExtractor) {
        val left = panelLeft
        val top = panelTop
        graphics.fill(left, top, left + panelWidth, top + panelHeight, OwTheme.PANEL)
        graphics.fill(left, top, left + panelWidth, top + OwTheme.TITLE_BAR_H, OwTheme.PANEL_RAISED)
        graphics.fill(left, top + OwTheme.TITLE_BAR_H - 1, left + panelWidth, top + OwTheme.TITLE_BAR_H, OwTheme.ACCENT)
        graphics.outline(left, top, panelWidth, panelHeight, OwTheme.BORDER)
        graphics.centeredText(Minecraft.getInstance().font, title, left + panelWidth / 2, top + (OwTheme.TITLE_BAR_H - 8) / 2, OwTheme.TEXT)
    }

    override fun onClose() {
        val p = parent
        if (p != null) Minecraft.getInstance().setScreenAndShow(p) else Minecraft.getInstance().gui.setScreen(null)
    }

    protected fun installScrollList(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, width: Int, height: Int) {
        val panel = OwScrollPanel(x, y, width, height)
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
        addRenderableWidget(panel)
        rows.forEach { addRenderableWidget(it.first) }
    }
}
