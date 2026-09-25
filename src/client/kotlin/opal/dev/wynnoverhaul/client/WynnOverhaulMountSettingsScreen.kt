package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import opal.dev.wynnoverhaul.WynnOverhaul

class WynnOverhaulMountSettingsScreen(private val menu: AbstractContainerMenu) : Screen(Component.literal("Mount Settings")) {
    private var mouseX = 0
    private var mouseY = 0
    private var journalButton: OwButton? = null
    private var characterButton: OwButton? = null

    private val ownSlots: List<Slot>
        get() {
            val total = menu.slots.size
            val count = (total - PLAYER_INV_SIZE).coerceAtLeast(0)
            return menu.slots.subList(0, count).filter { !it.item.isEmpty }
        }

    private fun panelWidth(): Int = PANEL_W
    private fun panelHeight(): Int = OwTheme.TITLE_BAR_H + SHORTCUT_ROW_H + PAD + maxOf(1, ownSlots.size) * ROW_H + PAD
    private fun panelLeft(): Int = (width - panelWidth()) / 2
    private fun panelTop(): Int = (height - panelHeight()) / 2
    private fun contentLeft(): Int = panelLeft() + PAD
    private fun contentTop(): Int = panelTop() + OwTheme.TITLE_BAR_H + SHORTCUT_ROW_H + PAD
    private fun rowWidth(): Int = panelWidth() - PAD * 2

    override fun init() {
        journalButton = OwButton(0, 0, 0, 0, Component.literal("Journal")) {
            openWynnOverhaulTab(WynnOverhaulInventoryScreen.InvTab.JOURNAL)
        }.also { addRenderableWidget(it) }
        characterButton = OwButton(0, 0, 0, 0, Component.literal("Character")) {
            openWynnOverhaulTab(WynnOverhaulInventoryScreen.InvTab.CHARACTER)
        }.also { addRenderableWidget(it) }
        layoutShortcuts()
    }

    private fun layoutShortcuts() {
        val left = panelLeft()
        val top = panelTop()
        val w = panelWidth()
        val by = top + OwTheme.TITLE_BAR_H + 2
        val bw = (w - PAD * 2 - 4) / 2
        val bh = SHORTCUT_ROW_H - 4
        journalButton?.let { it.x = left + PAD; it.y = by; it.width = bw; it.height = bh }
        characterButton?.let { it.x = left + PAD + bw + 4; it.y = by; it.width = bw; it.height = bh }
    }

    private fun openWynnOverhaulTab(tab: WynnOverhaulInventoryScreen.InvTab) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        try {
            player.closeContainer()
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("WynnOverhaul mount settings shortcut close threw", t)
        }
        if (player.containerMenu !== player.inventoryMenu) {
            player.containerMenu = player.inventoryMenu
        }
        client.setScreenAndShow(WynnOverhaulInventoryScreen(player.inventoryMenu, tab))
    }

    private fun hoveredSlot(): Slot? {
        val cl = contentLeft()
        val ct = contentTop()
        val w = rowWidth()
        ownSlots.forEachIndexed { i, slot ->
            val y = ct + i * ROW_H
            if (mouseX in cl until cl + w && mouseY in y until y + ROW_H - ROW_GAP) return slot
        }
        return null
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        layoutShortcuts()
        val left = panelLeft()
        val top = panelTop()
        val w = panelWidth()
        val h = panelHeight()
        OwTheme.drawPage(graphics, left, top, w, h)
        graphics.fill(left, top + OwTheme.TITLE_BAR_H - 1, left + w, top + OwTheme.TITLE_BAR_H, OwTheme.HAIRLINE)
        graphics.centeredText(font, title.string.uppercase(), left + w / 2, top + (OwTheme.TITLE_BAR_H - 8) / 2, OwTheme.TEXT)

        val hovered = hoveredSlot()
        val cl = contentLeft()
        val ct = contentTop()
        val rowW = rowWidth()
        val slots = ownSlots
        if (slots.isEmpty()) {
            graphics.text(font, "Loading...", cl, ct + 2, OwTheme.TEXT_DIM)
        } else {
            slots.forEachIndexed { i, slot ->
                drawRow(graphics, cl, ct + i * ROW_H, rowW, slot, slot === hovered)
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (!menu.carried.isEmpty) {
            graphics.item(menu.carried, mouseX - 8, mouseY - 8)
            graphics.itemDecorations(font, menu.carried, mouseX - 8, mouseY - 8)
        } else if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered.item, mouseX, mouseY)
        }
    }

    private fun drawRow(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, slot: Slot, hovered: Boolean) {
        val stack = slot.item
        val rowH = ROW_H - ROW_GAP
        graphics.fill(x, y, x + w, y + rowH, if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG)
        graphics.outline(x, y, w, rowH, if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        graphics.item(stack, x + ICON_PAD, y + (rowH - 16) / 2)
        val name = stripCodes(stack.hoverName.string)
        graphics.text(font, name, x + ICON_PAD + 20, y + (rowH - 8) / 2, OwTheme.TEXT)
    }

    private fun stripCodes(text: String): String {
        val noCodes = text.replace(Regex("§."), "")
        val sb = StringBuilder(noCodes.length)
        var i = 0
        while (i < noCodes.length) {
            val cp = noCodes.codePointAt(i)
            if (cp < 0xE000) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        val x = event.x().toInt()
        val y = event.y().toInt()
        val button = event.button()
        this.mouseX = x
        this.mouseY = y
        val slot = hoveredSlot() ?: return true
        if (button == 2 && WynnOverhaulItemDebug.tryCopyToClipboard(slot.item)) return true
        val player = Minecraft.getInstance().player ?: return true
        if (player.containerMenu !== menu) return true
        val index = slot.index
        when (button) {
            0 -> sendMountInput(index, 0, ContainerInput.PICKUP)
            1 -> sendMountInput(index, 1, ContainerInput.PICKUP)
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    private fun sendMountInput(slot: Int, button: Int, kind: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== menu) return
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul mount settings input failed", t)
        }
    }

    override fun onClose() {
        try {
            Minecraft.getInstance().player?.closeContainer()
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("WynnOverhaul mount settings close threw", t)
        }
        Minecraft.getInstance().gui.setScreen(null)
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val PLAYER_INV_SIZE = 36
        const val PANEL_W = 260
        const val PAD = 10
        const val SHORTCUT_ROW_H = 22
        const val ROW_H = 24
        const val ROW_GAP = 3
        const val ICON_PAD = 4
    }
}
