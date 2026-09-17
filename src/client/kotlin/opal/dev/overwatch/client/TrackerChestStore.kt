package opal.dev.overwatch.client

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.world.level.storage.LevelResource
import opal.dev.overwatch.Overwatch
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ChestRecord(val label: String, val tier: Int = 0, val availableAtMillis: Long = 0L)

object TrackerChestStore {

    private val PATH = FabricLoader.getInstance().configDir.resolve("overwatch-chests.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val MAP_TYPE = object : TypeToken<MutableMap<String, MutableMap<String, ChestRecord>>>() {}.type

    private var context: String? = null
    private var dirty = false
    private var nextSaveNanos = 0L
    private var loggedLoadError = false
    private var loggedSaveError = false

    fun markDirty() {
        if (context != null) dirty = true
    }

    fun sync(client: Minecraft, chests: MutableMap<Long, ChestRecord>) {
        val key = contextKey(client) ?: return
        if (key != context) {
            if (context != null) write(chests)
            context = key
            chests.clear()
            chests.putAll(read(key))
            dirty = false
            nextSaveNanos = 0L
            return
        }
        if (dirty && System.nanoTime() >= nextSaveNanos) write(chests)
    }

    fun detach(chests: MutableMap<Long, ChestRecord>) {
        if (context != null && dirty) write(chests)
        context = null
        dirty = false
        chests.clear()
    }

    private fun write(chests: Map<Long, ChestRecord>) {
        val ctx = context ?: return
        try {
            val all = readAll()
            if (chests.isEmpty()) {
                all.remove(ctx)
            } else {
                all[ctx] = chests.entries.sortedBy { it.key }
                    .associateTo(LinkedHashMap()) { it.key.toString() to it.value }
            }
            PATH.parent?.let { Files.createDirectories(it) }
            PATH.writeText(GSON.toJson(all))
            dirty = false
            nextSaveNanos = System.nanoTime() + SAVE_INTERVAL_NANOS
        } catch (t: Throwable) {
            if (!loggedSaveError) {
                loggedSaveError = true
                Overwatch.LOGGER.error("Failed to save tracked chests", t)
            }
        }
    }

    private fun read(key: String): Map<Long, ChestRecord> {
        val raw = readAll()[key] ?: return emptyMap()
        val out = HashMap<Long, ChestRecord>(raw.size)
        for ((k, v) in raw) k.toLongOrNull()?.let { out[it] = v }
        return out
    }

    private fun readAll(): MutableMap<String, MutableMap<String, ChestRecord>> {
        return try {
            if (!PATH.exists()) mutableMapOf() else GSON.fromJson(PATH.readText(), MAP_TYPE) ?: mutableMapOf()
        } catch (t: Throwable) {
            if (!loggedLoadError) {
                loggedLoadError = true
                Overwatch.LOGGER.error("overwatch-chests.json unreadable, starting a fresh store", t)
            }
            mutableMapOf()
        }
    }

    private fun contextKey(client: Minecraft): String? {
        return try {
            val singleplayer = client.singleplayerServer
            if (singleplayer != null) {
                val dir = singleplayer.getWorldPath(LevelResource.ROOT).fileName?.toString()
                if (dir.isNullOrBlank()) null else "sp/$dir"
            } else {
                val ip = client.currentServer?.ip?.trim()?.lowercase()
                if (ip.isNullOrBlank()) null else "mp/${ip.substringBefore(':')}"
            }
        } catch (t: Throwable) {
            null
        }
    }

    private const val SAVE_INTERVAL_NANOS = 3_000_000_000L
}
