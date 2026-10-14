package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor

interface ModifiedMark {
    var modified: Boolean
}

fun drawModifiedMark(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
    HudStyle.diamond(graphics, x + width - 7, y + height / 2, 2, OwTheme.ACCENT)
}
