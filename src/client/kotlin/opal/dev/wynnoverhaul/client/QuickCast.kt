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
    private val pending = ArrayDeque<Int>()
    private val keys = ArrayList<KeyMapping>()
    private var chainAnchorNanos = Long.MIN_VALUE / 2

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
            pending.clear()
            return
        }
        val player = client.player ?: return
        if (client.gui.screen() != null) {
            queue.clear()
            pending.clear()
            return
        }
        keys.forEachIndexed { i, key ->
            while (key.consumeClick()) {
                val inFlight = pending.size + if (queue.isNotEmpty()) 1 else 0
                if (inFlight < MAX_QUEUED) pending.addLast(i)
            }
        }
        if (queue.isEmpty() && pending.isNotEmpty()) enqueue(pending.removeFirst())

        val now = System.nanoTime()
        while (queue.isNotEmpty() && queue.first().atNanos <= now) {
            val next = queue.removeFirst()
            SoulsCamera.onCombatAction(client)
            if (next.use) {
                client.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
            } else {
                player.swing(InteractionHand.MAIN_HAND)
            }
            if (queue.isEmpty() && pending.isNotEmpty()) enqueue(pending.removeFirst())
        }
    }

    private fun enqueue(slot: Int) {
        val now = System.nanoTime()
        val start = maxOf(now, chainAnchorNanos + CLICK_GAP_NANOS)
        val combo = combos()[slot]
        combo.forEachIndexed { i, use ->
            val at = start + i * CLICK_GAP_NANOS
            queue.add(Queued(at, use))
            chainAnchorNanos = at
        }
        SpellComboGuard.suspendFor((start - now) + combo.size * CLICK_GAP_NANOS)
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
    private const val MAX_QUEUED = 3
}
