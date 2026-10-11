package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.phys.Vec3

object SimulatedCast {
    private var hook: FishingHook? = null
    private var nextLocal = 0

    fun active(): Boolean = hook?.isRemoved == false

    fun cast(client: Minecraft, player: LocalPlayer, target: Vec3?) {
        val level = client.level ?: return
        if (active()) return
        val bobber = FishingHook(player, level, 0, 0)
        bobber.id = LOCAL_ID_BASE + (nextLocal++ and LOCAL_ID_MASK)
        if (target != null) bobber.deltaMovement = launchVelocity(bobber.position(), target)
        level.addEntity(bobber)
        hook = bobber
        level.playLocalSound(player.x, player.y, player.z, SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, THROW_VOLUME, throwPitch(level.random.nextFloat()), false)
    }

    fun reel(client: Minecraft, player: LocalPlayer) {
        val bobber = hook ?: return
        hook = null
        if (bobber.isRemoved) return
        bobber.discard()
        client.level?.playLocalSound(player.x, player.y, player.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, RETRIEVE_VOLUME, throwPitch(client.level!!.random.nextFloat()), false)
    }

    fun clear() {
        hook?.takeUnless { it.isRemoved }?.discard()
        hook = null
    }

    fun holdingArm(player: Player): HumanoidArm? =
        if (GatheringTool.kind(player.mainHandItem) == ROD) player.mainArm else null

    private fun launchVelocity(from: Vec3, to: Vec3): Vec3 {
        val offset = to.subtract(from)
        val ticks = (MIN_TICKS + offset.length() / TICKS_PER_BLOCK).coerceAtMost(MAX_TICKS.toDouble()).toInt()
        var reach = 0.0
        var carry = 1.0
        var fall = 0.0
        var speed = 0.0
        repeat(ticks) {
            reach += carry
            carry *= DRAG
            val step = speed + GRAVITY
            fall += step
            speed = step * DRAG
        }
        return Vec3(offset.x / reach, (offset.y - fall) / reach, offset.z / reach)
    }

    private fun throwPitch(roll: Float): Float = 0.4f / (roll * 0.4f + 0.8f)

    private const val ROD = "ROD"
    private const val LOCAL_ID_BASE = -1_000_000
    private const val LOCAL_ID_MASK = 0xFFFF
    private const val GRAVITY = -0.03
    private const val DRAG = 0.92
    private const val MIN_TICKS = 10.0
    private const val MAX_TICKS = 26
    private const val TICKS_PER_BLOCK = 2.0
    private const val THROW_VOLUME = 0.5f
    private const val RETRIEVE_VOLUME = 1.0f
}
