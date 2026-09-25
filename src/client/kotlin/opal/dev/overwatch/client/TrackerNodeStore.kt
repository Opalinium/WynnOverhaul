package opal.dev.overwatch.client

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import opal.dev.overwatch.Overwatch
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class NodeRecord(val label: String, val profession: String = "", val availableAtMillis: Long = 0L)

object TrackerNodeStore {
    private val PATH = FabricLoader.getInstance().configDir.resolve("overwatch-nodes.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val MAP_TYPE = object : TypeToken<MutableMap<String, MutableMap<String, NodeRecord>>>() {}.type

    private var context: String? = null
    private var dirty = false
    private var nextSaveNanos = 0L
    private var loggedLoadError = false
    private var loggedSaveError = false

    fun markDirty() {
        if (context != null) dirty = true
    }

    fun sync(client: Minecraft, nodes: MutableMap<Long, NodeRecord>) {
        val key = WorldContext.key(client) ?: return
        if (key != context) {
            if (context != null) write(nodes)
            context = key
            nodes.clear()
            nodes.putAll(read(key))
            dirty = false
            nextSaveNanos = 0L
            return
        }
        if (dirty && System.nanoTime() >= nextSaveNanos) write(nodes)
    }

    fun detach(nodes: MutableMap<Long, NodeRecord>) {
        if (context != null && dirty) write(nodes)
        context = null
        dirty = false
        nodes.clear()
    }

    private fun write(nodes: Map<Long, NodeRecord>) {
        val ctx = context ?: return
        try {
            val all = readAll()
            if (nodes.isEmpty()) {
                all.remove(ctx)
            } else {
                all[ctx] = nodes.entries.sortedBy { it.key }
                    .associateTo(LinkedHashMap()) { it.key.toString() to it.value }
            }
            PATH.parent?.let { Files.createDirectories(it) }
            PATH.writeText(GSON.toJson(all))
            dirty = false
            nextSaveNanos = System.nanoTime() + SAVE_INTERVAL_NANOS
        } catch (t: Throwable) {
            if (!loggedSaveError) {
                loggedSaveError = true
                Overwatch.LOGGER.error("Failed to save tracked gathering nodes", t)
            }
        }
    }

    private fun read(key: String): Map<Long, NodeRecord> {
        val raw = readAll()[key] ?: return emptyMap()
        val out = HashMap<Long, NodeRecord>(raw.size)
        for ((k, v) in raw) k.toLongOrNull()?.let { out[it] = v }
        return out
    }

    private fun readAll(): MutableMap<String, MutableMap<String, NodeRecord>> {
        return try {
            if (!PATH.exists()) mutableMapOf() else GSON.fromJson(PATH.readText(), MAP_TYPE) ?: mutableMapOf()
        } catch (t: Throwable) {
            if (!loggedLoadError) {
                loggedLoadError = true
                Overwatch.LOGGER.error("overwatch-nodes.json unreadable, starting a fresh store", t)
            }
            mutableMapOf()
        }
    }

    private const val SAVE_INTERVAL_NANOS = 3_000_000_000L
}
