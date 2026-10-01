package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.AbstractContainerMenu

object PouchInterceptor {
    enum class PouchKind { INGREDIENT, EMERALD }

    fun init() {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (!WynnOverhaulGate.inGame) return@register
            if (pendingHost == null || System.nanoTime() - hostSetAtNanos > HOST_TIMEOUT_NANOS) return@register
            if (screen !is AbstractContainerScreen<*>) return@register
            pendingMenu = screen.menu
        }
    }

    fun heldHost(menu: AbstractContainerMenu): WynnOverhaulInventoryScreen? = if (pendingMenu === menu) pendingHost else null

    fun arm(host: WynnOverhaulInventoryScreen, kind: PouchKind) {
        pendingHost = host
        pendingKind = kind
        pendingMenu = null
        hostSetAtNanos = System.nanoTime()
    }

    fun clear(host: WynnOverhaulInventoryScreen) {
        if (pendingHost === host) {
            pendingHost = null
            pendingKind = null
            pendingMenu = null
        }
    }

    fun tick(client: Minecraft) {
        if (pendingHost != null && System.nanoTime() - hostSetAtNanos > HOST_TIMEOUT_NANOS) {
            pendingHost = null
            pendingKind = null
            pendingMenu = null
        }
        val menu = pendingMenu ?: return
        pendingMenu = null
        if (!WynnOverhaulGate.inGame) return
        val player = client.player ?: return
        if (player.containerMenu !== menu) return
        val host = pendingHost ?: return
        val kind = pendingKind ?: return
        pendingHost = null
        pendingKind = null
        host.attachPouchMenu(menu, kind)
        if (client.gui.screen() !== host) client.setScreenAndShow(host)
    }

    private var pendingHost: WynnOverhaulInventoryScreen? = null
    private var pendingKind: PouchKind? = null
    private var pendingMenu: AbstractContainerMenu? = null
    private var hostSetAtNanos = 0L

    private const val HOST_TIMEOUT_NANOS = 10_000_000_000L
}
