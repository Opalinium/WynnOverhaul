package opal.dev.overwatch.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import opal.dev.overwatch.Overwatch

object WynncraftQuests {

    data class Quest(
        val name: String,
        val combatLevel: Int,
        val miningLevel: Int,
        val woodcuttingLevel: Int,
        val farmingLevel: Int,
        val fishingLevel: Int,
        val length: String,
        val province: String,
        val location: String,
        val npc: String,
        val requiredQuest: String,
        val requiredItem: String,
        val rewards: List<String>,
        val emeralds: Int,
        val experience: Int,
        val type: String,
        val tags: String,
    )

    val all: List<Quest> by lazy { load() }

    fun search(query: String): List<Quest> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun findTracked(trackedName: String): Quest? {
        if (trackedName.isBlank()) return null
        all.firstOrNull { it.name.equals(trackedName, ignoreCase = true) }?.let { return it }
        return all.firstOrNull { trackedName.contains(it.name, ignoreCase = true) || it.name.contains(trackedName, ignoreCase = true) }
    }

    private fun load(): List<Quest> {
        return try {
            val stream = WynncraftQuests::class.java.getResourceAsStream("/assets/overwatch/quests.json")
                ?: return emptyList()
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val type = object : TypeToken<List<Quest>>() {}.type
                Gson().fromJson<List<Quest>>(reader, type) ?: emptyList()
            }
        } catch (t: Throwable) {
            Overwatch.LOGGER.error("Failed to load bundled quest reference data", t)
            emptyList()
        }
    }
}
