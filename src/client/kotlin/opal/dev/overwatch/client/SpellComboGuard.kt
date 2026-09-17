package opal.dev.overwatch.client

import net.minecraft.client.Minecraft

object SpellComboGuard {

    private var suspendUntilNanos = 0L
    private var wasUseDown = false

    fun tick(client: Minecraft) {
        val config = OverwatchConfig.current
        if (!config.combatSpellGuardEnabled) {
            wasUseDown = false
            return
        }
        val useDown = client.options.keyUse.isDown
        if (useDown && !wasUseDown) {
            suspendUntilNanos = System.nanoTime() + (config.combatSpellGuardMs * 1_000_000L).toLong()
        }
        wasUseDown = useDown
    }

    fun isSuspended(): Boolean {
        val config = OverwatchConfig.current
        return config.combatSpellGuardEnabled && System.nanoTime() < suspendUntilNanos
    }
}
