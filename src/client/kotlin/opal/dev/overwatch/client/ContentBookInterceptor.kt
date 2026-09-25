package opal.dev.overwatch.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack
import opal.dev.overwatch.Overwatch

class ContentBookInterceptor : ClientModInitializer {
    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (!OverwatchGate.inGame) return@register
            if (!OverwatchConfig.current.contentBookOverrideEnabled) return@register
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!screen.title.string.contains(CONTENT_BOOK_TITLE_MARKER)) return@register
            pendingMenu = screen.menu
        }
    }

    companion object {
        const val CONTENT_BOOK_TITLE_MARKER = "\uDAFF\uDFEE"

        var pendingJournalHost: OverwatchInventoryScreen? = null

        fun tick(client: Minecraft) {
            val menu = pendingMenu ?: return
            pendingMenu = null
            if (!OverwatchGate.inGame) return
            val player = client.player ?: return
            if (player.containerMenu !== menu) return
            val host = pendingJournalHost ?: OverwatchInventoryScreen(player.inventoryMenu, OverwatchInventoryScreen.InvTab.JOURNAL)
            pendingJournalHost = null
            host.attachJournalMenu(menu)
            if (client.gui.screen() !== host) client.setScreenAndShow(host)
        }

        fun openJournalContainer(client: Minecraft, host: OverwatchInventoryScreen, menuSlot: Int): String? {
            val player = client.player ?: return "No player"
            if (ContentBookQuery.isActive) return "Still working on the book -- try again in a moment"
            if (player.containerMenu !== player.inventoryMenu) return "Close the current container first"
            val hand = contentBookHand(player)
            if (hand != null) {
                client.gameMode?.useItem(player, hand)
            } else {
                if (menuSlot !in 0 until player.inventoryMenu.slots.size) return "Couldn't find the Content Book in your inventory"
                client.gameMode?.handleContainerInput(player.inventoryMenu.containerId, menuSlot, RIGHT_CLICK_BUTTON, ContainerInput.PICKUP, player)
            }
            pendingJournalHost = host
            return null
        }

        private fun triggerOpen(client: Minecraft, player: Player): Boolean {
            val hand = contentBookHand(player)
            if (hand != null) {
                client.gameMode?.useItem(player, hand)
                return true
            }
            val items = player.inventory.nonEquipmentItems
            val slot = items.indices.firstOrNull { isContentBook(items[it]) } ?: return false
            val menuSlot = if (slot < HOTBAR_SIZE) USE_ROW_SLOT_START + slot else slot
            client.gameMode?.handleContainerInput(player.inventoryMenu.containerId, menuSlot, RIGHT_CLICK_BUTTON, ContainerInput.PICKUP, player)
            return true
        }

        fun findBookSlot(player: Player): Int? {
            if (contentBookHand(player) != null) return -2
            val items = player.inventory.nonEquipmentItems
            items.indices.firstOrNull { isContentBook(items[it]) }?.let {
                return if (it < HOTBAR_SIZE) USE_ROW_SLOT_START + it else it
            }
            val open = player.containerMenu
            if (open !== player.inventoryMenu) {
                scanMirror(open, ::isContentBook)?.let { return it }
            }
            return null
        }

        fun mirrorSlotToMenu(mirrorIdx: Int): Int = if (mirrorIdx < 27) 9 + mirrorIdx else 36 + (mirrorIdx - 27)

        fun scanMirror(menu: AbstractContainerMenu, pred: (ItemStack) -> Boolean): Int? {
            if (menu.slots.size <= PLAYER_MIRROR_SIZE) return null
            val base = menu.slots.size - PLAYER_MIRROR_SIZE
            for (i in 35 downTo 0) {
                if (pred(menu.slots[base + i].item)) return mirrorSlotToMenu(i)
            }
            return null
        }

        private fun contentBookHand(player: Player): InteractionHand? = when {
            isContentBook(player.mainHandItem) -> InteractionHand.MAIN_HAND
            isContentBook(player.offhandItem) -> InteractionHand.OFF_HAND
            else -> null
        }

        fun isContentBook(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            val name = stack.hoverName.string
            if (name.contains(CONTENT_BOOK_TITLE_MARKER)) return true
            val letters = name.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
            return letters.contains("Content Book", ignoreCase = true)
        }

        private var pendingMenu: AbstractContainerMenu? = null

        private const val HOTBAR_SIZE = 9
        private const val USE_ROW_SLOT_START = 36
        private const val RIGHT_CLICK_BUTTON = 1
        private const val PLAYER_MIRROR_SIZE = 36
    }
}
