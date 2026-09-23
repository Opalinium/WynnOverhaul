package opal.dev.overwatch.client

object MountFeedingSummary {

    private var cacheKey: String? = null
    private var cacheResult: MountShoppingList? = null

    fun compute(reading: MountReading): MountShoppingList? {
        val resolved = MountRegistry.resolve(reading)
        if (MountMaterials.STATS.any { resolved.stats[it] == null }) return null
        val cur = IntArray(8) { resolved.stats.getValue(MountMaterials.STATS[it]).current }
        val lim = IntArray(8) { resolved.stats.getValue(MountMaterials.STATS[it]).limit }
        if (MountMaterials.STATS.any { resolved.stats.getValue(it).max == null }) {
            val trainable = MountMaterials.STATS.filter { resolved.stats.getValue(it).current < resolved.stats.getValue(it).limit }
            return MountShoppingList(emptyList(), 0, emptySet(), allMaxed = false, noMaterialsAvailable = false, rawH = cur.max(), maxUnknown = true, trainable = trainable)
        }
        val max = IntArray(8) { resolved.stats.getValue(MountMaterials.STATS[it]).max!! }
        val key = "${cur.joinToString(",")}|${lim.joinToString(",")}|${max.joinToString(",")}"
        if (key == cacheKey) return cacheResult
        val result = MountOptimizer.computeShoppingList(cur, lim, max, MountTrainMode.NORMAL, null)
        cacheKey = key
        cacheResult = result
        return result
    }

    fun mergedFeedCounts(result: MountShoppingList): List<Pair<String, Int>> {
        val merged = LinkedHashMap<String, Int>()
        for (phase in result.phases) {
            if (phase.isTraining) continue
            for ((name, count) in phase.feedCounts) merged[name] = (merged[name] ?: 0) + count
        }
        return merged.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
