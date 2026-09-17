package opal.dev.overwatch.client

object MountFeedingSummary {

    private var cacheKey: String? = null
    private var cacheResult: MountShoppingList? = null

    fun compute(reading: MountReading): MountShoppingList? {
        if (MountMaterials.STATS.any { reading.stats[it] == null }) return null
        val cur = IntArray(8) { reading.stats.getValue(MountMaterials.STATS[it]).current }
        val lim = IntArray(8) { reading.stats.getValue(MountMaterials.STATS[it]).limit }
        val max = IntArray(8) { reading.stats.getValue(MountMaterials.STATS[it]).max ?: MountGuideData.NEW_MOUNT_STAT_MAX }
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
