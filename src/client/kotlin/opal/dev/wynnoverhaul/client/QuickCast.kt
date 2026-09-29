package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand

object QuickCast {
    private data class Queued(val atNanos: Long, val use: Boolean)

    private val queue = ArrayDeque<Queued>()
    private val keys = ArrayList<KeyMapping>()

    fun init() {
        repeat(WynnSpellSegments.SLOT_COMBOS.size) { slot ->
            keys.add(
                KeyMappingHelper.registerKeyMapping(
                    KeyMapping(
                        "key.wynnoverhaul.quick_cast_${slot + 1}",
                        InputConstants.Type.KEYSYM,
                        InputConstants.UNKNOWN.value,
                        WynnOverhaulKeyCategory.CATEGORY,
                    ),
                ),
            )
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    fun tick(client: Minecraft) {
        if (WynnOverhaulGate.inGame) WynnClassTracker.refresh(client)
        if (!WynnOverhaulConfig.current.quickCastEnabled || !WynnOverhaulGate.inGame) {
            queue.clear()
            return
        }
        val player = client.player ?: return
        if (client.gui.screen() != null) {
            queue.clear()
            return
        }
        if (queue.isEmpty()) {
            var slot = -1
            keys.forEachIndexed { i, key ->
                while (key.consumeClick()) {
                    if (slot < 0) slot = i
                }
            }
            if (slot >= 0) enqueue(slot)
        }
        val now = System.nanoTime()
        while (queue.isNotEmpty() && queue.first().atNanos <= now) {
            val next = queue.removeFirst()
            SpellComboGuard.registerEdge(client, next.use, !next.use)
            if (next.use) {
                client.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
            } else {
                player.swing(InteractionHand.MAIN_HAND)
            }
        }
    }

    private fun enqueue(slot: Int) {
        val now = System.nanoTime()
        combos()[slot].forEachIndexed { i, use ->
            queue.add(Queued(now + i * CLICK_GAP_NANOS, use))
        }
    }

    private fun combos(): List<List<Boolean>> =
        if (WynnClassTracker.family == WynnClassTracker.Family.LEFT_FIRST) {
            WynnSpellSegments.SLOT_COMBOS_LEFT
        } else {
            WynnSpellSegments.SLOT_COMBOS
        }

    fun slotCombo(slot: Int): List<Boolean> = combos()[slot]

    fun keyLabel(slot: Int): String? {
        val key = keys.getOrNull(slot) ?: return null
        if (key.isUnbound) return null
        return prettifyKey(key.saveString())
    }

    private fun prettifyKey(saved: String): String {
        val tail = saved.substringAfterLast('.')
        if (saved.startsWith("key.mouse")) {
            return when (tail.lowercase()) {
                "left", "button0" -> "LMB"
                "middle", "button2" -> "MMB"
                "right", "button1" -> "RMB"
                else -> "M" + tail.uppercase()
            }
        }
        return tail.uppercase()
    }

    private const val CLICK_GAP_NANOS = 130_000_000L
}
