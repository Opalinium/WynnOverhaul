package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.Vec3

object GatherSession {
    private val NODE_LABEL = Regex("""(Woodcutting|Farming|Fishing|Mining) Lv\.? Min""")
    private val HARVEST_LABEL = Regex("""\+\d+(?:\.\d+)?\s.*\sXP\s*\[[\d.]+%\]""")
    private const val LABEL_RANGE = 6.0
    private const val MAX_NANOS = 30_000_000_000L
    private const val LEASH_SQ = 9.0

    private var toolKey: String? = null
    private var startNanos = 0L
    private var startPos: Vec3 = Vec3.ZERO
    private var keyWasDown = false
    private var completed = false
    private val harvestSeen = HashSet<Int>()

    fun active(): Boolean = toolKey != null

    fun takeCompleted(): Boolean = completed.also { completed = false }

    fun tick(client: Minecraft, player: LocalPlayer, key: String?) {
        val screenOpen = client.gui.screen() != null
        val down = !screenOpen && (client.options.keyUse.isDown || client.options.keyAttack.isDown)
        val pressed = down && !keyWasDown
        keyWasDown = down
        val current = toolKey
        if (current != null) {
            val expired = System.nanoTime() - startNanos > MAX_NANOS
            val moved = player.position().distanceToSqr(startPos) > LEASH_SQ
            if (key != current || screenOpen || expired || moved) {
                toolKey = null
            } else if (newHarvestLabel(client, player)) {
                toolKey = null
                completed = true
            }
            return
        }
        if (!pressed || key == null) return
        if (nodeNearby(client, player)) {
            toolKey = key
            startNanos = System.nanoTime()
            startPos = player.position()
            harvestSeen.clear()
            newHarvestLabel(client, player)
        }
    }

    private fun labels(client: Minecraft, player: LocalPlayer): List<Pair<Int, String>> {
        val level = client.level ?: return emptyList()
        return level.getEntitiesOfClass(Display.TextDisplay::class.java, player.boundingBox.inflate(LABEL_RANGE)).mapNotNull { entity ->
            EntityTracker.textOf(entity)?.string?.let { entity.id to it }
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
