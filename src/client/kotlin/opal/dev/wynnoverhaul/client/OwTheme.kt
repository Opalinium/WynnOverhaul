package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object OwTheme {
    const val BG_DIM = 0x50120D08.toInt()
    const val PANEL = 0xF01C140D.toInt()
    const val PANEL_ALT = 0xF0241A10.toInt()
    const val PANEL_RAISED = 0xF02E2013.toInt()
    const val BORDER = 0xFF4A3117.toInt()
    const val BORDER_BRIGHT = 0xFFC9A227.toInt()
    const val ACCENT = 0xFFD4AF37.toInt()
    const val ACCENT_DIM = 0xFF7A5F1E.toInt()
    const val ACCENT_FAINT = 0xFF33270F.toInt()
    const val TEXT = 0xFFEDE0C8.toInt()
    const val TEXT_DIM = 0xFFA6947A.toInt()
    const val TEXT_FAINT = 0xFF6B5D48.toInt()
    const val GOOD = 0xFF8FBF5C.toInt()
    const val WARN = 0xFFD98E3D.toInt()
    const val BAD = 0xFFB6472F.toInt()

    const val PAGE_WASH = 0xF00E0C0A.toInt()
    const val HAIRLINE = 0xFF5A4A28.toInt()
    const val TILE_BG = 0xF0161412.toInt()
    const val TILE_HOVER = 0xF0221D17.toInt()
    const val TILE_BORDER = 0xFF3A2E1C.toInt()
    const val TILE_NEW = 0xFF6FAE5E.toInt()

    const val ROW_H = 22
    const val PAD = 12
    const val GAP = 6
    const val TITLE_BAR_H = 28
    const val SCROLLBAR_W = 6
    const val SCROLL_RATE = 30

    val BUTTON_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/button")
    val BUTTON_HOVER_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/button_highlighted")
    val BUTTON_DISABLED_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/button_disabled")
    val CHECKBOX_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/checkbox")
    val CHECKBOX_HOVER_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/checkbox_highlighted")
    val CHECKBOX_SELECTED_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/checkbox_selected")
    val CHECKBOX_SELECTED_HOVER_SPRITE: Identifier = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted")

    const val SPRITE_TINT = 0xFFD8B979.toInt()
    const val SPRITE_TINT_DISABLED = 0xFF6B6154.toInt()

    fun drawTintedSprite(graphics: GuiGraphicsExtractor, sprite: Identifier, x: Int, y: Int, w: Int, h: Int, tint: Int = SPRITE_TINT) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, tint)
    }

    fun hudPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, alpha: Int = 255) {
        if (!WynnOverhaulConfig.current.hudPanelsEnabled) return
        HudStyle.plate(graphics, x, y, w, h, ACCENT, alpha / 255f)
    }

    fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, fill: Int = PANEL) {
        graphics.fill(x, y, x + w, y + h, fill)
        graphics.outline(x - 2, y - 2, w + 4, h + 4, BORDER)
        graphics.outline(x, y, w, h, BORDER_BRIGHT)
        drawRivet(graphics, x + 1, y + 1)
        drawRivet(graphics, x + w - 3, y + 1)
        drawRivet(graphics, x + 1, y + h - 3)
        drawRivet(graphics, x + w - 3, y + h - 3)
    }

    fun drawPage(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, PAGE_WASH)
        graphics.fill(x, y, x + w, y + 1, HAIRLINE)
        graphics.fill(x, y + h - 1, x + w, y + h, HAIRLINE)
        graphics.fill(x, y, x + 1, y + h, HAIRLINE)
        graphics.fill(x + w - 1, y, x + w, y + h, HAIRLINE)
    }

    fun drawSectionHeader(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, x: Int, y: Int, w: Int, title: String, right: String = "") {
        graphics.text(font, title.uppercase(), x, y, ACCENT)
        if (right.isNotEmpty()) {
            graphics.text(font, right, x + w - font.width(right), y, TEXT_DIM)
        }
        graphics.fill(x, y + 12, x + w, y + 13, HAIRLINE)
    }

    fun drawTile(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, borderArgb: Int, isNew: Boolean = false) {
        graphics.fill(x, y, x + size, y + size, TILE_BG)
        graphics.fill(x, y, x + size, y + 1, borderArgb)
        graphics.fill(x, y + size - 1, x + size, y + size, borderArgb)
        graphics.fill(x, y, x + 1, y + size, borderArgb)
        graphics.fill(x + size - 1, y, x + size, y + size, borderArgb)
        if (isNew) {
            graphics.fill(x - 1, y - 1, x + size + 1, y, TILE_NEW)
            graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, TILE_NEW)
            graphics.fill(x - 1, y, x, y + size, TILE_NEW)
            graphics.fill(x + size, y, x + size + 1, y + size, TILE_NEW)
        }
    }

    private fun drawRivet(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        graphics.fill(x, y, x + 2, y + 2, BORDER_BRIGHT)
    }
}
