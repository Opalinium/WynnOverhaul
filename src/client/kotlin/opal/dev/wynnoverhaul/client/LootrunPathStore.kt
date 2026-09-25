package opal.dev.wynnoverhaul.client

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class LootrunPathPoint(val x: Double, val y: Double, val z: Double)

data class LootrunPath(val name: String, val dimension: String, val points: List<LootrunPathPoint>, val savedAtMillis: Long)

object LootrunPathStore {
    private val PATH = FabricLoader.getInstance().configDir.resolve("wynnoverhaul-lootrun-paths.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val LIST_TYPE = object : TypeToken<MutableList<LootrunPath>>() {}.type

    private var loggedError = false

    fun list(): List<LootrunPath> = read().sortedByDescending { it.savedAtMillis }

    fun save(path: LootrunPath) {
        val all = read().toMutableList()
        all.removeAll { it.name == path.name }
        all.add(path)
        write(all)
    }

    fun delete(name: String) {
        val all = read().toMutableList()
        if (all.removeAll { it.name == name }) write(all)
    }

    fun rename(oldName: String, newName: String) {
        val all = read().toMutableList()
        val index = all.indexOfFirst { it.name == oldName }
        if (index < 0) return
        all[index] = all[index].copy(name = newName)
        write(all)
    }

    private fun read(): List<LootrunPath> = try {
        if (!PATH.exists()) emptyList() else GSON.fromJson(PATH.readText(), LIST_TYPE) ?: emptyList()
    } catch (t: Throwable) {
        if (!loggedError) {
            loggedError = true
            WynnOverhaul.LOGGER.error("wynnoverhaul-lootrun-paths.json unreadable, starting fresh", t)
        }
        emptyList()
    }

    private fun write(all: List<LootrunPath>) {
        try {
            PATH.parent?.let { Files.createDirectories(it) }
            PATH.writeText(GSON.toJson(all))
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("Failed to save lootrun paths", t)
        }
    }
}
