package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.CAPTION_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.FOOTER_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.MARGIN
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.OUTSIDE_SLOT
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_STEP

class WynnOverhaulUpgraderScreen(menu: AbstractContainerMenu) :
    WynnOverhaulSideScreen(menu, "Item Upgrader", menu.slots.size - PLAYER_SLOTS) {
    private class Option(val slot: Int, val stack: ItemStack, val name: String, val rgb: Int, val lines: List<String>, val enabled: Boolean)

    private class Card(val rect: Rect, val option: Option)

    private val chestSlots = menu.slots.size - PLAYER_SLOTS
    private var cards: List<Card> = emptyList()
    private var invLayout: InventoryLayout? = null
    private var panelTop = 0
    private var panelH = 0

    override fun sidePanelW(): Int = CARD_COLS * TILE_STEP + 2 * MARGIN

    private fun options(): List<Option> {
        val out = ArrayList<Option>()
        for (slot in 0 until chestSlots) {
            val stack = stackAt(slot)
            if (stack.isEmpty) continue
            val name = plainName(stack)
            if (name.isBlank()) continue
            val lore = WynnItemRarity.loreLines(stack).filter { it.isNotBlank() }
            val clickable = lore.any { it.contains("Click to", ignoreCase = true) }
            val body = lore.filter { !it.contains("Click to", ignoreCase = true) }
            val rgb = stack.hoverName.style.color?.value ?: OwTheme.TEXT
            out.add(Option(slot, stack, name, rgb, body, clickable))
        }
        return out
    }

    private fun cardHeight(option: Option, textW: Int): Int {
        if (!option.enabled) return DISABLED_H
        val lines = HudStyle.wrap(font, option.lines.joinToString(" "), textW).size
        return maxOf(CARD_MIN_H, NAME_H + lines * LINE_H + CARD_PAD * 2)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val all = options()
        val active = all.filter { it.enabled }
        val idle = all.filter { !it.enabled }
        val invOrder = inventoryOrder()
        val invX = invPanelX()
        val sideX = sidePanelX()
        val invW = invPanelW()
        val sideW = sidePanelW()
        val gridX = sideX + MARGIN
        val inner = sideW - MARGIN * 2
        val textW = inner - TILE - CARD_PAD * 3

        val activeH = active.sumOf { cardHeight(it, textW) + CARD_GAP }
        val idleRows = (idle.size + 1) / 2
        val idleH = if (idle.isEmpty()) 0 else CAPTION_H + idleRows * (DISABLED_H + CARD_GAP)
        val tail = GAP + FOOTER_H + 4
        val fullH = height - MARGIN * 2 + 8
        val leftIdeal = LEFT_TOP + inventoryNaturalH(invOrder) + tail
        val rightIdeal = RIGHT_TOP + maxOf(activeH + idleH, MIN_BODY_H) + tail
        val h = minOf(fullH, maxOf(leftIdeal, rightIdeal))
        val top = ((height - h) / 2).coerceAtLeast(MARGIN - 4)
        panelTop = top
        panelH = h

        panel(graphics, invX - 4, top, invW + 8, h)
        panel(graphics, sideX - 4, top, sideW + 8, h)
        graphics.text(font, "Item Upgrader", gridX, top + TITLE_Y, OwTheme.TEXT)

        val placed = ArrayList<Card>()
        var y = top + RIGHT_TOP
        for (option in active) {
            val ch = cardHeight(option, textW)
            val rect = Rect(gridX, y, inner, ch)
            placed.add(Card(rect, option))
            val hovered = rect.contains(mouseX, mouseY)
            drawCard(graphics, rect, option, hovered, textW)
            y += ch + CARD_GAP
        }
        if (idle.isNotEmpty()) {
            graphics.text(font, "Coming later", gridX, y + 2, OwTheme.TEXT_DIM)
            y += CAPTION_H
            val halfW = (inner - CARD_GAP) / 2
            for ((i, option) in idle.withIndex()) {
                val rect = Rect(gridX + (i % 2) * (halfW + CARD_GAP), y + (i / 2) * (DISABLED_H + CARD_GAP), halfW, DISABLED_H)
                placed.add(Card(rect, option))
                drawCard(graphics, rect, option, false, textW)
            }
        }
        if (all.isEmpty()) OwSkeleton.bars(graphics, gridX, top + RIGHT_TOP, inner, 3, CARD_MIN_H, CARD_GAP)
        cards = placed

        val layout = layoutInventory(top + LEFT_TOP, invX + MARGIN, invOrder)
        invLayout = layout
        val hoverHotbar = layout.hotbar.firstOrNull { it.slot >= 0 && it.rect.contains(mouseX, mouseY) }
        val hoverInv = layout.inventory.firstOrNull { it.rect.contains(mouseX, mouseY) }
        drawInventory(graphics, layout, invX + MARGIN, hoverHotbar, hoverInv)
        drawInventoryFooter(graphics, invX, invW, footerY(top, h))
        val hoverDock = drawDock(graphics, mouseX, mouseY)
        drawCarried(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (menu.carried.isEmpty) {
            val stack = when {
                hoverHotbar != null -> stackAt(hoverHotbar.slot)
                hoverInv != null -> stackAt(hoverInv.slot)
                hoverDock != null -> hoverDock
                else -> ItemStack.EMPTY
            }
            if (!stack.isEmpty) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
    }

    private fun drawCard(graphics: GuiGraphicsExtractor, rect: Rect, option: Option, hovered: Boolean, textW: Int) {
        val active = option.enabled
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, if (hovered && active) OwTheme.TILE_HOVER else TILE_BG)
        graphics.outline(rect.x, rect.y, rect.w, rect.h, if (hovered && active) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        val iconY = rect.y + if (active) CARD_PAD else (rect.h - TILE) / 2
        val iconRect = Rect(rect.x + CARD_PAD, iconY, TILE, TILE)
        if (rect.h >= TILE + 2) drawTile(graphics, iconRect, option.stack, false)
        val textX = rect.x + CARD_PAD * 2 + TILE
        val nameColor = if (active) 0xFF000000.toInt() or option.rgb else OwTheme.TEXT_FAINT
        if (!active) {
            graphics.text(font, truncateToWidth(font, option.name, rect.w - TILE - CARD_PAD * 3), textX, rect.y + (rect.h - 8) / 2, nameColor)
            return
        }
        graphics.text(font, option.name, textX, rect.y + CARD_PAD, nameColor)
        var ly = rect.y + CARD_PAD + NAME_H
        for (line in HudStyle.wrap(font, option.lines.joinToString(" "), textW)) {
            graphics.text(font, line, textX, ly, OwTheme.TEXT_DIM)
            ly += LINE_H
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (super.mouseClicked(event, doubleClick)) return true
        val button = event.button()
        val card = cards.firstOrNull { it.rect.contains(mx, my) }
        if (card != null) {
            if (button == 0 && card.option.enabled && menu.carried.isEmpty) send(card.option.slot, 0, ContainerInput.PICKUP)
            return true
        }
        val layout = invLayout
        val invSlot = layout?.hotbar?.firstOrNull { it.slot >= 0 && it.rect.contains(mx, my) }?.slot
            ?: layout?.inventory?.firstOrNull { it.rect.contains(mx, my) }?.slot
        if (invSlot != null) {
            if (lockClick(invSlot, button, event.hasAltDown())) return true
            val stack = stackAt(invSlot)
            when (button) {
                0, 1 -> if (!stack.isEmpty || !menu.carried.isEmpty) send(invSlot, button, ContainerInput.PICKUP)
                2 -> if (!stack.isEmpty) WynnOverhaulItemDebug.tryCopyToClipboard(stack)
            }
            return true
        }
        if (!menu.carried.isEmpty && (button == 0 || button == 1)) {
            val inPanels = mx >= invPanelX() - 4 && mx < sidePanelX() + sidePanelW() + 4 && my >= panelTop && my < panelTop + panelH
            if (!inPanels) send(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
        }
        return true
    }

    companion object {
        private const val PLAYER_SLOTS = 36
        private const val CARD_COLS = 9
        private const val TITLE_Y = 9
        private const val LEFT_TOP = 7
        private const val RIGHT_TOP = TITLE_Y + CAPTION_H + 6
        private const val CARD_PAD = 6
        private const val CARD_GAP = 4
        private const val NAME_H = 12
        private const val LINE_H = 10
        private const val CARD_MIN_H = TILE + CARD_PAD * 2
        private const val DISABLED_H = 20
        private const val MIN_BODY_H = 80
        const val TITLE_MARKER = ""
    }
}
