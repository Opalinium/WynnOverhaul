package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.CAPTION_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.FOOTER_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.HOVER_BORDER
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.MARGIN
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_ENTITY_SIZE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_W
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.SECTION_GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG_EMPTY
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_STEP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_TINT_ALPHA

abstract class WynnOverhaulSideScreen(
    protected val menu: AbstractContainerMenu,
    title: String,
    protected val inventoryStart: Int,
) : Screen(Component.literal(title)) {
    protected class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(px: Int, py: Int): Boolean = px >= x && px < x + w && py >= y && py < y + h
    }

    protected class Tile(val rect: Rect, val slot: Int)

    protected class Control(
        val rect: Rect,
        val label: String,
        val enabled: Boolean,
        val accent: Boolean,
        val tint: Int,
        val tipSlot: Int,
        val action: () -> Unit,
    )

    protected class InventoryLayout(val hotbarY: Int, val hotbar: List<Tile>, val inventory: List<Tile>)

    protected abstract fun sidePanelW(): Int

    protected fun stackAt(slot: Int): ItemStack = menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

    protected fun plainName(stack: ItemStack): String = TextClean.clean(stack.hoverName.string)

    protected fun matches(stack: ItemStack, query: String): Boolean {
        return ItemSearch.matches(stack, query)
    }

    protected fun inventoryHidden(slot: Int): Boolean {
        val offset = slot - inventoryStart
        if (offset in 0 until ACCESSORY_COUNT) return true
        if (offset == EMERALD_POUCH_OFFSET) return true
        val stack = stackAt(slot)
        return HotbarHudElement.isHidden(stack) && !WynnPouches.isEmeraldPouch(stack)
    }

    protected fun depositable(slot: Int): Boolean = slot >= inventoryStart + ACCESSORY_COUNT && slot != inventoryStart + EMERALD_POUCH_OFFSET

    protected fun firstFreeInventorySlot(): Int? =
        (inventoryStart + ACCESSORY_COUNT until inventoryStart + 36).firstOrNull { depositable(it) && stackAt(it).isEmpty }

    protected fun availableWidth(): Int = width - MARGIN * 2

    private fun dockW(): Int = TILE + GAP + PREVIEW_W + GAP + TILE

    private fun showDock(): Boolean = availableWidth() >= dockW() + GAP + invPanelW() + GAP + sidePanelW()

    protected fun invPanelW(): Int = INV_COLS * TILE_STEP + 2 * MARGIN

    private fun startX(): Int {
        val total = (if (showDock()) dockW() + GAP else 0) + invPanelW() + GAP + sidePanelW()
        return ((width - total) / 2).coerceAtLeast(MARGIN)
    }

    private fun dockX(): Int = startX()

    protected fun invPanelX(): Int = startX() + (if (showDock()) dockW() + GAP else 0)

    protected fun sidePanelX(): Int = invPanelX() + invPanelW() + GAP

    protected fun inventoryOrder(): List<Int> =
        (inventoryStart until inventoryStart + 27).filter { !inventoryHidden(it) && !stackAt(it).isEmpty }

    protected fun inventoryNaturalH(order: List<Int>): Int {
        val rows = ((order.size + INV_COLS - 1) / INV_COLS).coerceAtLeast(1)
        return CAPTION_H + TILE_STEP + SECTION_GAP + CAPTION_H + rows * TILE_STEP
    }

    protected fun layoutInventory(top: Int, gridX: Int, order: List<Int>): InventoryLayout {
        val hotbarY = top + CAPTION_H
        val hotbar = ArrayList<Tile>()
        for (i in 0 until INV_COLS) {
            val slot = inventoryStart + 27 + i
            val stack = stackAt(slot)
            val show = !HotbarHudElement.isHidden(stack) || WynnPouches.isEmeraldPouch(stack)
            hotbar.add(Tile(Rect(gridX + i * TILE_STEP, hotbarY, TILE, TILE), if (show) slot else -1))
        }
        val gridTop = hotbarY + TILE_STEP + SECTION_GAP + CAPTION_H
        val inventory = ArrayList<Tile>()
        for ((i, slot) in order.withIndex()) {
            inventory.add(Tile(Rect(gridX + (i % INV_COLS) * TILE_STEP, gridTop + (i / INV_COLS) * TILE_STEP, TILE, TILE), slot))
        }
        return InventoryLayout(hotbarY, hotbar, inventory)
    }

    protected fun drawInventory(graphics: GuiGraphicsExtractor, layout: InventoryLayout, gridX: Int, hoverHotbar: Tile?, hoverInv: Tile?) {
        graphics.text(font, "Hotbar", gridX, layout.hotbarY - CAPTION_H + 2, OwTheme.TEXT_DIM)
        for (tile in layout.hotbar) if (tile.slot >= 0) drawTile(graphics, tile.rect, stackAt(tile.slot), tile === hoverHotbar, tile.slot)
        graphics.text(font, "Inventory", gridX, layout.hotbarY + TILE_STEP + SECTION_GAP + 2, OwTheme.TEXT_DIM)
        for (tile in layout.inventory) drawLoose(graphics, tile.rect, stackAt(tile.slot), tile === hoverInv, tile.slot)
    }

    protected fun drawInventoryFooter(graphics: GuiGraphicsExtractor, invX: Int, invW: Int, y: Int) {
        val free = (inventoryStart + ACCESSORY_COUNT until inventoryStart + 36).count { depositable(it) && stackAt(it).isEmpty }
        val freeText = if (free > 0) "$free free" else "Full"
        val emeralds = "%,d emeralds".format(WynnPouches.totalEmeralds((inventoryStart until menu.slots.size).map { stackAt(it) }))
        val sep = " · "
        val x = invX + invW / 2 - (font.width(freeText) + font.width(sep) + font.width(emeralds)) / 2
        graphics.text(font, freeText, x, y, if (free > 0) OwTheme.TEXT_DIM else OwTheme.BAD)
        graphics.text(font, sep, x + font.width(freeText), y, OwTheme.TEXT_DIM)
        graphics.text(font, emeralds, x + font.width(freeText) + font.width(sep), y, OwTheme.GOOD)
    }

    protected fun footerY(top: Int, panelH: Int): Int = top + panelH - 4 - FOOTER_H + 3

    protected fun drawDock(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): ItemStack? {
        if (!showDock()) return null
        val player = Minecraft.getInstance().player ?: return null
        val dockX = dockX()
        val dollX = dockX + TILE + GAP
        val dollTop = ((height - PREVIEW_H) / 2).coerceAtLeast(MARGIN)
        val armorX = dollX - GAP - TILE
        val armorTop = dollTop + (PREVIEW_H - 4 * TILE_STEP) / 2
        val tiles = ArrayList<Pair<Rect, ItemStack>>()
        for (i in 0 until 4) {
            tiles.add(Rect(armorX, armorTop + i * TILE_STEP, TILE, TILE) to (player.inventoryMenu.slots.getOrNull(ARMOR_FIRST_SLOT + i)?.item ?: ItemStack.EMPTY))
        }
        val rightX = dollX + PREVIEW_W + GAP
        tiles.add(Rect(rightX, dollTop, TILE, TILE) to (player.inventoryMenu.slots.getOrNull(OFFHAND_MENU_SLOT)?.item ?: ItemStack.EMPTY))
        val jx = maxOf(dockX, armorX - TILE_STEP)
        val jy = armorTop + 4 * TILE_STEP + 12
        for (i in 0 until ACCESSORY_COUNT) {
            tiles.add(Rect(jx + (i % 2) * TILE_STEP, jy + CAPTION_H + (i / 2) * TILE_STEP, TILE, TILE) to stackAt(inventoryStart + i))
        }
        graphics.text(font, "Accessories", jx, jy + 2, OwTheme.TEXT_DIM)
        val level = WynnLevelTracker.level
        if (level != null) {
            val text = "Lv $level"
            graphics.text(font, text, dollX + (PREVIEW_W - font.width(text)) / 2, maxOf(2, dollTop - 14), OwTheme.TEXT_DIM)
        }
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, dollX, dollTop, dollX + PREVIEW_W, dollTop + PREVIEW_H, PREVIEW_ENTITY_SIZE, 0.0625f,
            mouseX.toFloat(), mouseY.toFloat(), player,
        )
        var hovered: ItemStack? = null
        for ((rect, stack) in tiles) {
            val over = rect.contains(mouseX, mouseY)
            drawTile(graphics, rect, stack, over)
            if (over && !stack.isEmpty) hovered = stack
        }
        return hovered
    }

    protected fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        OwTheme.glassPanel(graphics, x, y, w, h)
    }

    protected fun drawTile(graphics: GuiGraphicsExtractor, r: Rect, stack: ItemStack, hovered: Boolean, slot: Int = -1) {
        ItemTiles.tile(graphics, font, r.x, r.y, stack, hovered, slot >= 0 && SlotLocks.isLocked(menu, slot))
    }

    protected fun drawLoose(graphics: GuiGraphicsExtractor, r: Rect, stack: ItemStack, hovered: Boolean, slot: Int = -1) {
        ItemTiles.loose(graphics, font, r.x, r.y, stack, hovered, slot >= 0 && SlotLocks.isLocked(menu, slot))
    }

    protected fun lockClick(slot: Int, button: Int, alt: Boolean): Boolean =
        button == 0 && alt && SlotLocks.toggle(menu, slot)

    protected fun drawControl(graphics: GuiGraphicsExtractor, c: Control, hovered: Boolean) {
        val r = c.rect
        val active = hovered && c.enabled
        graphics.fill(r.x, r.y, r.x + r.w, r.y + r.h, if (c.accent && c.enabled) OwTheme.ACCENT_DIM else if (active) OwTheme.TILE_HOVER else TILE_BG)
        val border = when {
            !c.enabled -> OwTheme.TILE_BORDER
            active -> OwTheme.BORDER_BRIGHT
            c.accent -> OwTheme.ACCENT
            else -> OwTheme.HAIRLINE
        }
        graphics.outline(r.x, r.y, r.w, r.h, border)
        val color = if (c.enabled) c.tint else OwTheme.TEXT_FAINT
        graphics.centeredText(font, truncateToWidth(font, c.label, r.w - 4), r.x + r.w / 2, r.y + (r.h - 8) / 2, color)
    }

    protected fun drawCarried(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (menu.carried.isEmpty) return
        graphics.item(menu.carried, mouseX - 8, mouseY - 8)
        graphics.itemDecorations(font, menu.carried, mouseX - 8, mouseY - 8)
    }

    protected fun send(slot: Int, button: Int, kind: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== menu) return
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul container input failed", t)
        }
    }

    protected open fun closeContainer() {
        try {
            Minecraft.getInstance().player?.closeContainer()
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("WynnOverhaul container close threw", t)
        }
        Minecraft.getInstance().gui.setScreen(null)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!isTextInputFocused() && Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        closeContainer()
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        const val INV_COLS = 7
        const val ACCESSORY_COUNT = 4
        const val ARMOR_FIRST_SLOT = 5
        const val OFFHAND_MENU_SLOT = 45
        const val EMERALD_POUCH_OFFSET = 5
        const val ACTION_H = 16
    }
}
