package opal.dev.overwatch.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import opal.dev.overwatch.Overwatch

class OverwatchClient : ClientModInitializer {

    private lateinit var toggleKey: KeyMapping
    private lateinit var configKey: KeyMapping
    private val attackGate = JitteredActionGate()

    override fun onInitializeClient() {
        val config = OverwatchConfig.ensureLoaded()

        toggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        configKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.config",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.START_MAIN.register { _ -> onRenderFrame() }

        Overwatch.LOGGER.info("Overwatch client initialized (enabled={})", config.enabled)
    }

    private fun onTick(client: Minecraft) {
        val config = OverwatchConfig.current
        if (client.level != null) SpellComboGuard.tick(client)

        while (configKey.consumeClick()) {
            client.setScreenAndShow(OverwatchHubScreen())
        }

        while (toggleKey.consumeClick()) {
            config.enabled = !config.enabled
            config.save()
            client.player?.sendOverlayMessage(
                Component.literal("[Overwatch] Attack ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal(if (config.enabled) "enabled" else "disabled")
                        .withStyle(if (config.enabled) ChatFormatting.GREEN else ChatFormatting.RED),
                ),
            )
        }

        if (!config.enabled || client.gui.screen() != null || client.level == null || !client.options.keyAttack.isDown) {
            attackGate.reset()
        }
    }

    private fun onRenderFrame() {
        val client = Minecraft.getInstance()
        val config = OverwatchConfig.current
        if (!config.enabled || client.gui.screen() != null || client.level == null) return
        val player = client.player ?: return
        if (!client.options.keyAttack.isDown) return

        RaytraceAttack.tryAttack(client, player, config, attackGate)
    }
}
