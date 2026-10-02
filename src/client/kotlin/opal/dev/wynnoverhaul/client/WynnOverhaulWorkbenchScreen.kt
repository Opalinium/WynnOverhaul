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

class WynnOverhaulWorkbenchScreen(menu: AbstractContainerMenu, private val spec: Spec) :
    WynnOverhaulSideScreen(menu, spec.title, menu.slots.size - PLAYER_SLOTS) {
    class Spec(val marker: String, val title: String, val inputs: List<Int>, val result: Int?, val rowSize: Int = inputs.size, val upgrader: Boolean = false)

    private class Slot(val rect: Rect, val slot: Int)

    private var slots: List<Slot> = emptyList()
    private var controls: List<Control> = emptyList()
    private var invLayout: InventoryLayout? = null
    private var panelTop = 0
    private var panelH = 0

    override fun sidePanelW(): Int = WORK_COLS * TILE_STEP + 2 * MARGIN

    private fun isPlaceholder(stack: ItemStack): Boolean = plainName(stack).let { it.startsWith("Empty ") || it == "No Result" }

    private fun isClickHint(line: String): Boolean = line.contains("Click to", ignoreCase = true)

    private fun statusLines(stack: ItemStack): List<String> {
        val lines = WynnItemRarity.loreLines(stack).filter { !isClickHint(it) }
        val out = ArrayList<String>()
        for (line in lines) {
            if (line.isBlank() && (out.isEmpty() || out.last().isEmpty())) continue
            out.add(line)
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeLast()
        return out
    }

    private fun wrapStatus(lines: List<String>, width: Int): List<String> =
        lines.flatMap { if (it.isBlank()) listOf("") else HudStyle.wrap(font, it, width) }

    private fun statusHeight(lines: List<String>): Int = lines.sumOf { if (it.isEmpty()) BLANK_H else LINE_H }

    private fun statusColor(line: String): Int = when {
        line.startsWith("-") -> OwTheme.TEXT
        line.startsWith("Price") -> OwTheme.GOOD
        else -> OwTheme.TEXT_DIM
    }

    private fun bulkHint(stack: ItemStack): String? =
        WynnItemRarity.loreLines(stack).firstOrNull { it.contains("Shift Click to", ignoreCase = true) }
            ?.replace("Shift Click to", "Shift-click to")

    private fun hintOf(): String =
        spec.inputs.map { stackAt(it) }.firstOrNull { isPlaceholder(it) }
            ?.let { statusLines(it).filter { line -> line.isNotBlank() }.joinToString(" ") }.orEmpty()

    private fun actionReady(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val rgb = stack.hoverName.style.color?.value ?: return true
        return rgb != WAITING_RGB
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val invOrder = inventoryOrder()
        val invX = invPanelX()
        val sideX = sidePanelX()
        val invW = invPanelW()
        val sideW = sidePanelW()
        val gridX = sideX + MARGIN
        val inner = sideW - MARGIN * 2
        val action = stackAt(ACTION_SLOT)
        val hint = HudStyle.wrap(font, hintOf(), inner)
        val status = wrapStatus(statusLines(action), inner)
        val bulk = bulkHint(action)

        val tail = GAP + FOOTER_H + 4
        val fullH = height - MARGIN * 2 + 8
        val rightBody = hint.size * LINE_H + SECTION + spec.inputs.chunked(spec.rowSize).size * TILE_STEP + SECTION + statusHeight(status) + SECTION + ACTION_H + (if (bulk != null) LINE_H + 2 else 0)
        val leftIdeal = LEFT_TOP + inventoryNaturalH(invOrder) + tail
        val rightIdeal = RIGHT_TOP + rightBody + tail
        val h = minOf(fullH, maxOf(leftIdeal, rightIdeal))
        val top = ((height - h) / 2).coerceAtLeast(MARGIN - 4)
        panelTop = top
        panelH = h

        panel(graphics, invX - 4, top, invW + 8, h)
        panel(graphics, sideX - 4, top, sideW + 8, h)
        graphics.text(font, spec.title, gridX, top + TITLE_Y, OwTheme.TEXT)

        val back = stackAt(BACK_SLOT)
        val backLabel = if (back.isEmpty) "Back" else plainName(back)
        val backW = font.width(backLabel) + 16
        val backControl = Control(Rect(gridX + inner - backW, top + TITLE_Y - 4, backW, ACTION_H), backLabel, !back.isEmpty, false, OwTheme.TEXT, BACK_SLOT) {
            send(BACK_SLOT, 0, ContainerInput.PICKUP)
        }

        var y = top + RIGHT_TOP
        for (line in hint) {
            graphics.text(font, line, gridX, y, OwTheme.TEXT_DIM)
            y += LINE_H
        }
        y += SECTION

        val placed = ArrayList<Slot>()
        var hoverStack = ItemStack.EMPTY
        val inputRows = spec.inputs.chunked(spec.rowSize)
        for ((rowIndex, rowSlots) in inputRows.withIndex()) {
            val withResult = rowIndex == 0 && spec.result != null
            val shown = if (withResult) rowSlots + listOfNotNull(spec.result) else rowSlots
            val arrowW = if (withResult) ARROW_W else 0
            val rowW = shown.size * TILE_STEP - (TILE_STEP - TILE) + arrowW
            var tx = gridX + (inner - rowW) / 2
            for (slot in shown) {
                if (withResult && slot == spec.result) {
                    graphics.centeredText(font, "→", tx + ARROW_W / 2 - 2, y + (TILE - 8) / 2, OwTheme.TEXT_DIM)
                    tx += ARROW_W
                }
                val rect = Rect(tx, y, TILE, TILE)
                val hovered = rect.contains(mouseX, mouseY) && !backControl.rect.contains(mouseX, mouseY)
                drawTile(graphics, rect, stackAt(slot), hovered)
                if (hovered) hoverStack = stackAt(slot)
                placed.add(Slot(rect, slot))
                tx += TILE_STEP
            }
            y += TILE_STEP
        }
        slots = placed
        y += SECTION - (TILE_STEP - TILE)

        for (line in status) {
            if (line.isEmpty()) {
                y += BLANK_H
                continue
            }
            graphics.text(font, line, gridX, y, statusColor(line))
            y += LINE_H
        }
        y += SECTION
        val ready = actionReady(action)
        val label = if (action.isEmpty) "..." else plainName(action)
        val actionControl = Control(Rect(gridX, y, inner, ACTION_H), label, ready, true, OwTheme.ACCENT, ACTION_SLOT) {
            send(ACTION_SLOT, 0, ContainerInput.PICKUP)
        }
        controls = listOf(backControl, actionControl)
        if (bulk != null) graphics.text(font, truncateToWidth(font, bulk, inner), gridX, y + ACTION_H + 4, OwTheme.TEXT_DIM)
        val hoverControl = controls.firstOrNull { it.rect.contains(mouseX, mouseY) }
        for (control in controls) drawControl(graphics, control, control === hoverControl)

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
                hoverControl != null -> stackAt(hoverControl.tipSlot)
                !hoverStack.isEmpty -> hoverStack
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
            if (button == 0 && control.enabled) {
                if (control.tipSlot == ACTION_SLOT && event.hasShiftDown() && bulkHint(stackAt(ACTION_SLOT)) != null) {
                    send(ACTION_SLOT, 0, ContainerInput.QUICK_MOVE)
                } else {
                    control.action()
                }
            }
            return true
        }
        val target = slots.firstOrNull { it.rect.contains(mx, my) }
        if (target != null) {
            val stack = stackAt(target.slot)
            if (button == 0 && menu.carried.isEmpty && !stack.isEmpty && !isPlaceholder(stack)) send(target.slot, 0, ContainerInput.PICKUP)
            return true
        }
        val layout = invLayout
        val invSlot = layout?.hotbar?.firstOrNull { it.slot >= 0 && it.rect.contains(mx, my) }?.slot
            ?: layout?.inventory?.firstOrNull { it.rect.contains(mx, my) }?.slot
        if (invSlot != null) {
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
        private const val WORK_COLS = 9
        private const val ACTION_SLOT = 17
        private const val BACK_SLOT = 18
        private const val WAITING_RGB = 0xAAAAAA
        private const val TITLE_Y = 9
        private const val LEFT_TOP = 7
        private const val RIGHT_TOP = TITLE_Y + CAPTION_H + 8
        private const val LINE_H = 10
        private const val BLANK_H = 4
        private const val SECTION = 8
        private const val ARROW_W = 20
        private val UPGRADE_POWDERS = Spec("", "Upgrade Powders", listOf(11, 12, 13, 14), 15, upgrader = true)
        private val REMOVE_POWDERS = Spec("", "Remove Powders", listOf(13), null, upgrader = true)
        private val ASCEND = Spec("", "Ascend Equipment", listOf(11), 24, upgrader = true)
        private val IDENTIFY = Spec("\uE018", "Item Identifier", listOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24), null, 5)
        private val SELL = Spec("\uE016", "Sell Items", listOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24), null, 5)
        private val REPAIR = Spec("", "Repair Items", listOf(11, 12, 13, 14, 15), null)
        private val SPECS = listOf(UPGRADE_POWDERS, REMOVE_POWDERS, ASCEND, IDENTIFY, SELL, REPAIR)

        fun specFor(title: String): Spec? = SPECS.firstOrNull { title.contains(it.marker) }
    }
}
