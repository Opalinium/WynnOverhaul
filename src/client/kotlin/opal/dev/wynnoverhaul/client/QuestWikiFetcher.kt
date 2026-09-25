package opal.dev.wynnoverhaul.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import opal.dev.wynnoverhaul.WynnOverhaul

object QuestWikiFetcher {
    data class WikiLine(
        val text: String,
        val italic: Boolean = false,
        val spoiler: Boolean = false,
        val title: String? = null,
        val objective: Boolean = false,
    )

    data class WikiSection(val title: String, val lines: List<WikiLine>)

    data class WikiCoord(val x: Int, val y: Int, val z: Int)

    data class WikiPage(
        val name: String,
        val title: String,
        val type: String,
        val intro: List<WikiLine> = emptyList(),
        val sections: List<WikiSection> = emptyList(),
        val coord: WikiCoord? = null,
    )

    private val byKey: Map<String, WikiPage> by lazy { load() }
    private val byDisambiguatedKey: Map<String, WikiPage> by lazy { buildDisambiguatedIndex() }

    fun allPages(): Collection<WikiPage> = byKey.values

    fun find(type: ActivityType, name: String): WikiPage? {
        val labels = labelsFor(type)
        for (label in labels) {
            byKey["$label::$name"]?.let { return it }
        }
        for (label in labels) {
            byDisambiguatedKey["$label::$name"]?.let { return it }
        }
        val fallback = byKey.values.firstOrNull { it.name == name }
        if (fallback == null) {
            val triedKeys = labels.joinToString(", ") { "$it::$name" }
            WynnOverhaul.LOGGER.warn(
                "WynnOverhaul activity wiki lookup failed: type={} name='{}' triedKeys=[{}] bundleSize={} sampleKeys={}",
                type, name, triedKeys, byKey.size,
                byKey.keys.filter { it.startsWith(labels.firstOrNull().orEmpty()) }.take(5),
            )
        }
        return fallback
    }

    private val TRAILING_PAREN = Regex("""\s*\([^()]*\)$""")

    private fun buildDisambiguatedIndex(): Map<String, WikiPage> {
        val index = HashMap<String, WikiPage>()
        for (page in byKey.values) {
            val stripped = page.name.replace(TRAILING_PAREN, "").trim()
            if (stripped == page.name) continue
            index.putIfAbsent("${page.type}::$stripped", page)
        }
        return index
    }

    private fun labelsFor(type: ActivityType): List<String> = when (type) {
        ActivityType.QUEST, ActivityType.STORYLINE_QUEST, ActivityType.MINI_QUEST -> listOf("Quest")
        ActivityType.WORLD_EVENT -> listOf("WorldEvent")
        ActivityType.SECRET_DISCOVERY -> listOf("SecretDiscovery", "UltimateDiscovery")
        ActivityType.WORLD_DISCOVERY, ActivityType.TERRITORIAL_DISCOVERY, ActivityType.MOUNT_ENCLOSURE -> listOf("WorldDiscovery")
        ActivityType.CAVE, ActivityType.LOOTRUN_CAMP -> listOf("Cave")
        ActivityType.DUNGEON -> listOf("Dungeon")
        ActivityType.RAID -> listOf("Raid")
        ActivityType.BOSS_ALTAR -> listOf("BossAltar")
    }

    private fun load(): Map<String, WikiPage> {
        return try {
            val stream = QuestWikiFetcher::class.java.getResourceAsStream("/assets/wynnoverhaul/activity_wiki.json")
            if (stream == null) {
                WynnOverhaul.LOGGER.error("WynnOverhaul activity wiki bundle not found on classpath at /assets/wynnoverhaul/activity_wiki.json")
                return emptyMap()
            }
            val loaded = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val mapType = object : TypeToken<Map<String, WikiPage>>() {}.type
                Gson().fromJson<Map<String, WikiPage>>(reader, mapType) ?: emptyMap()
            }
            loaded
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("Failed to load bundled activity wiki data", t)
            emptyMap()
        }
    }
}
