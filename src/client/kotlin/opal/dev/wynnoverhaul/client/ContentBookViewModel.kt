package opal.dev.wynnoverhaul.client

class ContentBookViewModel(initialActivities: List<ActivityInfo>) {
    enum class Sort(val label: String) {
        RECOMMENDED("Recommended"),
        NAME("Name"),
        LEVEL("Level"),
        STATUS("Status"),
        PROXIMITY("Proximity"),
    }

    var activities: List<ActivityInfo> = initialActivities
        private set
    var query: String = ""

    var filter: String? = initialActivities.firstOrNull { it.trackingState == ActivityTrackingState.TRACKED }?.type?.filterName
        ?: WynnOverhaulConfig.current.contentBookFilter.ifEmpty { null }
    var sort: Sort = runCatching { Sort.valueOf(WynnOverhaulConfig.current.contentBookSort) }.getOrDefault(Sort.RECOMMENDED)
    var actionMessage: String? = null

    fun update(list: List<ActivityInfo>) {
        activities = list
    }

    fun results(): List<ActivityInfo> {
        var list = activities.asSequence()
        val f = filter
        if (f != null) list = list.filter { it.type.filterName == f }
        val q = query.trim()
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        var result = list.toList()
        result = when (sort) {
            Sort.RECOMMENDED -> result.sortedBy { if (it.status == ActivityStatus.COMPLETED) 1 else 0 }
            Sort.NAME -> result.sortedBy { it.name }
            Sort.LEVEL -> result.sortedByDescending { it.levelReq }
            Sort.STATUS -> result.sortedBy { it.status.ordinal }
            Sort.PROXIMITY -> result.sortedBy { DiscoveryTracker.distanceTo(it) }
        }
        result = result.sortedByDescending { it.trackingState == ActivityTrackingState.TRACKED }
        return result
    }

    fun statusLine(): String = actionMessage ?: if (ContentBookQuery.isEnumerating) {
        "${activities.size} activities (refreshing...)"
    } else {
        "${results().size} of ${activities.size} activities"
    }

    fun filterOptions(): List<String?> =
        listOf<String?>(null) + ActivityType.entries.distinctBy { it.filterName }.sortedBy { it.filterName }.map { it.filterName }

    fun cycleSort() {
        sort = Sort.entries[(sort.ordinal + 1) % Sort.entries.size]
        saveViewPrefs()
    }

    fun selectFilter(value: String?) {
        filter = value
        actionMessage = null
        saveViewPrefs()
    }

    fun applyTrackToggleLocal(activity: ActivityInfo) {
        activities = ContentBookCache.applyTrackToggle(activity.type, activity.name)
            ?: activities.map { info ->
                when {
                    info.name == activity.name && info.type == activity.type ->
                        info.copy(trackingState = if (info.trackingState == ActivityTrackingState.TRACKED) ActivityTrackingState.TRACKABLE else ActivityTrackingState.TRACKED)
                    info.trackingState == ActivityTrackingState.TRACKED -> info.copy(trackingState = ActivityTrackingState.TRACKABLE)
                    else -> info
                }
            }
        actionMessage = null
    }

    private fun saveViewPrefs() {
        val config = WynnOverhaulConfig.current
        config.contentBookSort = sort.name
        config.contentBookFilter = filter ?: ""
        config.save()
    }

    companion object {
        const val ROW_H = 18
        const val LIST_COLS = 3
    }
}
