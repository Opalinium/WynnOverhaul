package opal.dev.overwatch.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OverwatchWeaponAnimationScreen(parent: Screen? = null) : OwScreen(Component.literal("Weapon Animations"), parent) {
    override val panelWidth: Int get() = (width * 0.36).toInt().coerceIn(320, 460).coerceAtMost((width - 20).coerceAtLeast(200))
    override val panelLeft: Int get() = (width - panelWidth - 12).coerceAtLeast(0)
    override val panelHeight: Int get() = (height * 0.85).toInt().coerceIn(280, 520)

    override fun init() {
        super.init()
        val left = contentLeft
        val w = contentWidth
        val rows = ArrayList<Pair<AbstractWidget, Int>>()

        rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("Add held weapon")) { addHeld() } to OwTheme.ROW_H
        rows += OwSectionHeader(left, 0, w, "Registered models") to 18
        rows += OwLabel(left, 0, w, 12, "Click: next animation   Right-click: previous   X: remove", OwTheme.TEXT_DIM) to 14

        val entries = WeaponAnimationRegistry.entries()
        if (entries.isEmpty()) {
            rows += OwLabel(left, 0, w, 14, "Nothing registered yet. Hold a weapon and press Add, or bind the keybind in Controls.", OwTheme.TEXT_FAINT) to 18
        }
        for (entry in entries) {
            rows += EntryRow(left, 0, w, ROW_H, entry) { rebuildWidgets() } to ROW_H + ROW_GAP
        }

        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    private fun addHeld() {
        val player = Minecraft.getInstance().player ?: return
        val stack = ActiveWeapon.stack(player) ?: player.mainHandItem
        if (WeaponAnimationRegistry.register(stack) == null) {
            player.sendSystemMessage(Component.literal("[Overwatch] Hold a weapon first.").withStyle(ChatFormatting.RED))
            return
        }
        rebuildWidgets()
    }

    private class EntryRow(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val entry: WeaponAnimationEntry,
        private val onRemoved: () -> Unit,
    ) : AbstractWidget(x, y, width, height, Component.literal(entry.name)) {
        private val preview = WeaponAnimationRegistry.preview(entry)

        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val font = Minecraft.getInstance().font
            val hovered = isHovered
            val border = if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE
            graphics.fill(x, y, x + width, y + height, if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG)
            graphics.fill(x, y, x + width, y + 1, border)
            graphics.fill(x, y + height - 1, x + width, y + height, border)
            graphics.fill(x, y, x + 1, y + height, border)
            graphics.fill(x + width - 1, y, x + width, y + height, border)

            graphics.fill(x + PREVIEW_PAD, y + PREVIEW_PAD, x + PREVIEW_PAD + PREVIEW_BOX, y + PREVIEW_PAD + PREVIEW_BOX, OwTheme.PANEL)
            graphics.pose().pushMatrix()
            graphics.pose().translate((x + PREVIEW_PAD + PREVIEW_BOX / 2).toFloat(), (y + height / 2).toFloat())
            graphics.pose().scale(ICON_SCALE)
            graphics.item(preview, -8, -8)
            graphics.pose().popMatrix()

            val textX = x + PREVIEW_PAD * 2 + PREVIEW_BOX
            val textW = width - (textX - x) - REMOVE_W - 6
            graphics.text(font, truncateToWidth(font, entry.name, textW), textX, y + 7, OwTheme.TEXT)
            val style = entry.style
            val label = if (style == WeaponAnimationRegistry.AUTO) {
                val auto = if (entry.autoStyle.isEmpty()) "none detected" else WeaponAnimations.styleLabel(entry.autoStyle)
                "Auto ($auto)"
            } else {
                WeaponAnimations.styleLabel(style)
            }
            val labelColor = if (style == WeaponAnimationRegistry.AUTO) OwTheme.TEXT_DIM else OwTheme.ACCENT
            graphics.text(font, truncateToWidth(font, label, textW), textX, y + 19, labelColor)
            graphics.text(font, truncateToWidth(font, WeaponAnimationRegistry.modelSummary(entry), textW), textX, y + 31, OwTheme.TEXT_FAINT)

            val removeLeft = x + width - REMOVE_W
            val removeHot = mouseX >= removeLeft && mouseX < x + width && mouseY >= y && mouseY < y + height
            graphics.fill(removeLeft, y + 1, x + width - 1, y + height - 1, if (removeHot) OwTheme.BAD else OwTheme.PANEL_ALT)
            graphics.centeredText(font, "X", removeLeft + REMOVE_W / 2, y + (height - 8) / 2, OwTheme.TEXT)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (!active || !visible || !isMouseOver(event.x(), event.y())) return false
            if (event.x() >= x + width - REMOVE_W) {
                WeaponAnimationRegistry.remove(entry)
                onRemoved()
            } else {
                WeaponAnimationRegistry.cycle(entry, if (event.button() == 1) -1 else 1)
            }
            return true
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }

    private companion object {
        const val ROW_H = 44
        const val ROW_GAP = 4
        const val PREVIEW_PAD = 5
        const val PREVIEW_BOX = 34
        const val ICON_SCALE = 1.9f
        const val REMOVE_W = 22
    }
}
