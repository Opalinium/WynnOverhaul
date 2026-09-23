package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.overwatch.Overwatch

object CharacterInfo {

    fun register() {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (!OverwatchGate.inGame) return@register
            if (!OverwatchConfig.current.customInventoryEnabled) return@register
            if (screen !is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) return@register
            if (screen is InventoryScreen) return@register
            if (bypassOnce) {
                bypassOnce = false
                return@register
            }
            val title = screen.title.string
            if (title.contains(ContentBookInterceptor.CONTENT_BOOK_TITLE_MARKER)) return@register
            pendingMenu = screen.menu
            pendingScreen = screen
            pendingTicks = 0
        }
    }

    fun tick(client: Minecraft) {
        val menu = pendingMenu ?: return
        if (!OverwatchGate.inGame) {
            clearPending()
            return
        }
        if (++pendingTicks > PENDING_TIMEOUT_TICKS) {
            clearPending()
            return
        }
        if (client.gui.screen() !== pendingScreen) {
            clearPending()
            return
        }
        val player = client.player ?: run { clearPending(); return }
        if (player.containerMenu !== menu) {
            clearPending()
            return
        }
        if (!menuHasContents(menu)) return
        if (!isCharacterMenuContent(menu)) {
            clearPending()
            return
        }
        pendingMenu = null
        pendingScreen = null
        val host = pendingCharacterHost ?: OverwatchInventoryScreen(player.inventoryMenu, OverwatchInventoryScreen.InvTab.CHARACTER)
        pendingCharacterHost = null
        host.attachCharacterMenu(menu)
        if (client.gui.screen() !== host) client.setScreenAndShow(host)
    }

    var pendingCharacterHost: OverwatchInventoryScreen? = null

    var bypassOnce: Boolean = false

    fun openCharacterContainer(client: Minecraft, host: OverwatchInventoryScreen, menuSlot: Int): String? {
        val player = client.player ?: return "No player"
        if (player.containerMenu !== player.inventoryMenu) return "Close the current container first"
        val hand = characterHand(player)
        if (hand == null && menuSlot !in 0 until player.inventoryMenu.slots.size) {
            return "Character Info item not found in your inventory"
        }
        pendingCharacterHost = host
        try {
            if (hand != null) client.gameMode?.useItem(player, hand)
            else client.gameMode?.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.PICKUP, player)
        } catch (t: Throwable) {
            pendingCharacterHost = null
            Overwatch.LOGGER.error("Overwatch character tab input failed", t)
            return "Couldn't open Character Info"
        }
        return null
    }

    private fun characterHand(player: Player): InteractionHand? = when {
        isInfo(player.mainHandItem) -> InteractionHand.MAIN_HAND
        isInfo(player.offhandItem) -> InteractionHand.OFF_HAND
        else -> null
    }

    fun findInfoSlot(): Int? {
        val player = Minecraft.getInstance().player ?: return null
        val menu = player.inventoryMenu
        val order = (36..44) + (0 until menu.slots.size).filter { it !in 36..44 }
        order.firstOrNull {
            if (it !in 0 until menu.slots.size) return@firstOrNull false
            isInfo(menu.slots[it].item)
        }?.let { return it }
        val open = player.containerMenu
        if (open !== menu) {
            ContentBookInterceptor.scanMirror(open, ::isInfo)?.let { return it }
        }
        return null
    }

    fun isInfo(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val letters = stack.hoverName.string.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
        if (letters.contains("Character Info", ignoreCase = true)) return true
        return WynnItemRarity.loreLines(stack).any { "unassigned skill points" in it.lowercase() }
    }

    fun readPoints(stack: ItemStack): Pair<Int?, Int?> {
        if (stack.isEmpty) return null to null
        val lore = WynnItemRarity.loreLines(stack)
        fun firstInt(pattern: Regex): Int? = lore.firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return firstInt(SKILL_POINTS_LINE) to firstInt(ABILITY_POINTS_LINE)
    }

    fun infoStack(): ItemStack {
        val player = Minecraft.getInstance().player ?: return ItemStack.EMPTY
        val slot = findInfoSlot() ?: return ItemStack.EMPTY
        val menu = player.inventoryMenu
        if (slot !in 0 until menu.slots.size) return ItemStack.EMPTY
        return menu.slots[slot].item
    }

    private fun isCharacterMenuContent(menu: AbstractContainerMenu): Boolean {
        for (slot in 0 until menu.slots.size) {
            val stack = menu.slots[slot].item
            if (stack.isEmpty) continue
            val letters = stack.hoverName.string.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
            if (letters.contains("Skill Crystal", ignoreCase = true)) return true
            if (letters.contains("Ability Tree", ignoreCase = true)) return true
        }
        return false
    }

    private fun menuHasContents(menu: AbstractContainerMenu): Boolean {
        for (slot in 0 until menu.slots.size) {
            if (!menu.slots[slot].item.isEmpty) return true
        }
        return false
    }

    private fun clearPending() {
        pendingMenu = null
        pendingScreen = null
        pendingTicks = 0
    }

    private var pendingMenu: AbstractContainerMenu? = null
    private var pendingScreen: Screen? = null
    private var pendingTicks = 0

    private const val PENDING_TIMEOUT_TICKS = 100

    private val SKILL_POINTS_LINE = Regex("""Unassigned Skill Points:\s*(\d+)""")
    private val ABILITY_POINTS_LINE = Regex("""Unused Ability Points:\s*(\d+)""")
}
