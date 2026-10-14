package opal.dev.wynnoverhaul.client

import com.google.gson.GsonBuilder
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object PinBoard {
    enum class Kind { ACTIVITY, CUSTOM, PING, RECEIVED }

    data class Pin(
        val id: String = "",
        val label: String = "",
        val x: Int = 0,
        val y: Int = 0,
        val z: Int = 0,
        val kind: Kind = Kind.CUSTOM,
        val colorArgb: Int = CUSTOM_COLOR,
        val approximate: Boolean = false,
        val expiresMillis: Long = 0L,
    )

    class Entry(val match: EntityTracker.Match, val primary: Boolean)

    private class Store(val pins: List<Pin> = emptyList(), val primary: String? = null)

    private val pinsById = LinkedHashMap<String, Pin>()
    private var primaryId: String? = null
    private var loaded = false
    private var loggedError = false
    private var revision = 0
    private var cachedRevision = -1
    private var cachedEntries: List<Entry> = emptyList()
    private var customCounter = 0
    private val path = FabricLoader.getInstance().configDir.resolve("wynnoverhaul-pins.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var cycleKey: KeyMapping

    val pins: List<Pin>
        get() {
            ensureLoaded()
            return pinsById.values.toList()
        }

    val primary: Pin?
        get() {
            ensureLoaded()
            return primaryId?.let { pinsById[it] }
        }

    fun register() {
        cycleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.wynnoverhaul.pins.cycle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, WynnOverhaulKeyCategory.CATEGORY),
        )
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("owpin")
                    .then(ClientCommands.literal("here").executes { addHere(""); 1 }
                        .then(ClientCommands.argument("label", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes { ctx ->
                            addHere(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "label")); 1
                        }))
                    .then(ClientCommands.literal("next").executes { cyclePrimary(); 1 })
                    .then(ClientCommands.literal("clear").executes { clear(); say("Pins cleared", ChatFormatting.GRAY); 1 })
                    .then(ClientCommands.literal("list").executes { list(); 1 }),
            )
        }
    }

    fun tick(client: Minecraft) {
        while (cycleKey.consumeClick()) cyclePrimary()
        val player = client.player ?: return
        if (!WynnOverhaulGate.inGame) return
        ensureLoaded()
        if (pinsById.isEmpty()) return
        val config = WynnOverhaulConfig.current
        val now = System.currentTimeMillis()
        val arriveSqr = ARRIVE_BLOCKS * ARRIVE_BLOCKS
        val gone = pinsById.values.filter { pin ->
            (pin.expiresMillis != 0L && now >= pin.expiresMillis) ||
                (config.pinAutoClear && !pin.approximate && player.distanceToSqr(pin.x + 0.5, pin.y.toDouble(), pin.z + 0.5) < arriveSqr)
        }
        for (pin in gone) remove(pin.id)
    }

    fun isPinned(id: String): Boolean {
        ensureLoaded()
        return id in pinsById
    }

    fun activityId(type: ActivityType, name: String): String = "A:${type.name}:$name"

    fun toggleActivity(activity: ActivityInfo, located: DiscoveryTracker.Located): Boolean {
        val id = activityId(activity.type, activity.name)
        if (isPinned(id)) {
            remove(id)
            return false
        }
        val label = if (located.approximate) "${activity.name} (approx.)" else activity.name
        add(Pin(id, label, located.x, located.y, located.z, Kind.ACTIVITY, activity.type.colorArgb, located.approximate))
        return true
    }

    fun add(pin: Pin) {
        ensureLoaded()
        pinsById.remove(pin.id)
        pinsById[pin.id] = pin
        while (pinsById.size > MAX_PINS) {
            val evict = pinsById.keys.firstOrNull { it != primaryId } ?: break
            pinsById.remove(evict)
        }
        if (primaryId == null || primaryId !in pinsById) primaryId = pin.id
        changed()
    }

    fun remove(id: String) {
        ensureLoaded()
        if (pinsById.remove(id) == null) return
        if (primaryId == id) primaryId = pinsById.keys.firstOrNull()
        changed()
    }

    fun setPrimary(id: String) {
        ensureLoaded()
        if (id !in pinsById || primaryId == id) return
        primaryId = id
        changed()
    }

    fun cyclePrimary() {
        ensureLoaded()
        val ids = pinsById.keys.toList()
        if (ids.isEmpty()) {
            say("No pinned waypoints", ChatFormatting.GRAY)
            return
        }
        val next = ids[(ids.indexOf(primaryId) + 1) % ids.size]
        primaryId = next
        changed()
        say("Tracking ${pinsById.getValue(next).label}", ChatFormatting.GREEN)
    }

    fun clear() {
        ensureLoaded()
        pinsById.clear()
        primaryId = null
        changed()
    }

    fun clearKind(kind: Kind) {
        ensureLoaded()
        val ids = pinsById.values.filter { it.kind == kind }.map { it.id }
        for (id in ids) remove(id)
    }

    fun entries(): List<Entry> {
        ensureLoaded()
        if (cachedRevision == revision) return cachedEntries
        cachedEntries = pinsById.values.map { pin ->
            val box = AABB(pin.x - 0.15, pin.y.toDouble(), pin.z - 0.15, pin.x + 0.15, pin.y + 1.6, pin.z + 0.15)
            val primary = pin.id == primaryId
            val label = if (primary && pinsById.size > 1) "★ ${pin.label}" else pin.label
            Entry(EntityTracker.Match(null, box, label, pin.colorArgb, true, iconFor(pin)), primary)
        }
        cachedRevision = revision
        return cachedEntries
    }

    fun nextCustomLabel(): String = "Waypoint ${++customCounter}"

    private fun iconFor(pin: Pin) = when (pin.kind) {
        Kind.ACTIVITY -> ActivityType.entries.firstOrNull { it.colorArgb == pin.colorArgb }?.let { WaypointIcons.activity(it) } ?: WaypointIcons.task
        else -> WaypointIcons.task
    }

    private fun addHere(label: String) {
        val player = Minecraft.getInstance().player ?: return
        val text = label.trim().ifEmpty { nextCustomLabel() }
        val id = "C:${System.nanoTime()}"
        add(Pin(id, text, player.blockX, player.blockY, player.blockZ, Kind.CUSTOM, CUSTOM_COLOR))
        say("Pinned $text", ChatFormatting.GREEN)
    }

    private fun list() {
        val all = pins
        if (all.isEmpty()) {
            say("No pinned waypoints", ChatFormatting.GRAY)
            return
        }
        val player = Minecraft.getInstance().player ?: return
        for (pin in all) {
            val distance = Math.sqrt(player.distanceToSqr(pin.x + 0.5, pin.y.toDouble(), pin.z + 0.5)).toInt()
            val mark = if (pin.id == primaryId) "★ " else "  "
            say("$mark${pin.label}  ${pin.x}, ${pin.y}, ${pin.z}  ${distance}m", ChatFormatting.GRAY)
        }
    }

    private fun say(text: String, color: ChatFormatting) {
        Minecraft.getInstance().player?.sendOverlayMessage(
            Component.literal("[WynnOverhaul] ").withStyle(ChatFormatting.GRAY).append(Component.literal(text).withStyle(color)),
        )
    }

    private fun changed() {
        revision++
        save()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            if (!path.exists()) return
            val store = gson.fromJson(path.readText(), Store::class.java) ?: return
            for (pin in store.pins) pinsById[pin.id] = pin
            primaryId = store.primary?.takeIf { it in pinsById } ?: pinsById.keys.firstOrNull()
            revision++
        } catch (t: Throwable) {
            logOnce("Pins unreadable, starting fresh", t)
        }
    }

    private fun save() {
        try {
            path.parent?.let { Files.createDirectories(it) }
            val persistent = pinsById.values.filter { it.kind == Kind.ACTIVITY || it.kind == Kind.CUSTOM }
            path.writeText(gson.toJson(Store(persistent, primaryId?.takeIf { id -> persistent.any { it.id == id } })))
        } catch (t: Throwable) {
            logOnce("Failed to save pins", t)
        }
    }

    private fun logOnce(message: String, t: Throwable) {
        if (loggedError) return
        loggedError = true
        WynnOverhaul.LOGGER.error(message, t)
    }

    const val CUSTOM_COLOR = 0xFFFFD866.toInt()
    const val PING_COLOR = 0xFF66E0FF.toInt()
    const val RECEIVED_COLOR = 0xFFFF9F43.toInt()
    private const val MAX_PINS = 14
    private const val ARRIVE_BLOCKS = 8.0
}
