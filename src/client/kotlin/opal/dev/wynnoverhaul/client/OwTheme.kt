package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor

object OwTheme {
    const val BG_DIM = 0x50120D08.toInt()
    const val PANEL = 0xF01C140D.toInt()
    const val PANEL_ALT = 0xF0241A10.toInt()
    const val PANEL_RAISED = 0xF02E2013.toInt()
    const val BORDER_BRIGHT = 0xFFC9A227.toInt()
    const val ACCENT = 0xFFD4AF37.toInt()
    const val ACCENT_DIM = 0xFF7A5F1E.toInt()
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


    const val ROW_H = 22
    const val PAD = 12
    const val GAP = 6
    const val TITLE_BAR_H = 28
    const val SCROLLBAR_W = 6
    const val SCROLL_RATE = 30

    fun hudPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, alpha: Int = 255) {
        if (!WynnOverhaulConfig.current.hudPanelsEnabled) return
        HudStyle.plate(graphics, x, y, w, h, ACCENT, alpha / 255f)
    }

    fun drawPage(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        OwShape.panel(graphics, x, y, w, h)
    }

    fun glassPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        OwShape.panel(graphics, x, y, w, h, glass = true, cut = 3)
    }
}
