package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import opal.dev.wynnoverhaul.WynnOverhaul

class WynnOverhaulMountSettingsScreen(private val menu: AbstractContainerMenu) : Screen(Component.literal("Mount Settings")) {
    private var mouseX = 0
    private var mouseY = 0
    private var scrollY = 0
    private var journalButton: OwButton? = null
    private var characterButton: OwButton? = null
    private val fade = OwFade()

    private data class Card(val setting: MountSettingRows.Setting, val y: Int, val h: Int, val descLines: List<String>, val pills: List<Pill>)
    private data class Pill(val option: MountSettingRows.Option, val x: Int, val y: Int, val w: Int)

    private fun settings(): List<MountSettingRows.Setting> {
        val total = menu.slots.size
        val count = (total - PLAYER_INV_SIZE).coerceAtLeast(0)
        return menu.slots.subList(0, count)
            .filter { !it.item.isEmpty }
            .map { MountSettingRows.parse(it.index, it.item) }
    }

    private fun panelWidth(): Int = PANEL_W
    private fun panelLeft(): Int = (width - panelWidth()) / 2
    private fun innerWidth(): Int = panelWidth() - PAD * 2
    private fun bodyHeight(cards: List<Card>, info: List<MountSettingRows.Setting>): Int {
        val infoH = info.size * (INFO_H + CARD_GAP)
        val cardsH = if (cards.isEmpty()) 0 else cards.last().y + cards.last().h
        return maxOf(MIN_BODY_H, infoH + cardsH)
    }

    private fun layoutCards(settings: List<MountSettingRows.Setting>): List<Card> {
        val w = innerWidth() - CARD_PAD * 2
        var y = 0
        val cards = ArrayList<Card>()
        for (setting in settings) {
            val desc = if (setting.description.isEmpty()) emptyList() else HudStyle.wrap(font, setting.description, w)
            val pills = ArrayList<Pill>()
            var px = 0
            var py = 0
            for (option in setting.options) {
                val pw = font.width(option.label) + PILL_PAD * 2
                if (px > 0 && px + pw > w) {
                    px = 0
                    py += PILL_H + PILL_GAP
                }
                pills.add(Pill(option, px, py, pw))
                px += pw + PILL_GAP
            }
            val pillRows = if (pills.isEmpty()) 0 else pills.last().y / (PILL_H + PILL_GAP) + 1
            var h = CARD_PAD + TITLE_H
            if (desc.isNotEmpty()) h += 2 + desc.size * LINE_H
            if (pills.isNotEmpty()) h += 5 + pillRows * PILL_H + (pillRows - 1) * PILL_GAP
            if (setting.hint.isNotEmpty()) h += 5 + LINE_H
            h += CARD_PAD
            cards.add(Card(setting, y, h, desc, pills))
            y += h + CARD_GAP
        }
        return cards
    }

    private fun panelHeight(bodyH: Int): Int =
        (OwTheme.TITLE_BAR_H + SHORTCUT_ROW_H + PAD + bodyH + PAD).coerceAtMost(height - 16).coerceAtLeast(160)

    private fun panelTop(panelH: Int): Int = (height - panelH) / 2
    private fun bodyTop(panelH: Int): Int = panelTop(panelH) + OwTheme.TITLE_BAR_H + SHORTCUT_ROW_H + PAD
    private fun bodyViewH(panelH: Int): Int = panelH - OwTheme.TITLE_BAR_H - SHORTCUT_ROW_H - PAD * 2

    override fun init() {
        journalButton = OwButton(0, 0, 0, 0, Component.literal("Journal")) {
            openWynnOverhaulTab(WynnOverhaulInventoryScreen.InvTab.JOURNAL)
        }.also { addRenderableWidget(it) }
        characterButton = OwButton(0, 0, 0, 0, Component.literal("Character")) {
            openWynnOverhaulTab(WynnOverhaulInventoryScreen.InvTab.CHARACTER)
        }.also { addRenderableWidget(it) }
    }

    private fun layoutShortcuts(panelH: Int) {
        val left = panelLeft()
        val top = panelTop(panelH)
        val by = top + OwTheme.TITLE_BAR_H + 2
        val bw = (panelWidth() - PAD * 2 - 4) / 2
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

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        val all = settings()
        val info = all.filter { it.isInfo }
        val cards = layoutCards(all.filter { !it.isInfo })
        val bodyH = bodyHeight(cards, info)
        val panelH = panelHeight(bodyH)
        val left = panelLeft()
        val top = panelTop(panelH)
        val w = panelWidth()
        layoutShortcuts(panelH)

        OwTheme.drawPage(graphics, left, top, w, panelH)
        graphics.fill(left, top + OwTheme.TITLE_BAR_H - 1, left + w, top + OwTheme.TITLE_BAR_H, OwTheme.HAIRLINE)
        graphics.centeredText(font, title.string.uppercase(), left + w / 2, top + (OwTheme.TITLE_BAR_H - 8) / 2, OwTheme.TEXT)

        val viewTop = bodyTop(panelH)
        val viewH = bodyViewH(panelH)
        scrollY = scrollY.coerceIn(0, (bodyH - viewH).coerceAtLeast(0))
        val x = left + PAD
        val innerW = innerWidth()

        graphics.enableScissor(left, viewTop, left + w, viewTop + viewH)
        var y = viewTop - scrollY
        if (all.isEmpty()) {
            OwSkeleton.bars(graphics, x, y, innerW, 4, 18, 6)
        }
        for (banner in info) {
            graphics.fill(x, y, x + innerW, y + INFO_H, OwTheme.TILE_BG)
            graphics.outline(x, y, innerW, INFO_H, OwTheme.HAIRLINE)
            graphics.centeredText(font, truncateToWidth(font, banner.title, innerW - 8), x + innerW / 2, y + (INFO_H - 8) / 2, OwTheme.TEXT_DIM)
            y += INFO_H + CARD_GAP
        }
        val cardsTop = y
        var hovered: Card? = null
        for (card in cards) {
            val cy = cardsTop + card.y
            if (cy + card.h < viewTop || cy > viewTop + viewH) continue
            val isHover = mouseX in x until x + innerW && mouseY in cy until cy + card.h && mouseY in viewTop until viewTop + viewH
            if (isHover) hovered = card
            drawCard(graphics, x, cy, innerW, card, isHover)
        }
        graphics.disableScissor()

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        fade.overlay(graphics, left, top + OwTheme.TITLE_BAR_H, w, panelH - OwTheme.TITLE_BAR_H)

        if (!menu.carried.isEmpty) {
            graphics.item(menu.carried, mouseX - 8, mouseY - 8)
            graphics.itemDecorations(font, menu.carried, mouseX - 8, mouseY - 8)
        }
        hoveredSlot = hovered?.setting?.slot ?: -1
    }

    private var hoveredSlot = -1

    private fun drawCard(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, card: Card, hovered: Boolean) {
        graphics.fill(x, y, x + w, y + card.h, if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG)
        graphics.outline(x, y, w, card.h, if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        val inner = w - CARD_PAD * 2
        val cx = x + CARD_PAD
        var cy = y + CARD_PAD
        graphics.text(font, truncateToWidth(font, card.setting.title, inner), cx, cy + 1, OwTheme.TEXT)
        cy += TITLE_H
        if (card.descLines.isNotEmpty()) {
            cy += 2
            for (line in card.descLines) {
                graphics.text(font, line, cx, cy, OwTheme.TEXT_DIM)
                cy += LINE_H
            }
        }
        if (card.pills.isNotEmpty()) {
            cy += 5
            for (pill in card.pills) {
                val px = cx + pill.x
                val py = cy + pill.y
                if (pill.option.active) {
                    graphics.fill(px, py, px + pill.w, py + PILL_H, OwTheme.ACCENT_DIM)
                    graphics.outline(px, py, pill.w, PILL_H, OwTheme.BORDER_BRIGHT)
                    graphics.text(font, pill.option.label, px + PILL_PAD, py + (PILL_H - 8) / 2, OwTheme.TEXT)
                } else {
                    graphics.fill(px, py, px + pill.w, py + PILL_H, OwTheme.PANEL)
                    graphics.outline(px, py, pill.w, PILL_H, OwTheme.TILE_BORDER)
                    graphics.text(font, pill.option.label, px + PILL_PAD, py + (PILL_H - 8) / 2, OwTheme.TEXT_FAINT)
                }
            }
            val rows = card.pills.last().y / (PILL_H + PILL_GAP) + 1
            cy += rows * PILL_H + (rows - 1) * PILL_GAP
        }
        if (card.setting.hint.isNotEmpty()) {
            cy += 5
            graphics.text(font, truncateToWidth(font, card.setting.hint, inner), cx, cy, OwTheme.TEXT_FAINT)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        this.mouseX = event.x().toInt()
        this.mouseY = event.y().toInt()
        val slotIndex = hoveredSlot
        if (slotIndex < 0) return true
        val button = event.button()
        if (button == 2) {
            WynnOverhaulItemDebug.tryCopyToClipboard(menu.slots.getOrNull(slotIndex)?.item ?: return true)
            return true
        }
        val player = Minecraft.getInstance().player ?: return true
        if (player.containerMenu !== menu) return true
        when (button) {
            0 -> sendMountInput(slotIndex, 0, ContainerInput.PICKUP)
            1 -> sendMountInput(slotIndex, 1, ContainerInput.PICKUP)
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        this.scrollY = (this.scrollY - (scrollY * SCROLL_STEP).toInt()).coerceAtLeast(0)
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
        const val PANEL_W = 300
        const val PAD = 10
        const val SHORTCUT_ROW_H = 22
        const val CARD_PAD = 8
        const val CARD_GAP = 6
        const val TITLE_H = 12
        const val LINE_H = 10
        const val INFO_H = 20
        const val PILL_H = 14
        const val PILL_GAP = 4
        const val PILL_PAD = 7
        const val MIN_BODY_H = 40
        const val SCROLL_STEP = 14
    }
}
