package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB

object DiscoveryTracker {
    class Located(val x: Int, val y: Int, val z: Int, val approximate: Boolean, val hint: String?)

    class Target(val name: String, val located: Located)

    @Volatile
    var target: Target? = null
        private set

    private val cache = HashMap<String, Located?>()
    private var indexBuilt = false
    private var regionsIncluded = false
    private val places = ArrayList<Pair<String, Located>>()
    private val coordPages = ArrayList<Pair<QuestWikiFetcher.WikiPage, String>>()

    fun locate(activity: ActivityInfo): Located? {
        val key = "${activity.type}::${activity.name}"
        if (cache.containsKey(key)) return cache[key]
        WynnRegions.ensureLoaded()
        val result = resolve(activity)
        if ((result != null && !result.approximate) || WynnRegions.centers().isNotEmpty()) cache[key] = result
        return result
    }

    fun distanceTo(activity: ActivityInfo): Double {
        val player = Minecraft.getInstance().player ?: return Double.MAX_VALUE
        val located = locate(activity) ?: return Double.MAX_VALUE
        val dx = player.x - located.x
        val dz = player.z - located.z
        return dx * dx + dz * dz
    }

    fun isTracked(name: String): Boolean = target?.name == name

    fun toggle(activity: ActivityInfo): Boolean {
        if (isTracked(activity.name)) {
            target = null
            return true
        }
        val located = locate(activity) ?: return false
        target = Target(activity.name, located)
        return true
    }

    fun clear() {
        target = null
    }

    fun matches(): List<EntityTracker.Match> {
        val current = target ?: return emptyList()
        val player = Minecraft.getInstance().player ?: return emptyList()
        val located = current.located
        if (!located.approximate && player.distanceToSqr(located.x + 0.5, located.y.toDouble(), located.z + 0.5) < ARRIVE_SQR) {
            target = null
            return emptyList()
        }
        val label = if (located.approximate) "${current.name} (approx.)" else current.name
        val box = AABB(located.x.toDouble(), located.y.toDouble(), located.z.toDouble(), located.x + 1.0, located.y + 1.0, located.z + 1.0)
        return listOf(EntityTracker.Match(null, box, label, COLOR, true, WaypointIcons.task))
    }

    private fun resolve(activity: ActivityInfo): Located? {
        val page = QuestWikiFetcher.find(activity.type, activity.name) ?: return null
        page.coord?.let { return Located(it.x, it.y, it.z, false, null) }
        buildIndex()
        val intro = pageText(page).take(INTRO_CHARS)
        for (match in LOCATION.findAll(intro)) {
            val candidate = match.groupValues[1].trim()
            if (candidate.length < MIN_NAME || candidate in GENERIC) continue
            val named = places.firstOrNull { it.first == candidate && it.first != page.name }
            if (named != null) return Located(named.second.x, named.second.y, named.second.z, true, candidate)
            val supporters = coordPages.filter { (other, text) -> other.name != page.name && mentions(text, candidate) }
            if (supporters.isNotEmpty() && supporters.size <= MAX_SUPPORT) {
                val x = median(supporters.map { it.first.coord!!.x })
                val y = median(supporters.map { it.first.coord!!.y })
                val z = median(supporters.map { it.first.coord!!.z })
                return Located(x, y, z, true, candidate)
            }
        }
        return null
    }

    private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]

    private fun pageText(page: QuestWikiFetcher.WikiPage): String = buildString {
        for (line in page.intro) append(line.text).append(' ')
        for (section in page.sections) for (line in section.lines) append(line.text).append(' ')
    }

    private fun mentions(text: String, name: String): Boolean {
        var from = 0
        while (true) {
            val idx = text.indexOf(name, from)
            if (idx < 0) return false
            val before = if (idx == 0) ' ' else text[idx - 1]
            val after = if (idx + name.length >= text.length) ' ' else text[idx + name.length]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = idx + 1
        }
    }

    private fun buildIndex() {
        val regions = WynnRegions.centers()
        if (indexBuilt && (regionsIncluded || regions.isEmpty())) return
        places.clear()
        if (coordPages.isEmpty()) {
            for (page in QuestWikiFetcher.allPages()) if (page.coord != null) coordPages.add(page to pageText(page))
        }
        for (page in QuestWikiFetcher.allPages()) {
            val coord = page.coord ?: continue
            if (page.type != "Cave" && page.type != "WorldDiscovery" && page.type != "BossAltar") continue
            val name = page.name.replace(TRAILING_PAREN, "").trim()
            places.add(name to Located(coord.x, coord.y, coord.z, false, null))
        }
        for ((name, center) in regions) {
            places.add(name to Located(center.first, REGION_Y, center.second, true, REGION_HINT))
        }
        indexBuilt = true
        regionsIncluded = regions.isNotEmpty()
    }

    private val TRAILING_PAREN = Regex("""\s*\([^()]*\)$""")
    private val LOCATION = Regex("""\b(?:in|near|by|on|at|inside|within)\s+(?:the\s+)?([A-Z][\w'’-]*(?:\s+(?:(?:of|the|du|de|la)\s+)*[A-Z][\w'’-]*)*)""")
    private val GENERIC = setOf("Secret Discovery", "Ultimate Discovery", "Discovery", "Wynncraft")
    private const val INTRO_CHARS = 400
    private const val MAX_SUPPORT = 60
    private const val MIN_NAME = 4
    private const val REGION_Y = 80
    private const val REGION_HINT = "region"
    private const val ARRIVE_SQR = 36.0
    private const val COLOR = 0xFFB98CFF.toInt()
}
