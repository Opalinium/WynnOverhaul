package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object ScreenHold {
    @Volatile
    var closeHeld = false

    @JvmStatic
    fun holdsClose(current: Screen?, incoming: Screen?): Boolean =
        closeHeld && incoming == null && current is WynnOverhaulInventoryScreen

    inline fun <T> keepOpen(block: () -> T): T {
        closeHeld = true
        try {
            return block()
        } finally {
            closeHeld = false
        }
    }

    private fun hostFor(screen: AbstractContainerScreen<*>): Screen? {
        val menu = screen.menu
        return MountSettingsInterceptor.heldScreen(menu)
            ?: BankInterceptor.heldScreen(menu)
            ?: PouchInterceptor.heldHost(menu)
            ?: ContentBookInterceptor.heldHost(menu)
            ?: CharacterInfo.heldHost(menu)
    }

    @JvmStatic
    fun isHeld(screen: AbstractContainerScreen<*>): Boolean = hostFor(screen) != null

    @JvmStatic
    fun renderHeld(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float): Boolean {
        if (screen !is AbstractContainerScreen<*>) return false
        val host = hostFor(screen)
        if (host != null) {
            host.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, partialTick)
            return true
        }
        return WynnOverhaulInventory.capturing(screen)
    }
}
