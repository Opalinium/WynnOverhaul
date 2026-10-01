package opal.dev.wynnoverhaul.client

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.mixin.client.ItemDisplayAccessor
import kotlin.math.abs
import kotlin.math.hypot

object WynnClassBuffTracker {
    data class Buff(
        val label: String,
        val color: Int,
        val count: Int = 0,
        val broken: Int = 0,
        val seconds: Int = -1,
        val fraction: Float = 1f,
        val note: String = "",
    )

    @Volatile
    var buffs: List<Buff> = emptyList()
        private set

    private class Casted(
        val label: String,
        val classKey: String,
        val spell: String?,
        val groups: Set<String>,
        val color: Int,
    ) {
        val entityIds = HashSet<Int>()
        val pending = HashSet<Int>()
        var registerAtMillis = 0L
        val missingSince = HashMap<Int, Long>()
        var activeGroup: String? = null
        var pendingGroup: String? = null
        val active: Boolean get() = entityIds.isNotEmpty()
    }

    private val mantle = Casted("Mantle", "warrior", "warscream", setOf(MANTLE_GROUP), 0xFF5B7FE0.toInt())
    private val brokenMantle = Casted("Broken Mantle", "warrior", "warscream", setOf(BROKEN_GROUP), 0xFFE05B5B.toInt())
    private val guardianAngels = Casted("Guardian Angels", "archer", "arrowshield", setOf(GA_GROUP, GA_ULT_GROUP), 0xFFE8D26A.toInt())
    private val arrowShield = Casted("Arrow Shield", "archer", "arrowshield", setOf(ARROW_GROUP), 0xFF7CC1E8.toInt())
    private val judrajim = Casted("Judrajim", "mage", "heal", setOf(JUDRAJIM_GROUP), 0xFFB48CE8.toInt())
    private val casted = listOf(mantle, brokenMantle, guardianAngels, arrowShield, judrajim)

    private val recent = HashMap<Int, Long>()
    private val claimed = HashSet<Int>()
    private val seen = HashSet<Int>()
    private val maxSeconds = HashMap<String, Int>()

    private val modelGroups: Map<Float, String> by lazy { loadGroups() }

    fun tick(client: Minecraft) {
        if (!WynnOverhaulConfig.current.classBuffHudEnabled) {
            if (buffs.isNotEmpty()) clear()
            return
        }
        val player = client.player ?: return
        val level = client.level ?: return
        val now = System.currentTimeMillis()
        val classKey = WynnClassTracker.classKey

        if (classKey != null) {
            scanItemDisplays(client, now)
            for (type in casted) maintain(type, now) { level.getEntity(it) != null }
        }

        val out = ArrayList<Buff>()
        if (mantle.active || brokenMantle.active) {
            out.add(Buff("Mantle", mantle.color, mantle.entityIds.size, brokenMantle.entityIds.size))
        }
        if (arrowShield.active) out.add(Buff("Arrow Shield", arrowShield.color, arrowShield.entityIds.size))
        if (guardianAngels.active) {
            val note = if (guardianAngels.activeGroup == GA_ULT_GROUP) "Ascension" else ""
            out.add(Buff("Guardian Angels", guardianAngels.color, guardianAngels.entityIds.size, note = note))
        }
        if (judrajim.active) out.add(Buff("Judrajim", judrajim.color, note = "Active"))
        out.addAll(scanLabels(client, player.gameProfile.name))
        buffs = out
    }

    fun clear() {
        for (type in casted) {
            type.entityIds.clear()
            type.pending.clear()
            type.registerAtMillis = 0L
            type.missingSince.clear()
            type.activeGroup = null
            type.pendingGroup = null
        }
        recent.clear()
        claimed.clear()
        seen.clear()
        maxSeconds.clear()
        buffs = emptyList()
    }

    private fun scanItemDisplays(client: Minecraft, now: Long) {
        val player = client.player ?: return
        val level = client.level ?: return
        val box = AABB(
            player.x - ITEM_SCAN_RADIUS, player.y - ITEM_SCAN_RADIUS, player.z - ITEM_SCAN_RADIUS,
            player.x + ITEM_SCAN_RADIUS, player.y + ITEM_SCAN_RADIUS, player.z + ITEM_SCAN_RADIUS,
        )
        val present = HashSet<Int>()
        for (entity in level.getEntities(player, box) { it is Display.ItemDisplay }) {
            present.add(entity.id)
            if (seen.add(entity.id)) recent[entity.id] = now
        }
        seen.retainAll(present)
        recent.entries.removeIf { now - it.value > RECENT_MILLIS || it.key !in present }
        claimed.retainAll(present)

        val cast = WynnSpellTracker.lastCast
        val castKey = cast?.name?.lowercase()?.filter { it.isLetter() }
        val inCastWindow = cast != null && now - cast.atMillis < CAST_WINDOW_MILLIS

        for (id in recent.keys.toList()) {
            val entity = level.getEntity(id) ?: continue
            process(entity, inCastWindow, castKey, now)
        }
    }

    private fun process(entity: Entity, inCastWindow: Boolean, castKey: String?, now: Long) {
        if (entity.id in claimed) return
        if (entity.passengers.isEmpty()) return
        val modelIds = modelIdsOf(entity)
        if (modelIds.isEmpty()) return
        val groups = modelIds.map { modelGroups[it] }
        for (type in casted) {
            if (type.classKey != WynnClassTracker.classKey) continue
            if (outsideProximity(entity)) continue
            if (inCastWindow) {
                if (type.spell != castKey) continue
            } else if (!allowsOutOfWindow(type, groups)) {
                continue
            }
            if (!verifyGroups(type, groups)) continue
            if (blocked(type)) continue
            claimed.add(entity.id)
            onMatched(type, entity.id, groups, now)
            return
        }
    }

    private fun allowsOutOfWindow(type: Casted, groups: List<String?>): Boolean = when (type) {
        brokenMantle -> mantle.active && verifyGroups(type, groups)
        guardianAngels -> {
            val distinct = groups.toSet()
            guardianAngels.active && distinct.size == 1 && distinct.first() in guardianAngels.groups &&
                distinct.first() != guardianAngels.activeGroup
        }
        else -> false
    }

    private fun verifyGroups(type: Casted, groups: List<String?>): Boolean {
        if (groups.isEmpty()) return false
        if (type === guardianAngels) {
            val distinct = groups.toSet()
            return distinct.size == 1 && distinct.first() in type.groups
        }
        return groups.all { it != null && it in type.groups }
    }

    private fun blocked(candidate: Casted): Boolean {
        val rival = when (candidate) {
            guardianAngels -> arrowShield
            arrowShield -> guardianAngels
            else -> return false
        }
        return rival.active
    }

    private fun onMatched(type: Casted, id: Int, groups: List<String?>, now: Long) {
        if (type === brokenMantle) {
            type.entityIds.add(id)
            return
        }
        type.pending.add(id)
        type.pendingGroup = groups.firstOrNull { it != null }
        if (type.registerAtMillis == 0L) type.registerAtMillis = now + REGISTER_DELAY_MILLIS
    }

    private fun maintain(type: Casted, now: Long, exists: (Int) -> Boolean) {
        if (type.registerAtMillis != 0L && now >= type.registerAtMillis) {
            type.registerAtMillis = 0L
            if (type.pending.isNotEmpty()) {
                type.entityIds.clear()
                type.entityIds.addAll(type.pending)
                type.pending.clear()
                type.activeGroup = type.pendingGroup
            }
        }
        val grace = if (type === judrajim) JUDRAJIM_LINGER_MILLIS else 0L
        type.entityIds.removeIf { id ->
            if (exists(id)) {
                type.missingSince.remove(id)
                false
            } else {
                val since = type.missingSince.getOrPut(id) { now }
                now - since >= grace
            }
        }
        type.missingSince.keys.retainAll(type.entityIds)
        type.pending.removeIf { !exists(it) }
        if (type.entityIds.isEmpty() && type.pending.isEmpty()) type.activeGroup = null
    }
    private fun modelIdsOf(entity: Entity): List<Float> {
        val out = ArrayList<Float>()
        for (passenger in entity.passengers) {
            if (passenger !is Display.ItemDisplay) continue
            val stack = (passenger as ItemDisplayAccessor).`wynnoverhaul$getItemStack`()
            if (!stack.`is`(Items.OAK_BOAT)) continue
            val data = stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: continue
            out.addAll(data.floats())
        }
        return out
    }

    private fun outsideProximity(entity: Entity): Boolean {
        val player = Minecraft.getInstance().player ?: return true
        val velocity = player.deltaMovement
        val horizontal = hypot(entity.x - player.x, entity.z - player.z)
        val vertical = abs(entity.y - player.y)
        val horizontalLimit = 3.0 + hypot(velocity.x, velocity.z) * 4.5
        val verticalLimit = 4.5 + abs(velocity.y) * 4.5
        return horizontal > horizontalLimit || vertical > verticalLimit
    }

    private fun scanLabels(client: Minecraft, playerName: String): List<Buff> {
        val player = client.player ?: return emptyList()
        val level = client.level ?: return emptyList()
        val box = AABB(
            player.x - LABEL_SCAN_RADIUS, player.y - LABEL_SCAN_RADIUS, player.z - LABEL_SCAN_RADIUS,
            player.x + LABEL_SCAN_RADIUS, player.y + LABEL_SCAN_RADIUS, player.z + LABEL_SCAN_RADIUS,
        )
        class Group(var count: Int = 0, var seconds: Int = 0, var note: String = "")
        val groups = LinkedHashMap<String, Group>()
        for (entity in level.getEntities(player, box) { it is Display.TextDisplay }) {
            val raw = (entity as Display.TextDisplay).text.string
            val beast = BEAST.matchEntire(raw)
            if (beast != null) {
                if (beast.groupValues[1] != playerName) continue
                val group = groups.getOrPut(beast.groupValues[2]) { Group() }
                group.count++
                group.seconds = maxOf(group.seconds, beast.groupValues[3].toIntOrNull() ?: 0)
                continue
            }
            val puppet = PUPPET.matchEntire(raw) ?: continue
            if (puppet.groups["player"]?.value != playerName) continue
            val group = groups.getOrPut(puppet.groups["type"]!!.value) { Group() }
            group.count++
            group.seconds = maxOf(group.seconds, puppet.groups["seconds"]?.value?.toIntOrNull() ?: 0)
            val invigorate = puppet.groups["invigorate"]?.value
            if (invigorate != null) group.note = "Invigorate ${invigorate}s"
        }
        val live = groups.keys.toSet()
        maxSeconds.keys.retainAll(live)
        return groups.map { (label, group) ->
            val max = maxOf(maxSeconds[label] ?: 0, group.seconds)
            maxSeconds[label] = max
            val fraction = if (max <= 0) 1f else (group.seconds.toFloat() / max).coerceIn(0f, 1f)
            Buff(label, labelColor(label), group.count, seconds = group.seconds, fraction = fraction, note = group.note)
        }
    }

    private fun labelColor(label: String): Int = when (label) {
        "Crow" -> 0xFF8E8E9A.toInt()
        "Hound" -> 0xFFC99A5B.toInt()
        "Snake" -> 0xFF6FBF73.toInt()
        "Puppet" -> 0xFF4CB8A5.toInt()
        "Remnant" -> 0xFF8FA0E0.toInt()
        else -> 0xFFC97A4C.toInt()
    }

    private fun loadGroups(): Map<Float, String> {
        val out = HashMap<Float, String>()
        runCatching {
            val stream = WynnClassBuffTracker::class.java.getResourceAsStream("/assets/wynnoverhaul/item_display_models.json")
                ?: return out
            stream.reader().use { reader ->
                for ((group, array) in JsonParser.parseReader(reader).asJsonObject.entrySet()) {
                    for (value in array.asJsonArray) out[value.asFloat] = group
                }
            }
        }.onFailure { WynnOverhaul.LOGGER.warn("Class buff model data failed to load", it) }
        return out
    }

    private val BEAST = Regex("""^(\S+)(?:'s|') (Crow|Hound|Snake)\n(\d+)s""")
    private val PUPPET = Regex(
        """^(?<player>\S+)(?:'s|') (?<type>Puppet|Remnant|Patchwork Abomination)\n(?: (?<invigorate>\d+)s )?(?: (?<friendlyFire>\d+)s )? (?<seconds>\d+)s""",
    )

    private const val MANTLE_GROUP = "Mantle"
    private const val BROKEN_GROUP = "Broken Mantle"
    private const val GA_GROUP = "Guardian Angels"
    private const val GA_ULT_GROUP = "Angelic Ascension Guardian Angels"
    private const val ARROW_GROUP = "Arrow Shield"
    private const val JUDRAJIM_GROUP = "Judrajim"
    private const val ITEM_SCAN_RADIUS = 12.0
    private const val LABEL_SCAN_RADIUS = 48.0
    private const val RECENT_MILLIS = 300L
    private const val CAST_WINDOW_MILLIS = 250L
    private const val REGISTER_DELAY_MILLIS = 200L
    private const val JUDRAJIM_LINGER_MILLIS = 1000L
}
