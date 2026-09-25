package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.PauseScreen

object ScreenGate {
    fun blockedByScreen(client: Minecraft): Boolean {
        val screen = client.gui.screen() ?: return false
        return screen !is ChatScreen && screen !is PauseScreen
    }
}
