package opal.dev.overwatch.client

import com.google.gson.JsonParser
import opal.dev.overwatch.Overwatch
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object WynnRegions {
    private data class Region(val name: String, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int)

    @Volatile
    private var regions: List<Region> = emptyList()

    @Volatile
    private var loading = false
    private var loggedError = false

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun ensureLoaded() {
        if (regions.isNotEmpty() || loading) return
        loading = true
        Thread({
            try {
                val request = HttpRequest.newBuilder(URI.create("https://api.wynncraft.com/v3/guild/list/territory"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val json = JsonParser.parseString(response.body()).asJsonObject
                val list = ArrayList<Region>(json.size())
                for (entry in json.entrySet()) {
                    val obj = entry.value.asJsonObject
                    val loc = obj.getAsJsonObject("location") ?: continue
                    val start = loc.getAsJsonArray("start")
                    val end = loc.getAsJsonArray("end")
                    val x1 = start[0].asInt
                    val z1 = start[1].asInt
                    val x2 = end[0].asInt
                    val z2 = end[1].asInt
                    list.add(Region(entry.key, minOf(x1, x2), maxOf(x1, x2), minOf(z1, z2), maxOf(z1, z2)))
                }
                regions = list
            } catch (t: Throwable) {
                if (!loggedError) {
                    loggedError = true
                    Overwatch.LOGGER.warn("Overwatch: failed to load Wynncraft region list ({})", t.message)
                }
            } finally {
                loading = false
            }
        }, "Overwatch-WynnRegions").apply { isDaemon = true }.start()
    }

    fun nearestRegion(x: Double, z: Double): String? {
        val list = regions
        if (list.isEmpty()) return null
        var best: Region? = null
        var bestDist = Double.MAX_VALUE
        for (r in list) {
            val dx = when {
                x < r.minX -> r.minX - x
                x > r.maxX -> x - r.maxX
                else -> 0.0
            }
            val dz = when {
                z < r.minZ -> r.minZ - z
                z > r.maxZ -> z - r.maxZ
                else -> 0.0
            }
            val dist = dx * dx + dz * dz
            if (dist < bestDist) {
                bestDist = dist
                best = r
                if (dist == 0.0) break
            }
        }
        return best?.name
    }
}
