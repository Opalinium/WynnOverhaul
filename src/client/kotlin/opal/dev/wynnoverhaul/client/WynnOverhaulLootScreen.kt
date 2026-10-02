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
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_STEP
import opal.dev.wynnoverhaul.client.WynnOverhaulSideScreen.Companion.ACTION_H

class WynnOverhaulLootScreen(menu: AbstractContainerMenu, private val rawTitle: String) :
    WynnOverhaulSideScreen(menu, "Loot Chest", menu.slots.size - PLAYER_SLOTS) {
    private val chestSlots = menu.slots.size - PLAYER_SLOTS
    private val takeQueue = ArrayDeque<Int>()
    private var warnText: String? = null
    private var warnUntilNanos = 0L
    private var chestTiles: List<Pair<Rect, Int>> = emptyList()
    private var controls: List<Control> = emptyList()
    private var invLayout: InventoryLayout? = null
    private var panelTop = 0
    private var panelH = 0
    private val tier = STARS.find(rawTitle)?.groupValues?.get(1)?.length ?: 0

    override fun sidePanelW(): Int = CHEST_COLS * TILE_STEP + 2 * MARGIN

    private fun chestOrder(): List<Int> {
        val filled = (0 until chestSlots).filter { !stackAt(it).isEmpty }
        return InventorySort.parse(WynnOverhaulConfig.current.inventorySort).apply(filled) { stackAt(it) }
    }

    private fun warn(text: String) {
        warnText = text
        warnUntilNanos = System.nanoTime() + WARN_NANOS
    }

    override fun tick() {
        super.tick()
        val slot = takeQueue.firstOrNull() ?: return
        if (firstFreeInventorySlot() == null) {
            takeQueue.clear()
            warn("Inventory is full")
            return
        }
        takeQueue.removeFirst()
        if (!stackAt(slot).isEmpty) send(slot, 0, ContainerInput.QUICK_MOVE)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val order = chestOrder()
        val invOrder = inventoryOrder()
        val invX = invPanelX()
        val sideX = sidePanelX()
        val invW = invPanelW()
        val sideW = sidePanelW()
        val chestRows = ((order.size + CHEST_COLS - 1) / CHEST_COLS).coerceAtLeast(MIN_ROWS)
        val tail = GAP + FOOTER_H + 4
        val fullH = height - MARGIN * 2 + 8
        val leftIdeal = LEFT_TOP + inventoryNaturalH(invOrder) + tail
        val rightIdeal = RIGHT_TOP + chestRows * TILE_STEP + tail
        val h = minOf(fullH, maxOf(leftIdeal, rightIdeal))
        val top = ((height - h) / 2).coerceAtLeast(MARGIN - 4)
        panelTop = top
        panelH = h

        panel(graphics, invX - 4, top, invW + 8, h)
        panel(graphics, sideX - 4, top, sideW + 8, h)

        val gridX = sideX + MARGIN
        val inner = sideW - MARGIN * 2
        val titleY = top + TITLE_Y
        graphics.text(font, "Loot Chest", gridX, titleY, OwTheme.TEXT)
        if (tier > 0) {
            val text = "Tier $tier"
            graphics.text(font, text, gridX + inner - font.width(text), titleY, OwTheme.ACCENT)
        }

        val canTake = order.isNotEmpty() && takeQueue.isEmpty()
        controls = listOf(
            Control(Rect(gridX, top + ACTION_Y, inner, ACTION_H), if (takeQueue.isEmpty()) "Take all" else "Taking...", canTake, true, OwTheme.ACCENT, -1) {
                takeQueue.addAll(order)
            },
        )
        val hoverControl = controls.firstOrNull { it.rect.contains(mouseX, mouseY) }
        for (control in controls) drawControl(graphics, control, control === hoverControl)

        val gridTop = top + RIGHT_TOP
        val placed = ArrayList<Pair<Rect, Int>>()
        var hoverSlot = -1
        for ((i, slot) in order.withIndex()) {
            val rect = Rect(gridX + (i % CHEST_COLS) * TILE_STEP, gridTop + (i / CHEST_COLS) * TILE_STEP, TILE, TILE)
            placed.add(rect to slot)
            val hovered = hoverControl == null && rect.contains(mouseX, mouseY)
            if (hovered) hoverSlot = slot
            drawTile(graphics, rect, stackAt(slot), hovered)
        }
        chestTiles = placed
        if (order.isEmpty()) graphics.text(font, "(chest is empty)", gridX + 2, gridTop + 4, OwTheme.TEXT_DIM)

        val layout = layoutInventory(top + LEFT_TOP, invX + MARGIN, invOrder)
        invLayout = layout
        val hoverHotbar = layout.hotbar.firstOrNull { it.slot >= 0 && it.rect.contains(mouseX, mouseY) }
        val hoverInv = layout.inventory.firstOrNull { it.rect.contains(mouseX, mouseY) }
        drawInventory(graphics, layout, invX + MARGIN, hoverHotbar, hoverInv)
        drawInventoryFooter(graphics, invX, invW, footerY(top, h))

        val footer = footerY(top, h)
        val warning = warnText
        val footText = if (warning != null && System.nanoTime() < warnUntilNanos) warning else "${order.size} items"
        val footColor = if (warning != null && System.nanoTime() < warnUntilNanos) OwTheme.BAD else OwTheme.TEXT_DIM
        graphics.text(font, footText, sideX + sideW / 2 - font.width(footText) / 2, footer, footColor)

        val hoverDock = drawDock(graphics, mouseX, mouseY)
        drawCarried(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (menu.carried.isEmpty) {
            val stack = when {
                hoverSlot >= 0 -> stackAt(hoverSlot)
                hoverHotbar != null -> stackAt(hoverHotbar.slot)
                hoverInv != null -> stackAt(hoverInv.slot)
                hoverDock != null -> hoverDock
                else -> ItemStack.EMPTY
            }
            if (!stack.isEmpty) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (super.mouseClicked(event, doubleClick)) return true
        val button = event.button()
        val control = controls.firstOrNull { it.rect.contains(mx, my) }
        if (control != null) {
            if (button == 0 && control.enabled) control.action()
            return true
        }
        val layout = invLayout
        val invSlot = layout?.hotbar?.firstOrNull { it.slot >= 0 && it.rect.contains(mx, my) }?.slot
            ?: layout?.inventory?.firstOrNull { it.rect.contains(mx, my) }?.slot
        if (invSlot != null) {
            clickInventory(invSlot, button)
            return true
        }
        val tile = chestTiles.firstOrNull { it.first.contains(mx, my) }
        if (tile != null) {
            clickChest(tile.second, button)
            return true
        }
        if (!menu.carried.isEmpty && (button == 0 || button == 1)) {
            val inInv = mx >= invPanelX() - 4 && mx < invPanelX() + invPanelW() + 4 && my >= panelTop && my < panelTop + panelH
            if (inInv) {
                val slot = firstFreeInventorySlot()
                if (slot != null) send(slot, 0, ContainerInput.PICKUP) else warn("Inventory is full")
            } else if (mx < sidePanelX() - 4 || mx >= sidePanelX() + sidePanelW() + 4) {
                send(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
            }
        }
        return true
    }

    private fun clickInventory(slot: Int, button: Int) {
        val stack = stackAt(slot)
        when (button) {
            0, 1 -> if (!stack.isEmpty || !menu.carried.isEmpty) send(slot, button, ContainerInput.PICKUP)
            2 -> if (!stack.isEmpty) WynnOverhaulItemDebug.tryCopyToClipboard(stack)
        }
    }

    private fun clickChest(slot: Int, button: Int) {
        val stack = stackAt(slot)
        if (stack.isEmpty) return
        when (button) {
            0 -> {
                if (!menu.carried.isEmpty) return
                if (firstFreeInventorySlot() == null) warn("Inventory is full") else send(slot, 0, ContainerInput.QUICK_MOVE)
            }
            1 -> if (menu.carried.isEmpty) send(slot, 0, ContainerInput.PICKUP)
            2 -> WynnOverhaulItemDebug.tryCopyToClipboard(stack)
        }
    }

    companion object {
        private const val PLAYER_SLOTS = 36
        private const val CHEST_COLS = 9
        private const val MIN_ROWS = 3
        private const val TITLE_Y = 9
        private const val ACTION_Y = TITLE_Y + CAPTION_H + 2
        private const val LEFT_TOP = 7
        private const val RIGHT_TOP = ACTION_Y + ACTION_H + 8
        private const val WARN_NANOS = 2_500_000_000L
        private val STARS = Regex("""§f(✫+)""")
        const val TITLE_MARKER = "Loot Chest"
    }
}
