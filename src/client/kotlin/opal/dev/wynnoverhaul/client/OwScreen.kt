package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

private const val KEY_ESCAPE = 256

abstract class OwScreen(
    title: Component,
    protected val parent: Screen?,
) : Screen(title) {
    protected open val panelWidth: Int get() = (width * 0.7).toInt().coerceIn(320, 520)
    protected open val panelHeight: Int get() = (height * 0.82).toInt().coerceIn(240, 420)
    protected open val panelLeft: Int get() = (width - panelWidth) / 2
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
        OwDropdownOverlay.close()
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
        OwTheme.drawPage(graphics, left, top, panelWidth, panelHeight)
        graphics.fill(left, top + OwTheme.TITLE_BAR_H - 1, left + panelWidth, top + OwTheme.TITLE_BAR_H, OwTheme.HAIRLINE)
        graphics.centeredText(Minecraft.getInstance().font, title.string.uppercase(), left + panelWidth / 2, top + (OwTheme.TITLE_BAR_H - 8) / 2, OwTheme.TEXT)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val covered = OwDropdownOverlay.covers(mouseX, mouseY, height)
        val mx = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseX
        val my = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseY
        super.extractRenderState(graphics, mx, my, partialTick)
        OwDropdownOverlay.render(graphics, mouseX, mouseY, height)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (OwDropdownOverlay.mouseClicked(event.x().toInt(), event.y().toInt(), height)) return true
        return super.mouseClicked(event, doubled)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (OwDropdownOverlay.isOpen && event.key() == KEY_ESCAPE) {
            OwDropdownOverlay.close()
            return true
        }
        if (isTextInputFocused() && event.key() == KEY_ESCAPE) {
            releaseTextInputFocus()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        val p = parent
        if (p != null) Minecraft.getInstance().setScreenAndShow(p) else Minecraft.getInstance().gui.setScreen(null)
    }

    private val panelList = OwPanelList({ addRenderableWidget(it) })

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (OwDropdownOverlay.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY, height)) return true
        if (panelList.handleMouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    protected fun installScrollList(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, width: Int, height: Int) {
        panelList.install(rows, x, y, width, height)
    }
}
