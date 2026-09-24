package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

object RaytraceAttack {

    fun tryAttack(
        client: Minecraft,
        player: LocalPlayer,
        config: OverwatchConfig,
        gate: JitteredActionGate,
        immediateFirst: Boolean = true,
    ): Boolean {
        if (SpellComboGuard.isSuspended()) return false

        val now = System.nanoTime()
        if (!gate.isReady(now, immediateFirst = immediateFirst) { nextDelayNanos(player, config) }) return false

        val gameMode = client.gameMode ?: return false
        SoulsCamera.onCombatAction(client)

        if (config.wynnCombatEnabled) {
            player.swing(InteractionHand.MAIN_HAND)
            gate.markFired()
            return true
        }

        val crosshair = (client.hitResult as? EntityHitResult)?.entity
        val entity: Entity? = if (crosshair is LivingEntity && crosshair.isAlive) crosshair else null

        if (config.requireEntityTarget && entity !is LivingEntity) {
            return false
        }

        if (entity is Player) {
            val real = PlayerIdentity.isReal(client, entity)
            if (real && config.ignorePlayers) return false
            if (!real && config.ignoreFakePlayers) return false
        }

        if (entity != null) {
            gameMode.attack(player, entity)
            player.swing(InteractionHand.MAIN_HAND)
        } else if (!config.requireEntityTarget) {
            player.swing(InteractionHand.MAIN_HAND)
        }
        gate.markFired()
        return true
    }

    private fun nextDelayNanos(player: LocalPlayer, config: OverwatchConfig): Long {
        val wynnRate = if (config.wynnAttackSpeed) WynnWeapons.attacksPerSecond(player.mainHandItem) else null
        val effectiveCps = if (wynnRate != null) {
            AttackTiming.effectiveCps(wynnRate, config, exactRate = true)
        } else {
            val attacksPerSecond = player.getAttributeValue(Attributes.ATTACK_SPEED).coerceAtLeast(0.01)
            AttackTiming.effectiveCps(attacksPerSecond, config)
        }
        val delayMs = 1000.0 / effectiveCps
        return (delayMs * 1_000_000L).toLong()
    }
}
