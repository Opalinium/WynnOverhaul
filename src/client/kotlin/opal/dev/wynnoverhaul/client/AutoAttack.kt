package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.ai.attributes.Attributes

object AutoAttack {
    fun tick(client: Minecraft, player: LocalPlayer, config: WynnOverhaulConfig, gate: JitteredActionGate) {
        if (SpellComboGuard.isSuspended()) return
        if (!gate.isReady(System.nanoTime(), immediateFirst = true) { nextDelayNanos(player, config) }) return

        val gameMode = client.gameMode ?: return
        SoulsCamera.onCombatAction(client)

        if (ActiveWeapon.isRanged(player)) {
            gameMode.useItem(player, InteractionHand.MAIN_HAND)
            WeaponAnimations.onSwing(player)
        } else {
            player.swing(InteractionHand.MAIN_HAND)
        }
        gate.markFired()
    }

    private fun nextDelayNanos(player: LocalPlayer, config: WynnOverhaulConfig): Long {
        val wynnRate = if (config.wynnAttackSpeed) WynnWeapons.attacksPerSecond(ActiveWeapon.current(player)) else null
        val effectiveCps = if (wynnRate != null) {
            AttackTiming.effectiveCps(wynnRate, config, exactRate = true)
        } else {
            val attacksPerSecond = player.getAttributeValue(Attributes.ATTACK_SPEED).coerceAtLeast(0.01)
            AttackTiming.effectiveCps(attacksPerSecond, config)
        }
        return (1000.0 / effectiveCps * 1_000_000L).toLong()
    }
}
