package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import opal.dev.overwatch.mixin.client.TextDisplayAccessor

object TownNpcTracker {
    private class Role(val pattern: Regex, val icon: () -> ItemStack)

    private val roles = listOf(
        Role(Regex("""blacksmith""", RegexOption.IGNORE_CASE)) { ItemStack(Items.ANVIL) },
        Role(Regex("""upgrader""", RegexOption.IGNORE_CASE)) { ItemStack(Items.SMITHING_TABLE) },
        Role(Regex("""identifier""", RegexOption.IGNORE_CASE)) { ItemStack(Items.SPYGLASS) },
        Role(Regex("""powder master""", RegexOption.IGNORE_CASE)) { ItemStack(Items.GUNPOWDER) },
        Role(Regex("""\bbank(er)?\b""", RegexOption.IGNORE_CASE)) { ItemStack(Items.ENDER_CHEST) },
        Role(Regex("""merchant""", RegexOption.IGNORE_CASE)) { ItemStack(Items.EMERALD) },
    )

    private val levelTag = Regex("""\bLv\.?\s*\d+""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")
    private val icons = HashMap<Role, ItemStack>()

    @Volatile
    var current: List<EntityTracker.Match> = emptyList()
        private set

    private var tickCounter = 0

    private class Sample(val x: Double, val y: Double, val z: Double, val stable: Int)

    private val samples = HashMap<Int, Sample>()

    fun tick(client: Minecraft) {
        val config = OverwatchConfig.current
        val level = client.level
        val player = client.player
        if (!config.townNpcMarkersEnabled || !OverwatchGate.inGame || level == null || player == null) {
            if (current.isNotEmpty()) current = emptyList()
            return
        }
        if (++tickCounter % SCAN_INTERVAL != 0) {
            if (current.any { it.anchor?.isAlive == false }) current = current.filter { it.anchor?.isAlive != false }
            return
        }
        val out = ArrayList<EntityTracker.Match>()
        val seen = HashMap<Role, MutableList<Entity>>()
        val next = HashMap<Int, Sample>()
        val nearby = level.getEntities(player, player.boundingBox.inflate(RANGE)) { it.isAlive }
        val npcTags = nearby.filter { entity -> nameOf(entity)?.let { isGlyphOnly(it) } == true }
        for (entity in nearby) {
            if (player.distanceToSqr(entity) > RANGE * RANGE) continue
            val name = nameOf(entity) ?: continue
            if (entity.tickCount < MIN_AGE_TICKS) continue
            val previous = samples[entity.id]
            val moved = previous == null || previous.x != entity.x || previous.y != entity.y || previous.z != entity.z
            val stable = if (moved) 0 else previous.stable + 1
            next[entity.id] = Sample(entity.x, entity.y, entity.z, stable)
            if (stable < MIN_STABLE_SCANS) continue
            if (levelTag.containsMatchIn(name)) continue
            if (!hasNpcTag(entity, name, npcTags)) continue
            val role = roles.firstOrNull { it.pattern.containsMatchIn(name) } ?: continue
            val label = TextClean.clean(name).replace(spaces, " ")
            if (label.isBlank() || label.length > MAX_LABEL) continue
            val list = seen.getOrPut(role) { ArrayList() }
            if (list.any { it.distanceToSqr(entity) < DEDUP_SQR }) continue
            list.add(entity)
            out.add(EntityTracker.Match(entity, null, label, COLOR, true, icons.getOrPut(role) { role.icon() }))
        }
        samples.clear()
        samples.putAll(next)
        current = out
    }

    fun clear() {
        current = emptyList()
        samples.clear()
    }

    private fun isGlyphOnly(raw: String): Boolean =
        raw.any { !it.isWhitespace() } && TextClean.clean(raw).isEmpty()

    private fun hasNpcTag(entity: Entity, name: String, npcTags: List<Entity>): Boolean {
        if (name.lineSequence().any { isGlyphOnly(it) }) return true
        return npcTags.any { tag ->
            val dx = tag.x - entity.x
            val dz = tag.z - entity.z
            dx * dx + dz * dz < TAG_RADIUS_SQR && kotlin.math.abs(tag.y - entity.y) < TAG_HEIGHT
        }
    }

    private fun nameOf(entity: Entity): String? {
        entity.customName?.string?.takeIf { it.isNotBlank() }?.let { return it }
        if (entity is Display.TextDisplay) {
            val text = runCatching { (entity as TextDisplayAccessor).`overwatch$getText`() }.getOrNull()
            return text?.string?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private const val SCAN_INTERVAL = 10
    private const val MIN_AGE_TICKS = 40
    private const val MIN_STABLE_SCANS = 2
    private const val TAG_RADIUS_SQR = 1.0
    private const val TAG_HEIGHT = 1.5
    private const val RANGE = 48.0
    private const val DEDUP_SQR = 4.0
    private const val MAX_LABEL = 40
    private const val COLOR = 0xFFD8C27A.toInt()
}
