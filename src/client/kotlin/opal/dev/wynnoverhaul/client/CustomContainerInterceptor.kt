package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu

object CustomContainerInterceptor {
    const val BANK_TITLE_MARKER = ""

    fun register() {
        ScreenEvents.BEFORE_INIT.register { _, screen, width, height ->
            if (!WynnOverhaulGate.inGame) return@register
            if (screen !is AbstractContainerScreen<*>) return@register
            if (screen is InventoryScreen) return@register
            pendingMenu = screen.menu
            pendingScreen = screen
            pendingTicks = 0
            held = create(screen)?.also { it.init(width, height) }
        }
    }

    private fun create(screen: AbstractContainerScreen<*>): Screen? {
        val title = screen.title.string
        val config = WynnOverhaulConfig.current
        val slots = screen.menu.slots.size
        return when {
            config.customBankEnabled && title.contains(BANK_TITLE_MARKER) -> WynnOverhaulBankScreen(screen.menu)
            config.customMerchantEnabled && title.contains(WynnOverhaulMerchantScreen.TITLE_MARKER) && slots == MERCHANT_MENU_SLOTS && looksLikeMerchant(screen.menu) -> WynnOverhaulMerchantScreen(screen.menu)
            config.customLootChestEnabled && title.contains(WynnOverhaulLootScreen.TITLE_MARKER) && slots == LOOT_MENU_SLOTS -> WynnOverhaulLootScreen(screen.menu, title)
            config.customUpgraderEnabled && title.contains(WynnOverhaulUpgraderScreen.TITLE_MARKER) && slots == LOOT_MENU_SLOTS -> WynnOverhaulUpgraderScreen(screen.menu)
            slots == LOOT_MENU_SLOTS -> WynnOverhaulWorkbenchScreen.specFor(title)
                ?.takeIf { if (it.upgrader) config.customUpgraderEnabled else config.customStationEnabled }
                ?.let { WynnOverhaulWorkbenchScreen(screen.menu, it) }
            else -> null
        }
    }

    fun heldScreen(menu: AbstractContainerMenu): Screen? = if (pendingMenu === menu) held else null

    private fun looksLikeMerchant(menu: AbstractContainerMenu): Boolean {
        val container = (menu.slots.size - PLAYER_SLOTS).coerceAtLeast(0)
        for (i in 0 until container) {
            val stack = menu.slots[i].item
            if (stack.isEmpty) continue
            if (WynnItemRarity.loreLines(stack).any { it.contains("to Buy", ignoreCase = true) }) return true
        }
        return false
    }

    fun tick(client: Minecraft) {
        val menu = pendingMenu ?: return
        val screen = pendingScreen
        if (!WynnOverhaulGate.inGame || ++pendingTicks > PENDING_TIMEOUT_TICKS || client.gui.screen() !== screen) {
            clearPending()
            return
        }
        val player = client.player ?: run { clearPending(); return }
        if (player.containerMenu !== menu) {
            clearPending()
            return
        }
        val target = held
            ?: if (WynnOverhaulConfig.current.customBankEnabled && WynnOverhaulBankScreen.looksLikeBank(menu)) {
                WynnOverhaulBankScreen(menu).also { it.init(client.window.guiScaledWidth, client.window.guiScaledHeight) }
            } else {
                return
            }
        clearPending()
        client.setScreenAndShow(target)
        target.tick()
    }

    private fun clearPending() {
        held = null
        pendingMenu = null
        pendingScreen = null
        pendingTicks = 0
    }

    private var pendingMenu: AbstractContainerMenu? = null
    private var pendingScreen: Screen? = null
    private var held: Screen? = null
    private var pendingTicks = 0

    private const val PENDING_TIMEOUT_TICKS = 100
    private const val LOOT_MENU_SLOTS = 63
    private const val PLAYER_SLOTS = 36
    private const val MERCHANT_MENU_SLOTS = 90
}
