package opal.dev.overwatch.client

data class StoredMountStat(var current: Int = 0, var limit: Int = 0, var max: Int = 0)

data class StoredMount(
    var typeName: String = "",
    var name: String = "",
    var potential: Int = 0,
    var stats: MutableMap<String, StoredMountStat> = mutableMapOf(),
)

object MountRegistry {
    fun keyOf(typeName: String, name: String, potential: Int?): String =
        if (potential != null) "$typeName|$name|$potential" else "$typeName|$name"

    fun isComplete(reading: MountReading): Boolean =
        MountMaterials.STATS.all { reading.stats[it]?.max != null }

    fun note(reading: MountReading) {
        if (!isComplete(reading)) return
        val entry = StoredMount(
            typeName = reading.typeName,
            name = reading.name,
            potential = reading.potential ?: 0,
            stats = MountMaterials.STATS.associateTo(LinkedHashMap()) { key ->
                val s = reading.stats.getValue(key)
                key to StoredMountStat(s.current, s.limit, s.max ?: 0)
            },
        )
        val key = keyOf(reading.typeName, reading.name, reading.potential)
        val store = store()
        if (store[key] != entry) {
            store[key] = entry
            OverwatchConfig.current.save()
        }
    }

    fun resolve(reading: MountReading): MountReading {
        if (isComplete(reading)) return reading
        val entry = store()[keyOf(reading.typeName, reading.name, reading.potential)] ?: return reading
        val stats = LinkedHashMap<String, MountStatReading>()
        for (key in MountMaterials.STATS) {
            val live = reading.stats[key] ?: continue
            val stored = entry.stats[key]
            stats[key] = if (live.max == null && stored != null) live.copy(max = stored.max) else live
        }
        return reading.copy(stats = stats)
    }

    private fun store(): MutableMap<String, StoredMount> {
        val config = OverwatchConfig.current
        return config.mountRegistry ?: mutableMapOf<String, StoredMount>().also { config.mountRegistry = it }
    }
}
