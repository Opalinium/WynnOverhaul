package opal.dev.overwatch.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.Overwatch
import opal.dev.overwatch.mixin.client.FishingHookAccessor
import kotlin.random.Random

class AutoFishClient : ClientModInitializer {

    private lateinit var fishingToggleKey: KeyMapping

    private val actionGate = JitteredActionGate()
    private var lastBlindCastAtNanos: Long = 0L
    private var hookFirstSeenAtNanos: Long = 0L
    private var nextHotspotScanNanos: Long = 0L
    private var wasActive: Boolean = false

    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()

        fishingToggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.fishing.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "fishing_hotspot"),
            FishingHotspotHudElement(),
        )
    }

    private fun onTick(client: Minecraft) {
        val config = OverwatchConfig.current

        while (fishingToggleKey.consumeClick()) {
            config.fishingToggleActive = !config.fishingToggleActive
            config.save()
            client.player?.sendOverlayMessage(
                Component.literal("[Overwatch] Auto-fishing ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal(if (config.fishingToggleActive) "enabled" else "disabled")
                        .withStyle(if (config.fishingToggleActive) ChatFormatting.GREEN else ChatFormatting.RED),
                ),
            )
        }

        if (!config.fishingEnabled || ScreenGate.blockedByScreen(client) || client.level == null) {
            resetState()
            return
        }
        val player = client.player ?: return

        val engaged = client.options.keyUse.isDown || config.fishingToggleActive
        val hand = if (engaged) fishingHand(player) else null
        if (hand == null) {
            if (wasActive) {
                reelInOnRelease(client, player)
            }
            resetState()
            return
        }
        wasActive = true

        val hook = findOwnHook(client, player)
        val now = System.nanoTime()

        if (hook == null) {
            if (hookFirstSeenAtNanos != 0L) {
                Overwatch.LOGGER.info("Overwatch fishing: hook no longer found (reeled/expired)")
            }
            hookFirstSeenAtNanos = 0L
            FishingHudState.active = false
            FishingHudState.hotspotType = null
        } else {
            if (hookFirstSeenAtNanos == 0L) {
                hookFirstSeenAtNanos = now
                Overwatch.LOGGER.info("Overwatch fishing: hook detected (viaPlayerFishingField={})", player.fishing === hook)
            }
            FishingHudState.active = true
            if (now >= nextHotspotScanNanos) {
                nextHotspotScanNanos = now + HOTSPOT_SCAN_INTERVAL_NANOS
                FishingHudState.hotspotType = detectHotspotType(client, hook, config)
            }
        }

        var reason: String? = null
        val wantsToAct = if (hook != null) {
            if (now - hookFirstSeenAtNanos >= MIN_BITE_GRACE_NANOS) {
                reason = reelReason(client, hook, config)
            }
            reason != null
        } else {
            now - lastBlindCastAtNanos >= settleTimeoutNanos(config)
        }

        if (!wantsToAct) {
            actionGate.reset()
            return
        }

        if (!actionGate.isReady(now) { nextDelayNanos(config) }) return

        castOrReel(client, player, hand)
        actionGate.markFired()
        if (hook == null) {
            Overwatch.LOGGER.info("Overwatch fishing: blind cast (no hook found, settle timeout elapsed)")
            lastBlindCastAtNanos = now
        } else {
            Overwatch.LOGGER.info("Overwatch fishing: reeling in (reason={})", reason)
            lastBlindCastAtNanos = 0L
        }
    }

    private fun resetState() {
        actionGate.reset()
        lastBlindCastAtNanos = 0L
        hookFirstSeenAtNanos = 0L
        nextHotspotScanNanos = 0L
        wasActive = false
        FishingHudState.active = false
        FishingHudState.hotspotType = null
    }

    private fun reelInOnRelease(client: Minecraft, player: LocalPlayer) {
        val hand = fishingHand(player) ?: return
        if (findOwnHook(client, player) == null) return
        castOrReel(client, player, hand)
        Overwatch.LOGGER.info("Overwatch fishing: reeling in (reason=key released)")
    }

    private fun findOwnHook(client: Minecraft, player: LocalPlayer): FishingHook? {
        player.fishing?.let { return it }
        val level = client.level ?: return null
        val box = player.boundingBox.inflate(HOOK_SEARCH_RADIUS)
        for (entity in level.getEntities(player, box) { it is FishingHook }) {
            val candidate = entity as FishingHook
            val owner = candidate.playerOwner
            if (owner != null && owner.uuid == player.uuid) return candidate
        }
        return null
    }

    private fun settleTimeoutNanos(config: OverwatchConfig): Long = (config.fishingSettleTimeoutMs * 1_000_000L).toLong()

    private fun nextDelayNanos(config: OverwatchConfig): Long {
        val delayMs = config.fishingDelayMinMs + Random.nextDouble() * (config.fishingDelayMaxMs - config.fishingDelayMinMs)
        return (delayMs * 1_000_000L).toLong()
    }

    private fun castOrReel(client: Minecraft, player: LocalPlayer, hand: InteractionHand) {
        val gameMode = client.gameMode ?: return
        gameMode.useItem(player, hand)
        player.swing(hand)
    }

    private fun fishingHand(player: LocalPlayer): InteractionHand? {
        if (FishingRodItems.isRealFishingRod(player.mainHandItem)) return InteractionHand.MAIN_HAND
        if (FishingRodItems.isRealFishingRod(player.offhandItem)) return InteractionHand.OFF_HAND
        return null
    }

    private fun reelReason(client: Minecraft, hook: FishingHook, config: OverwatchConfig): String? {
        if (config.fishingNativeCatchDetection && (hook as FishingHookAccessor).isBiting()) {
            return "native"
        }
        if (config.fishingNametagCatchDetection && hasNearbyRedTripleBang(client, hook, config)) {
            return "nametag"
        }
        return null
    }

    private fun hasNearbyRedTripleBang(client: Minecraft, hook: FishingHook, config: OverwatchConfig): Boolean {
        val level = client.level ?: return false
        val horizontalRange = config.fishingNametagRange
        val verticalRange = horizontalRange * VERTICAL_RANGE_FACTOR
        val box = hook.boundingBox.inflate(horizontalRange, verticalRange, horizontalRange)

        var bestScore = Double.MAX_VALUE
        for (entity in level.getEntities(hook, box) { true }) {
            val name = entity.getCustomName() ?: continue
            if (!name.getString().contains("!!!")) continue
            if (!isReddish(name) && name.getSiblings().none { isReddish(it) }) continue

            val dx = entity.x - hook.x
            val dy = entity.y - hook.y
            val dz = entity.z - hook.z
            val score = (dx * dx + dz * dz) / (horizontalRange * horizontalRange) +
                (dy * dy) / (verticalRange * verticalRange)
            if (score < bestScore) bestScore = score
        }
        return bestScore <= 1.0
    }

    private fun detectHotspotType(client: Minecraft, hook: FishingHook, config: OverwatchConfig): String? {
        val keywords = config.fishingHotspotKeywords.filter { it.isNotBlank() }
        if (keywords.isEmpty()) return null
        val level = client.level ?: return null
        val searchRange = config.fishingHotspotRange
        val searchVerticalRange = config.fishingHotspotVerticalRange
        val box = hook.boundingBox.inflate(searchRange, searchVerticalRange, searchRange)

        var bestDistanceSqr = Double.MAX_VALUE
        var center: Vec3? = null
        var genericKeyword: String? = null
        var specificKeyword: String? = null
        var specificDistanceSqr = Double.MAX_VALUE
        for (entity in level.getEntities(hook, box) { true }) {
            val name = entity.getCustomName() ?: continue
            val nameText = name.getString()
            val matched = keywords.firstOrNull { nameText.contains(it, ignoreCase = true) } ?: continue

            val dx = entity.x - hook.x
            val dz = entity.z - hook.z
            val distanceSqr = dx * dx + dz * dz
            if (distanceSqr < bestDistanceSqr) {
                bestDistanceSqr = distanceSqr
                center = entity.position()
            }

            if (matched.equals("hotspot", ignoreCase = true)) {
                if (genericKeyword == null) genericKeyword = matched
            } else if (distanceSqr < specificDistanceSqr) {
                specificDistanceSqr = distanceSqr
                specificKeyword = matched
            }
        }

        val hotspotCenter = center ?: return null
        val hotspotKeyword = specificKeyword ?: genericKeyword ?: return null

        val ringRadius = HotspotRingParticles.estimateRadius(
            hotspotCenter,
            Vec3(hook.x, hook.y, hook.z),
            searchRange,
            searchVerticalRange,
        )
        val inHotspot = if (ringRadius != null) {
            val dx = hook.x - hotspotCenter.x
            val dz = hook.z - hotspotCenter.z
            val horizontalDistance = kotlin.math.sqrt(dx * dx + dz * dz)
            horizontalDistance <= ringRadius + RING_RADIUS_TOLERANCE &&
                kotlin.math.abs(hook.y - hotspotCenter.y) <= searchVerticalRange
        } else {
            val dx = hook.x - hotspotCenter.x
            val dy = hook.y - hotspotCenter.y
            val dz = hook.z - hotspotCenter.z
            val score = (dx * dx + dz * dz) / (searchRange * searchRange) + (dy * dy) / (searchVerticalRange * searchVerticalRange)
            score <= 1.0
        }

        return if (inHotspot) titleCase(hotspotKeyword) else null
    }

    private fun titleCase(text: String): String =
        text.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun isReddish(component: Component): Boolean {
        val color = component.getStyle().getColor() ?: return false
        val rgb = color.value
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        if (r < RED_MIN_CHANNEL) return false
        return g <= r * RED_OTHER_CHANNEL_RATIO && b <= r * RED_OTHER_CHANNEL_RATIO
    }

    private companion object {
        const val HOOK_SEARCH_RADIUS = 64.0
        const val HOTSPOT_SCAN_INTERVAL_NANOS = 250_000_000L
        const val MIN_BITE_GRACE_NANOS = 1_500_000_000L
        const val VERTICAL_RANGE_FACTOR = 0.4
        const val RED_MIN_CHANNEL = 120
        const val RED_OTHER_CHANNEL_RATIO = 0.55
        const val RING_RADIUS_TOLERANCE = 0.5
    }
}
