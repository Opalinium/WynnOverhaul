package opal.dev.overwatch.client

class ContentBookViewModel(initialActivities: List<ActivityInfo>) {

    enum class Sort(val label: String) {
        RECOMMENDED("Recommended"),
        NAME("Name"),
        LEVEL("Level"),
        STATUS("Status"),
    }

    var activities: List<ActivityInfo> = initialActivities
        private set
    var query: String = ""
    var page: Int = 0

    var filter: String? = initialActivities.firstOrNull { it.trackingState == ActivityTrackingState.TRACKED }?.type?.filterName
        ?: OverwatchConfig.current.contentBookFilter.ifEmpty { null }
    var sort: Sort = runCatching { Sort.valueOf(OverwatchConfig.current.contentBookSort) }.getOrDefault(Sort.RECOMMENDED)
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
        }
        result = result.sortedByDescending { it.trackingState == ActivityTrackingState.TRACKED }
        return result
    }

    fun pageCount(): Int {
        val n = results().size
        return maxOf(1, (n + PER_PAGE - 1) / PER_PAGE)
    }

    fun coercePage() {
        page = page.coerceIn(0, pageCount() - 1)
    }

    fun pageItems(): List<ActivityInfo> {
        coercePage()
        val r = results()
        val start = page * PER_PAGE
        return r.subList(start, minOf(r.size, start + PER_PAGE))
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

    fun cycleFilter() {
        val options = filterOptions()
        val i = options.indexOf(filter).coerceAtLeast(0)
        filter = options[(i + 1) % options.size]
        page = 0
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
        val config = OverwatchConfig.current
        config.contentBookSort = sort.name
        config.contentBookFilter = filter ?: ""
        config.save()
    }

    companion object {
        const val COLS = 9
        const val ROWS = 5
        const val SLOT_SIZE = 20
        const val SLOT_GAP = 4
        const val SLOT_PITCH = SLOT_SIZE + SLOT_GAP
        const val PER_PAGE = COLS * ROWS
    }
}
