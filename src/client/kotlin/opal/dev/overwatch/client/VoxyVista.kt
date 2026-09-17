package opal.dev.overwatch.client

import me.cortex.voxy.client.config.VoxyConfig
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import opal.dev.overwatch.Overwatch

object VoxyVista {

    private val voxyPresent: Boolean by lazy { FabricLoader.getInstance().isModLoaded("voxy") }
    private var previousWithinMap: Boolean? = null
    private var overridden = false
    private var loggedError = false

    fun tick(client: Minecraft) {
        if (!voxyPresent) return
        try {
            tickInternal(client)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("VoxyVista failed", t)
            }
        }
    }

    private fun tickInternal(client: Minecraft) {
        val config = OverwatchConfig.current
        if (!config.voxyVistaEnabled) {
            restore(client)
            return
        }
        val player = client.player
        if (player == null || client.level == null) {
            restore(client)
            return
        }

        val x = player.x
        val z = player.z
        val withinMap = x >= -2560 && x <= 2047 && z >= -6144 && z <= -1
        overridden = true

        if (previousWithinMap != null && previousWithinMap != withinMap) {
            VoxyConfig.CONFIG.enableRendering = withinMap
            client.connection?.sendCommand("voxy reload")

            if (config.voxyVistaMessages) {
                player.sendOverlayMessage(
                    Component.literal("[Overwatch] Voxy terrain ").withStyle(ChatFormatting.GRAY).append(
                        Component.literal(if (withinMap) "shown" else "hidden (outside Wynncraft map)")
                            .withStyle(if (withinMap) ChatFormatting.GREEN else ChatFormatting.YELLOW),
                    ),
                )
            }
        }
        previousWithinMap = withinMap
    }

    private fun restore(client: Minecraft) {
        if (overridden) {
            VoxyConfig.CONFIG.enableRendering = true
            client.connection?.sendCommand("voxy reload")
            overridden = false
        }
        previousWithinMap = null
    }
}
