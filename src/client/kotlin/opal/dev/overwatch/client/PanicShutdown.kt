package opal.dev.overwatch.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.Overwatch

class PanicShutdown : ClientModInitializer {

    private var lastPosition: Vec3? = null
    private var lastVelocity: Vec3? = null
    private var lastDimension: ResourceKey<Level>? = null

    private var lastFrameYaw: Float? = null
    private var lastFramePitch: Float? = null
    private var lastFrameNanos: Long = 0L

    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.START_MAIN.register { _ -> onRenderFrame() }
    }

    private fun onTick(client: Minecraft) {
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            lastPosition = null
            lastVelocity = null
            lastDimension = null
            return
        }

        val position = player.position()
        val velocity = player.deltaMovement
        val dimension = level.dimension()
        val previousPosition = lastPosition
        val previousVelocity = lastVelocity
        val previousDimension = lastDimension
        lastPosition = position
        lastVelocity = velocity
        lastDimension = dimension

        if (previousPosition == null || previousVelocity == null || previousDimension == null) return
        if (!shouldCheck()) return

        val actualDisplacement = position.subtract(previousPosition)
        val unexplainedResidual = actualDisplacement.subtract(previousVelocity).length()
        val residualTolerance = if (player.hurtTime > 0) KNOCKBACK_RESIDUAL_TOLERANCE else NORMAL_RESIDUAL_TOLERANCE

        val reason = when {
            dimension != previousDimension -> "dimension changed"
            !player.isPassenger && unexplainedResidual > residualTolerance -> "sudden position change"
            else -> null
        } ?: return

        triggerShutdown(player, reason)
    }

    private fun onRenderFrame() {
        val client = Minecraft.getInstance()
        val player = client.player
        val now = System.nanoTime()
        val last = lastFrameNanos
        lastFrameNanos = now
        if (player == null) {
            lastFrameYaw = null
            lastFramePitch = null
            return
        }

        val yaw = player.yRot
        val pitch = player.xRot
        val previousYaw = lastFrameYaw
        val previousPitch = lastFramePitch
        lastFrameYaw = yaw
        lastFramePitch = pitch

        if (previousYaw == null || previousPitch == null || last == 0L) return
        if (!shouldCheck()) return

        val deltaSeconds = ((now - last).toDouble() / 1_000_000_000.0).coerceIn(MIN_FRAME_DELTA_SECONDS, MAX_FRAME_DELTA_SECONDS)
        val angleDegrees = angularDeltaDegrees(yaw, pitch, previousYaw, previousPitch)
        val impliedDegreesPerSecond = angleDegrees / deltaSeconds

        if (impliedDegreesPerSecond > MAX_CONTINUOUS_DEGREES_PER_SECOND) {
            triggerShutdown(player, "sudden view angle change")
        }
    }

    private fun shouldCheck(): Boolean {
        val config = OverwatchConfig.current
        return config.panicShutdownEnabled && (config.enabled || config.fishingEnabled)
    }

    private fun triggerShutdown(player: LocalPlayer, reason: String) {
        val config = OverwatchConfig.current
        config.enabled = false
        config.fishingEnabled = false
        config.fishingToggleActive = false
        config.save()

        Overwatch.LOGGER.warn("Overwatch panic shutdown triggered: {}", reason)
        player.sendOverlayMessage(
            Component.literal("[Overwatch] Panic shutdown (").withStyle(ChatFormatting.RED)
                .append(Component.literal(reason).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(") -- all automation disabled").withStyle(ChatFormatting.RED)),
        )
    }

    private fun angularDeltaDegrees(yaw: Float, pitch: Float, previousYaw: Float, previousPitch: Float): Double {
        var deltaYaw = ((yaw - previousYaw) % 360f).toDouble()
        if (deltaYaw > 180.0) deltaYaw -= 360.0
        if (deltaYaw < -180.0) deltaYaw += 360.0
        val deltaPitch = (pitch - previousPitch).toDouble()
        return kotlin.math.hypot(deltaYaw, deltaPitch)
    }

    private companion object {
        const val NORMAL_RESIDUAL_TOLERANCE = 3.0
        const val KNOCKBACK_RESIDUAL_TOLERANCE = 8.0
        const val MIN_FRAME_DELTA_SECONDS = 0.002
        const val MAX_FRAME_DELTA_SECONDS = 0.1
        const val MAX_CONTINUOUS_DEGREES_PER_SECOND = 2800.0
    }
}
