package opal.dev.overwatch.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class OverwatchClient : ClientModInitializer {
    private lateinit var toggleKey: KeyMapping
    private lateinit var configKey: KeyMapping
    private lateinit var soulsToggleKey: KeyMapping
    private lateinit var soulsRecenterKey: KeyMapping
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

        soulsToggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.souls_camera",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        soulsRecenterKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.souls_recenter",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        WeaponAnimationRegistry.init()
        WeaponAnimations.init()

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.START_MAIN.register { _ -> onRenderFrame() }
    }

    private fun onTick(client: Minecraft) {
        val config = OverwatchConfig.current
        while (configKey.consumeClick()) {
            client.setScreenAndShow(OverwatchHubScreen())
        }
        OverwatchGate.refresh(client)
        CameraMemory.tick(client)
        if (!OverwatchGate.inGame) {
            attackGate.reset()
            return
        }
        if (client.level != null) SpellComboGuard.tick(client)
        TownNpcTracker.tick(client)
        XaeroHook.tick(client)

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

        while (soulsToggleKey.consumeClick()) {
            config.soulsCameraEnabled = !config.soulsCameraEnabled
            config.save()
            if (config.soulsCameraEnabled) client.options.setCameraType(CameraType.THIRD_PERSON_BACK)
            client.player?.sendOverlayMessage(
                Component.literal("[Overwatch] Souls camera ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal(if (config.soulsCameraEnabled) "enabled" else "disabled")
                        .withStyle(if (config.soulsCameraEnabled) ChatFormatting.GREEN else ChatFormatting.RED),
                ),
            )
        }
        while (soulsRecenterKey.consumeClick()) {
            SoulsCamera.recenter(client)
        }

        if (!config.enabled || client.gui.screen() != null || client.level == null || !ActiveWeapon.attackKey(client).isDown) {
            attackGate.reset()
        }
    }

    private fun onRenderFrame() {
        val client = Minecraft.getInstance()
        val config = OverwatchConfig.current
        if (!OverwatchGate.inGame) return
        if (!config.enabled || client.gui.screen() != null || client.level == null) return
        val player = client.player ?: return
        if (!ActiveWeapon.attackKey(client).isDown) return

        AutoAttack.tick(client, player, config, attackGate)
    }
}
