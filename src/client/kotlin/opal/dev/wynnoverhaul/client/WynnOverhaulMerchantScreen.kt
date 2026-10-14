package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.CAPTION_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.FOOTER_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.HOVER_BORDER
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.MARGIN
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.OUTSIDE_SLOT
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.SEARCH_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_STEP
import opal.dev.wynnoverhaul.client.WynnOverhaulSideScreen.Companion.ACTION_H

class WynnOverhaulMerchantScreen(menu: AbstractContainerMenu) :
    WynnOverhaulSideScreen(menu, "Merchant", menu.slots.size - PLAYER_SLOTS) {
    enum class Order(val label: String) {
        SHOP("Shop order"),
        PRICE_LOW("Price: low to high"),
        PRICE_HIGH("Price: high to low"),
        LEVEL("Level"),
        NAME("Name"),
    }

    private class Offer(
        val slot: Int,
        val stack: ItemStack,
        val name: String,
        val rgb: Int,
        val detail: String,
        val price: Long?,
        val affordable: Boolean,
        val level: Int?,
        val levelOk: Boolean,
    )

    private class Row(val rect: Rect, val offer: Offer)

    private class Extra(val slot: Int, val stack: ItemStack, val name: String, val clickable: Boolean)

    private val containerSlots = menu.slots.size - PLAYER_SLOTS
    private var searchField: OwTextField? = null
    private var orderDropdown: OwDropdown? = null
    private var searchHelpButton: OwButton? = null
    private var query = SearchMemory.merchant
    private var order = Order.SHOP
    private var scroll = 0
    private var rows: List<Row> = emptyList()
    private var controls: List<Control> = emptyList()
    private var listTop = 0
    private var listBottom = 0
    private var contentH = 0
    private var panelTop = 0
    private var panelH = 0
    private var invLayout: InventoryLayout? = null
    private var listLeft = 0
    private var listRight = 0

    override fun sidePanelW(): Int = SHOP_COLS * TILE_STEP + 2 * MARGIN

    override fun init() {
        OwDropdownOverlay.close()
        val x0 = sidePanelX() + MARGIN
        val inner = sidePanelW() - MARGIN * 2
        val orderW = font.width("Sort: Price: high to low") + 34
        searchField = OwTextField(font, x0, 0, inner - orderW - 8 - SEARCH_H, SEARCH_H).also {
            it.clearable = true
            it.value = query
            it.setResponder { v ->
                query = v
                SearchMemory.merchant = v
                scroll = 0
            }
            addRenderableWidget(it)
        }
        searchHelpButton = SearchHelpScreen.button(x0 + inner - orderW - 4 - SEARCH_H - 4, 0, SEARCH_H, SearchHelpScreen.Kind.ITEMS, this).also { addRenderableWidget(it) }
        orderDropdown = OwDropdown(
            x0 + inner - orderW, 0, orderW, SEARCH_H,
            "Sort",
            Order.entries.map { OwDropdownOverlay.Option(it.name, it.label) },
            { order.name },
        ) {
            order = Order.entries.firstOrNull { o -> o.name == it } ?: Order.SHOP
            scroll = 0
        }.also { addRenderableWidget(it) }
    }

    private fun merchantName(): String {
        val header = stackAt(HEADER_SLOT)
        return if (header.isEmpty) "Merchant" else plainName(header)
    }

    private fun isBuyLine(line: String): Boolean = line.contains("to Buy", ignoreCase = true)

    private fun isOffer(stack: ItemStack): Boolean = !stack.isEmpty && WynnItemRarity.loreLines(stack).any { isBuyLine(it) }

    private fun parseOffer(slot: Int, stack: ItemStack): Offer {
        val lines = WynnItemRarity.loreLines(stack)
        val priceLine = lines.firstOrNull { it.contains("²") && (it.contains("✔") || it.contains("✖")) }
        val price = priceLine?.let { PRICE.find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
        val levelLine = lines.firstOrNull { it.contains("Lv. Min", ignoreCase = true) }
        val level = levelLine?.let { LEVEL.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val detail = lines.firstOrNull { line ->
            line.isNotBlank() && line != "Price" && !isBuyLine(line) && line != priceLine && line != levelLine &&
                !line.endsWith(":") && !line.contains("Right-Click", ignoreCase = true) && !line.contains("Charges")
        }?.removePrefix("-")?.trim().orEmpty()
        val rgb = stack.hoverName.style.color?.value ?: OwTheme.TEXT
        return Offer(slot, stack, plainName(stack), rgb, detail, price, priceLine?.contains("✖") != true, level, levelLine?.contains("✖") != true)
    }

    private fun offers(): List<Offer> {
        val q = query.trim().lowercase()
        val list = (0 until containerSlots)
            .filter { it != HEADER_SLOT && isOffer(stackAt(it)) }
            .map { parseOffer(it, stackAt(it)) }
            .filter { q.isEmpty() || ItemSearch.matches(it.stack, q) }
        return when (order) {
            Order.SHOP -> list
            Order.PRICE_LOW -> list.sortedBy { it.price ?: Long.MAX_VALUE }
            Order.PRICE_HIGH -> list.sortedByDescending { it.price ?: -1L }
            Order.LEVEL -> list.sortedBy { it.level ?: Int.MAX_VALUE }
            Order.NAME -> list.sortedBy { it.name.lowercase() }
        }
    }

    private fun extras(): List<Extra> = (0 until containerSlots).mapNotNull { slot ->
        val stack = stackAt(slot)
        if (stack.isEmpty || slot == HEADER_SLOT || isOffer(stack)) return@mapNotNull null
        val name = plainName(stack)
        if (name.isBlank()) return@mapNotNull null
        val clickable = WynnItemRarity.loreLines(stack).any { it.contains("Click", ignoreCase = true) }
        Extra(slot, stack, name, clickable)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val covered = OwDropdownOverlay.covers(mouseX, mouseY, height)
        val mx = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseX
        val my = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseY
        renderShop(graphics, mx, my, partialTick)
        OwDropdownOverlay.render(graphics, mouseX, mouseY, height)
    }

    private fun renderShop(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val list = offers()
        val extra = extras().filter { it.clickable }
        val invOrder = inventoryOrder()
        val invX = invPanelX()
        val sideX = sidePanelX()
        val invW = invPanelW()
        val sideW = sidePanelW()
        val gridX = sideX + MARGIN
        val inner = sideW - MARGIN * 2

        val tail = GAP + FOOTER_H + 4
        val fullH = height - MARGIN * 2 + 8
        val extraH = if (extra.isEmpty()) 0 else ACTION_H + 6
        val leftIdeal = LEFT_TOP + inventoryNaturalH(invOrder) + tail
        val rightIdeal = RIGHT_TOP + extraH + IDEAL_ROWS * ROW_STEP + tail
        val h = minOf(fullH, maxOf(leftIdeal, rightIdeal))
        val top = ((height - h) / 2).coerceAtLeast(MARGIN - 4)
        panelTop = top
        panelH = h

        panel(graphics, invX - 4, top, invW + 8, h)
        panel(graphics, sideX - 4, top, sideW + 8, h)

        val name = merchantName()
        graphics.text(font, truncateToWidth(font, name, inner - 60), gridX, top + TITLE_Y, OwTheme.TEXT)
        val countText = "${list.size} items"
        graphics.text(font, countText, gridX + inner - font.width(countText), top + TITLE_Y, OwTheme.ACCENT)
        searchField?.y = top + SEARCH_Y
        searchHelpButton?.y = top + SEARCH_Y
        orderDropdown?.y = top + SEARCH_Y

        val builtControls = ArrayList<Control>()
        if (extra.isNotEmpty()) {
            val each = (inner - (extra.size - 1) * 2) / extra.size
            for ((i, e) in extra.withIndex()) {
                builtControls.add(Control(Rect(gridX + i * (each + 2), top + SEARCH_Y + SEARCH_H + 4, each, ACTION_H), e.name, true, false, OwTheme.TEXT, e.slot) {
                    send(e.slot, 0, ContainerInput.PICKUP)
                })
            }
        }
        controls = builtControls
        val hoverControl = controls.firstOrNull { it.rect.contains(mouseX, mouseY) }
        for (control in controls) drawControl(graphics, control, control === hoverControl)

        listTop = top + RIGHT_TOP + extraH
        listBottom = top + h - 4 - FOOTER_H - GAP
        listLeft = sideX
        listRight = sideX + sideW
        contentH = list.size * ROW_STEP
        scroll = scroll.coerceIn(0, (contentH - (listBottom - listTop)).coerceAtLeast(0))

        val placed = ArrayList<Row>()
        var hoverOffer: Offer? = null
        graphics.enableScissor(listLeft, listTop, listRight, listBottom)
        for ((i, offer) in list.withIndex()) {
            val y = listTop + i * ROW_STEP - scroll
            if (y + ROW_H <= listTop || y >= listBottom) continue
            val rect = Rect(gridX, y, inner - SCROLL_W, ROW_H)
            placed.add(Row(rect, offer))
            val hovered = hoverControl == null && covers(mouseX, mouseY) && rect.contains(mouseX, mouseY)
            if (hovered) hoverOffer = offer
            drawRow(graphics, rect, offer, hovered)
        }
        rows = placed
        if (list.isEmpty()) {
            val text = if (query.isBlank() && stackAt(HEADER_SLOT).isEmpty) "" else if (query.isBlank()) "(nothing for sale)" else "(no matches)"
            if (stackAt(HEADER_SLOT).isEmpty) OwSkeleton.bars(graphics, gridX, listTop + 2, inner, 4, ROW_H, ROW_STEP - ROW_H) else graphics.text(font, text, gridX + 2, listTop + 4, OwTheme.TEXT_DIM)
        }
        graphics.disableScissor()
        drawScroll(graphics)

        val layout = layoutInventory(top + LEFT_TOP, invX + MARGIN, invOrder)
        invLayout = layout
        val hoverHotbar = layout.hotbar.firstOrNull { it.slot >= 0 && it.rect.contains(mouseX, mouseY) }
        val hoverInv = layout.inventory.firstOrNull { it.rect.contains(mouseX, mouseY) }
        drawInventory(graphics, layout, invX + MARGIN, hoverHotbar, hoverInv)
        drawInventoryFooter(graphics, invX, invW, footerY(top, h))
        val footer = hoverOffer?.let { "Slot ${it.slot + 1}" } ?: "Click an item to buy it"
        graphics.text(font, footer, sideX + sideW / 2 - font.width(footer) / 2, footerY(top, h), OwTheme.TEXT_DIM)
        val hoverDock = drawDock(graphics, mouseX, mouseY)
        drawCarried(graphics, mouseX, mouseY)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (menu.carried.isEmpty) {
            val stack = when {
                hoverControl != null -> stackAt(hoverControl.tipSlot)
                hoverOffer != null -> hoverOffer.stack
                hoverHotbar != null -> stackAt(hoverHotbar.slot)
                hoverInv != null -> stackAt(hoverInv.slot)
                hoverDock != null -> hoverDock
                else -> ItemStack.EMPTY
            }
            if (!stack.isEmpty) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
                if (hoverControl == null) EquipCompareTooltip.draw(graphics, font, stack, mouseX, mouseY)
            }
        }
    }

    private fun covers(mx: Int, my: Int): Boolean = mx >= listLeft && mx < listRight && my >= listTop && my < listBottom

    private fun drawRow(graphics: GuiGraphicsExtractor, rect: Rect, offer: Offer, hovered: Boolean) {
        graphics.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, if (hovered) OwTheme.TILE_HOVER else TILE_BG)
        graphics.outline(rect.x, rect.y, rect.w, rect.h, if (hovered) HOVER_BORDER else OwTheme.HAIRLINE)
        val icon = Rect(rect.x + 3, rect.y + (rect.h - TILE) / 2, TILE, TILE)
        drawTile(graphics, icon, offer.stack, false)
        val textX = icon.x + TILE + 6
        val priceText = offer.price?.let { "%,d²".format(it) }.orEmpty()
        val levelText = offer.level?.let { "Lv $it" }.orEmpty()
        val rightW = maxOf(font.width(priceText), font.width(levelText))
        val textW = rect.w - (textX - rect.x) - rightW - 12
        graphics.text(font, truncateToWidth(font, offer.name, textW), textX, rect.y + 4, 0xFF000000.toInt() or offer.rgb)
        if (offer.detail.isNotEmpty()) graphics.text(font, truncateToWidth(font, offer.detail, textW), textX, rect.y + 15, OwTheme.TEXT_DIM)
        val rx = rect.x + rect.w - 6
        if (priceText.isNotEmpty()) graphics.text(font, priceText, rx - font.width(priceText), rect.y + 4, if (offer.affordable) OwTheme.GOOD else OwTheme.BAD)
        if (levelText.isNotEmpty()) graphics.text(font, levelText, rx - font.width(levelText), rect.y + 15, if (offer.levelOk) OwTheme.TEXT_DIM else OwTheme.BAD)
        if (!offer.affordable || !offer.levelOk) graphics.fill(rect.x + 1, rect.y + 1, rect.x + rect.w - 1, rect.y + rect.h - 1, UNAVAILABLE_WASH)
    }

    private fun drawScroll(graphics: GuiGraphicsExtractor) {
        val viewH = listBottom - listTop
        if (contentH <= viewH) return
        val x = listRight - SCROLL_W - 2
        val thumbH = (viewH * viewH / contentH).coerceAtLeast(12)
        val maxScroll = contentH - viewH
        val thumbY = listTop + (viewH - thumbH) * scroll / maxScroll
        graphics.fill(x, listTop, x + 2, listBottom, OwTheme.TILE_BORDER)
        graphics.fill(x, thumbY, x + 2, thumbY + thumbH, OwTheme.HAIRLINE)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (OwDropdownOverlay.mouseClicked(mx, my, height)) return true
        if (super.mouseClicked(event, doubleClick)) return true
        val button = event.button()
        val control = controls.firstOrNull { it.rect.contains(mx, my) }
        if (control != null) {
            if (button == 0 && control.enabled) control.action()
            return true
        }
        if (covers(mx, my)) {
            val row = rows.firstOrNull { it.rect.contains(mx, my) }
            if (row != null && menu.carried.isEmpty) {
                when (button) {
                    0 -> send(row.offer.slot, 0, ContainerInput.PICKUP)
                    2 -> WynnOverhaulItemDebug.tryCopyToClipboard(row.offer.stack)
                }
            }
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (OwDropdownOverlay.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY, height)) return true
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (covers(mouseX.toInt(), mouseY.toInt())) scroll = (scroll - (scrollY * ROW_STEP).toInt()).coerceAtLeast(0)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (OwDropdownOverlay.isOpen && event.key() == KEY_ESCAPE) {
            OwDropdownOverlay.close()
            return true
        }
        if (focusSearchOnKey(event, searchField)) return true
        return super.keyPressed(event)
    }

    companion object {
        private const val PLAYER_SLOTS = 36
        private const val UNAVAILABLE_WASH = 0x70100C08
        private const val SHOP_COLS = 9
        private const val HEADER_SLOT = 4
        private const val KEY_ESCAPE = 256
        private const val TITLE_Y = 9
        private const val SEARCH_Y = TITLE_Y + CAPTION_H + 2
        private const val LEFT_TOP = 7
        private const val RIGHT_TOP = SEARCH_Y + SEARCH_H + 6
        private const val ROW_H = 28
        private const val ROW_STEP = ROW_H + 2
        private const val SCROLL_W = 6
        private const val IDEAL_ROWS = 8
        private val PRICE = Regex("""([\d,]+)²""")
        private val LEVEL = Regex("""Lv\.?\s*Min:?\s*(\d+)""", RegexOption.IGNORE_CASE)
        const val TITLE_MARKER = ""

    }
}
