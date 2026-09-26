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

    private var cachedResults: List<ActivityInfo> = emptyList()
    private var cachedFrom: List<ActivityInfo>? = null
    private var cachedQuery = ""
    private var cachedFilter: String? = null
    private var cachedSort: Sort? = null
    private var cachedAtNanos = 0L

    fun results(): List<ActivityInfo> {
        val now = System.nanoTime()
        val fresh = cachedFrom === activities && cachedQuery == query && cachedFilter == filter && cachedSort == sort &&
            (sort != Sort.PROXIMITY || now - cachedAtNanos < PROXIMITY_REFRESH_NANOS)
        if (fresh) return cachedResults
        val computed = computeResults()
        cachedResults = computed
        cachedFrom = activities
        cachedQuery = query
        cachedFilter = filter
        cachedSort = sort
        cachedAtNanos = now
        return computed
    }

    private fun computeResults(): List<ActivityInfo> {
        var list = activities.asSequence()
        val f = filter
        if (f != null) list = list.filter { it.type.filterName == f }
        val q = query.trim()
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        val filtered = list.toList()
        val sorted = when (sort) {
            Sort.RECOMMENDED -> filtered.sortedBy { if (it.status == ActivityStatus.COMPLETED) 1 else 0 }
            Sort.NAME -> filtered.sortedBy { it.name }
            Sort.LEVEL -> filtered.sortedByDescending { it.levelReq }
            Sort.STATUS -> filtered.sortedBy { it.status.ordinal }
            Sort.PROXIMITY -> filtered.map { it to DiscoveryTracker.distanceTo(it) }.sortedBy { it.second }.map { it.first }
        }
        return sorted.sortedByDescending { it.trackingState == ActivityTrackingState.TRACKED }
    }

    fun statusLine(): String = actionMessage ?: if (ContentBookQuery.isEnumerating) {
        "${activities.size} activities (refreshing...)"
    } else {
        "${results().size} of ${activities.size} activities"
    }

    fun filterOptions(): List<String?> =
        listOf<String?>(null) + ActivityType.entries.distinctBy { it.filterName }.sortedBy { it.filterName }.map { it.filterName }

    fun selectSort(value: Sort) {
        sort = value
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
        private const val PROXIMITY_REFRESH_NANOS = 500_000_000L
    }
}
