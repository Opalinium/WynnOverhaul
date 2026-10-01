package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.AbstractContainerMenu

object MountSettingsInterceptor {
    const val MOUNT_TITLE_MARKER = "󏿭"

    fun register() {
        ScreenEvents.BEFORE_INIT.register { _, screen, width, height ->
            if (!WynnOverhaulGate.inGame) return@register
            if (screen !is AbstractContainerScreen<*>) return@register
            if (screen is InventoryScreen) return@register
            pendingMenu = screen.menu
            pendingScreen = screen
            pendingTicks = 0
            held = if (screen.title.string == MOUNT_TITLE_MARKER) {
                WynnOverhaulMountSettingsScreen(screen.menu).also { it.init(width, height) }
            } else {
                null
            }
        }
    }

    fun heldScreen(menu: AbstractContainerMenu): Screen? = if (pendingMenu === menu) held else null

    fun tick(client: Minecraft) {
        val menu = pendingMenu ?: return
        val screen = pendingScreen
        if (!WynnOverhaulGate.inGame) {
            clearPending()
            return
        }
        if (++pendingTicks > PENDING_TIMEOUT_TICKS) {
            clearPending()
            return
        }
        if (client.gui.screen() !== screen) {
            clearPending()
            return
        }
        val player = client.player ?: run { clearPending(); return }
        if (player.containerMenu !== menu) {
            clearPending()
            return
        }
        if (screen?.title?.string != MOUNT_TITLE_MARKER) return
        val target = held ?: WynnOverhaulMountSettingsScreen(menu)
        clearPending()
        client.setScreenAndShow(target)
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
}
