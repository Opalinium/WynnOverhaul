package opal.dev.wynnoverhaul.client

import me.cortex.voxy.client.config.VoxyConfig
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder
import me.cortex.voxy.client.core.util.IrisUtil
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import opal.dev.wynnoverhaul.WynnOverhaul

object VoxyVista {
    private val voxyPresent: Boolean by lazy { FabricLoader.getInstance().isModLoaded("voxy") }
    private var hiddenByUs = false
    private var loggedError = false

    fun tick(client: Minecraft) {
        if (!voxyPresent) return
        try {
            tickInternal(client)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("VoxyVista failed", t)
            }
        }
    }

    private fun tickInternal(client: Minecraft) {
        val config = WynnOverhaulConfig.current
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

        if (!withinMap && !hiddenByUs && VoxyConfig.CONFIG.enableRendering) {
            hiddenByUs = true
            setRendering(false)
            announce(player, config, false)
        } else if (withinMap && hiddenByUs) {
            hiddenByUs = false
            setRendering(true)
            announce(player, config, true)
        }
    }

    private fun announce(player: net.minecraft.world.entity.player.Player, config: WynnOverhaulConfig, shown: Boolean) {
        if (!config.voxyVistaMessages) return
        player.sendOverlayMessage(
            Component.literal("[WynnOverhaul] Voxy terrain ").withStyle(ChatFormatting.GRAY).append(
                Component.literal(if (shown) "shown" else "hidden (outside Wynncraft map)")
                    .withStyle(if (shown) ChatFormatting.GREEN else ChatFormatting.YELLOW),
            ),
        )
    }

    private fun setRendering(enabled: Boolean) {
        VoxyConfig.CONFIG.enableRendering = enabled
        val holder = IVoxyRenderSystemHolder.getNullableHolder()
        if (enabled) holder?.`voxy$createRenderer`() else holder?.`voxy$shutdownRenderer`()
        IrisUtil.reload()
    }

    private fun restore(client: Minecraft) {
        if (hiddenByUs) {
            hiddenByUs = false
            setRendering(true)
        }
    }
}
