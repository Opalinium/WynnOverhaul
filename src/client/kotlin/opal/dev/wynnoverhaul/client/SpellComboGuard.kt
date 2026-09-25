package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft

object SpellComboGuard {
    private const val COMBO_LENGTH = 3
    private const val TAIL_NANOS = 400_000_000L

    private var suspendUntilNanos = 0L
    private var inputs = 0
    private var wasUseDown = false
    private var wasAttackDown = false

    fun tick(client: Minecraft) {
        val config = WynnOverhaulConfig.current
        val useDown = client.options.keyUse.isDown
        val attackDown = client.options.keyAttack.isDown
        val useEdge = useDown && !wasUseDown
        val attackEdge = attackDown && !wasAttackDown
        wasUseDown = useDown
        wasAttackDown = attackDown
        if (!config.combatSpellGuardEnabled) {
            inputs = 0
            return
        }
        if (!useEdge && !attackEdge) return

        val now = System.nanoTime()
        val active = now < suspendUntilNanos
        if (!active) inputs = 0

        val counts = if (active) inputs < COMBO_LENGTH else if (startsWithLeft(client)) attackEdge else useEdge
        if (!counts) return

        inputs++
        val window = (config.combatSpellGuardMs * 1_000_000L).toLong()
        suspendUntilNanos = now + if (inputs >= COMBO_LENGTH) minOf(window, TAIL_NANOS) else window
    }

    @JvmStatic
    fun beforeClick(client: Minecraft, use: Boolean) {
        val player = client.player ?: return
        if (client.level == null || !WynnOverhaulGate.inGame) return
        tick(client)
        if (use && !isSuspended() && ActiveWeapon.isRanged(player)) WeaponAnimations.onSwing(player)
    }

    fun isSuspended(): Boolean {
        val config = WynnOverhaulConfig.current
        return config.combatSpellGuardEnabled && System.nanoTime() < suspendUntilNanos
    }

    private fun startsWithLeft(client: Minecraft): Boolean {
        val player = client.player ?: return false
        return ActiveWeapon.isRanged(player)
    }
}
