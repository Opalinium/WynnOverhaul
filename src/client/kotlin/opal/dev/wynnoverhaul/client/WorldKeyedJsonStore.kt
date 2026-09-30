package opal.dev.wynnoverhaul.client

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import opal.dev.wynnoverhaul.WynnOverhaul
import java.lang.reflect.Type
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class WorldKeyedJsonStore<T>(fileName: String, private val mapType: Type, private val label: String) {
    private val path = FabricLoader.getInstance().configDir.resolve(fileName)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var context: String? = null
    private var dirty = false
    private var nextSaveNanos = 0L
    private var loggedLoadError = false
    private var loggedSaveError = false

    fun markDirty() {
        if (context != null) dirty = true
    }

    fun sync(client: Minecraft, records: MutableMap<Long, T>) {
        val key = WorldContext.key(client) ?: return
        if (key != context) {
            if (context != null) write(records)
            context = key
            records.clear()
            records.putAll(read(key))
            dirty = false
            nextSaveNanos = 0L
            return
        }
        if (dirty && System.nanoTime() >= nextSaveNanos) write(records)
    }

    fun detach(records: MutableMap<Long, T>) {
        if (context != null && dirty) write(records)
        context = null
        dirty = false
        records.clear()
    }

    private fun write(records: Map<Long, T>) {
        val ctx = context ?: return
        try {
            val all = readAll()
            if (records.isEmpty()) {
                all.remove(ctx)
            } else {
                all[ctx] = records.entries.sortedBy { it.key }
                    .associateTo(LinkedHashMap()) { it.key.toString() to it.value }
            }
            path.parent?.let { Files.createDirectories(it) }
            path.writeText(gson.toJson(all))
            dirty = false
            nextSaveNanos = System.nanoTime() + SAVE_INTERVAL_NANOS
        } catch (t: Throwable) {
            if (!loggedSaveError) {
                loggedSaveError = true
                WynnOverhaul.LOGGER.error("Failed to save $label", t)
            }
        }
    }

    private fun read(key: String): Map<Long, T> {
        val raw = readAll()[key] ?: return emptyMap()
        val out = HashMap<Long, T>(raw.size)
        for ((k, v) in raw) k.toLongOrNull()?.let { out[it] = v }
        return out
    }

    private fun readAll(): MutableMap<String, MutableMap<String, T>> {
        return try {
            if (!path.exists()) mutableMapOf() else gson.fromJson(path.readText(), mapType) ?: mutableMapOf()
        } catch (t: Throwable) {
            if (!loggedLoadError) {
                loggedLoadError = true
                WynnOverhaul.LOGGER.error("$label store unreadable, starting fresh", t)
            }
            mutableMapOf()
        }
    }

    private companion object {
        const val SAVE_INTERVAL_NANOS = 3_000_000_000L
    }
}
