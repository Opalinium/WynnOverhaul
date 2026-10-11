package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.Vec3

object GatherSession {
    private val NODE_LABEL = Regex("""(Woodcutting|Farming|Fishing|Mining) Lv\.? Min""")
    private val PROGRESS_LABEL = Regex("""Ⓚ\s(Fishing)\s*\n""")
    private val HARVEST_LABEL = Regex("""\+\d+(?:\.\d+)?\s.*\sXP\s*\[[\d.]+%\]""")
    private const val LABEL_RANGE = 6.0
    private const val MAX_NANOS = 30_000_000_000L
    private const val LEASH_SQ = 9.0
    private const val NODE_AIM_DROP = 0.5
    private const val FISHING_KEY = "TOOL:ROD"
    private const val PRESS_WINDOW_NANOS = 3_000_000_000L
    private const val FINISH_GRACE_NANOS = 1_000_000_000L

    private var toolKey: String? = null
    private var startNanos = 0L
    private var startPos: Vec3 = Vec3.ZERO
    private var keyWasDown = false
    private var lastPressNanos = 0L
    private var completed = false
    private var started = false
    private var progressId = -1
    private var progressGoneNanos = 0L
    private val harvestSeen = HashSet<Int>()

    fun active(): Boolean = toolKey != null

    fun fishing(): Boolean = toolKey == FISHING_KEY

    fun takeCompleted(): Boolean = completed.also { completed = false }

    fun takeStarted(): Boolean = started.also { started = false }

    fun tick(client: Minecraft, player: LocalPlayer, key: String?) {
        val screenOpen = client.gui.screen() != null
        val down = !screenOpen && (client.options.keyUse.isDown || client.options.keyAttack.isDown)
        val pressed = down && !keyWasDown
        keyWasDown = down
        val now = System.nanoTime()
        if (pressed) lastPressNanos = now
        val current = toolKey
        if (current != null) {
            val expired = now - startNanos > MAX_NANOS
            val moved = player.position().distanceToSqr(startPos) > LEASH_SQ
            if (key != current || screenOpen || expired || moved) {
                end(client, player)
            } else if (newHarvestLabel(client, player)) {
                end(client, player)
                completed = true
            } else if (current == FISHING_KEY && progressGone(client, player, now)) {
                end(client, player)
            }
            return
        }
        if (key == null) return
        if (key == FISHING_KEY) {
            if (now - lastPressNanos > PRESS_WINDOW_NANOS) return
            val progress = progressDisplay(client, player) ?: return
            begin(client, player, key)
            progressId = progress.id
            progressGoneNanos = 0L
            SimulatedCast.cast(client, player, progress.position().add(0.0, -NODE_AIM_DROP, 0.0))
            return
        }
        if (!pressed) return
        if (nodeNearby(client, player)) begin(client, player, key)
    }

    private fun begin(client: Minecraft, player: LocalPlayer, key: String) {
        toolKey = key
        started = true
        startNanos = System.nanoTime()
        startPos = player.position()
        harvestSeen.clear()
        newHarvestLabel(client, player)
    }

    private fun end(client: Minecraft, player: LocalPlayer) {
        if (toolKey == FISHING_KEY) SimulatedCast.reel(client, player)
        toolKey = null
        progressId = -1
    }

    private fun progressGone(client: Minecraft, player: LocalPlayer, now: Long): Boolean {
        if (labelEntities(client, player).any { it.first.id == progressId }) {
            progressGoneNanos = 0L
            return false
        }
        if (progressGoneNanos == 0L) progressGoneNanos = now
        return now - progressGoneNanos > FINISH_GRACE_NANOS
    }

    private fun progressDisplay(client: Minecraft, player: LocalPlayer): Display.TextDisplay? =
        labelEntities(client, player)
            .filter { PROGRESS_LABEL.containsMatchIn(it.second) && TextClean.hasPrivateUse(it.second) }
            .minByOrNull { it.first.distanceToSqr(player) }
            ?.first

    private fun labels(client: Minecraft, player: LocalPlayer): List<Pair<Int, String>> =
        labelEntities(client, player).map { it.first.id to it.second }

    private fun labelEntities(client: Minecraft, player: LocalPlayer): List<Pair<Display.TextDisplay, String>> {
        val level = client.level ?: return emptyList()
        return level.getEntitiesOfClass(Display.TextDisplay::class.java, player.boundingBox.inflate(LABEL_RANGE)).mapNotNull { entity ->
            EntityTracker.textOf(entity)?.string?.let { entity to it }
        }
    }

    private fun nodeNearby(client: Minecraft, player: LocalPlayer): Boolean =
        labels(client, player).any { NODE_LABEL.containsMatchIn(it.second) }

    private fun newHarvestLabel(client: Minecraft, player: LocalPlayer): Boolean {
        var fresh = false
        for ((id, text) in labels(client, player)) {
            if (HARVEST_LABEL.containsMatchIn(text) && harvestSeen.add(id)) fresh = true
        }
        return fresh
    }
}
