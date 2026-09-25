package opal.dev.wynnoverhaul.client

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object PriceCheck {
    data class Query(val name: String, val tier: Int, val shiny: Boolean) {
        val key: String get() = "$name|$tier|$shiny"
    }

    class Stats(
        val lowest: Long?,
        val highest: Long?,
        val average: Double?,
        val median: Double?,
        val count: Int,
        val historic: Boolean,
    ) {
        fun reference(): Double? = median ?: average
    }

    enum class Status { LOADING, OK, NONE, AUTH, ERROR }

    class Entry(val status: Status, val stats: Stats?, val atMs: Long)

    class Listing(val price: Long, val npc: Boolean)

    private val cache = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var blockedUntilMs = 0L

    @Volatile
    private var rejectedKey: String? = null

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build()

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(QUEUE_CAPACITY),
    ) { r ->
        Thread(r, "wynnoverhaul-price-check").also { it.isDaemon = true }
    }

    private val sectionCode = Regex("§.")
    private val whitespace = Regex("\\s+")
    private val tmPrice = Regex("""(?:(\d[\d,]*)\s*x\s*)?(?:[✔✖]\s*)?(\d[\d,]*)²""")
    private val powderName = Regex("""(Earth|Thunder|Water|Fire|Air) Powder ([IV]{1,3})$""")
    private val spacer = Regex("À+")
    private val npcPrice = Regex("""^[✔✖]\s*(\d[\d,]*)²""")

    private var memoStack: ItemStack? = null
    private var memoAtMs = 0L
    private var memoQuery: Query? = null
    private var memoListing: Listing? = null

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val config = WynnOverhaulConfig.current
            if (!config.priceCheckEnabled || !WynnOverhaulGate.inGame || stack.isEmpty) return@register
            val hasKey = config.wynnventoryApiKey.isNotBlank()
            val npcWanted = config.priceCheckNpcEnabled
            if (!hasKey && !npcWanted) return@register

            val now = System.currentTimeMillis()
            if (memoStack !== stack || now - memoAtMs > MEMO_MAX_AGE_MS) {
                memoStack = stack
                memoAtMs = now
                memoQuery = queryOf(stack)
                memoListing = listingOf(stack)
            }
            val query = memoQuery
            val listing = memoListing?.takeIf { !it.npc || npcWanted }
            val entry = if (hasKey && query != null) lookup(query, config.wynnventoryApiKey.trim()) else null
            if (entry == null && listing == null) return@register

            lines.add(Component.literal(""))
            if (entry != null) lines.addAll(marketLines(entry))
            if (listing != null) lines.add(listingLine(listing, entry?.stats))
        }
    }

    private fun marketLines(entry: Entry): List<Component> {
        val out = ArrayList<Component>(4)
        out.add(Component.literal("Market price (Wynnventory)").withStyle(ChatFormatting.GOLD))
        when (entry.status) {
            Status.LOADING -> out.add(dim(" Loading…"))
            Status.NONE -> out.add(dim(" No market data"))
            Status.AUTH -> out.add(Component.literal(" API key rejected").withStyle(ChatFormatting.RED))
            Status.ERROR -> out.add(Component.literal(" Unavailable right now").withStyle(ChatFormatting.RED))
            Status.OK -> {
                val s = entry.stats ?: return out
                s.lowest?.let { out.add(stat(" Lowest", it.toDouble())) }
                s.median?.let { out.add(stat(" Median", it)) }
                s.average?.let { out.add(stat(" Average", it)) }
                if (s.historic) out.add(dim(" Nothing listed now - recent history"))
                else if (s.count > 0) out.add(dim(" ${s.count} listing${if (s.count == 1) "" else "s"}"))
            }
        }
        return out
    }

    private fun stat(label: String, value: Double): Component =
        Component.literal("$label: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(formatEmeralds(value.toLong())).withStyle(ChatFormatting.WHITE))

    private fun dim(text: String): Component = Component.literal(text).withStyle(ChatFormatting.DARK_GRAY)

    private fun listingLine(listing: Listing, stats: Stats?): Component {
        val label = if (listing.npc) "NPC price" else "Listed at"
        val line = Component.literal("$label: ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(formatEmeralds(listing.price)).withStyle(ChatFormatting.WHITE))
        val ref = stats?.takeIf { it.count > 0 || it.median != null || it.average != null }?.reference()
        if (ref == null || ref <= 0.0 || listing.price <= 0L) return line
        val pct = ((listing.price - ref) / ref * 100.0)
        val rounded = kotlin.math.abs(pct).toInt()
        if (rounded == 0) return line.append(dim("  (at market)"))
        val below = pct < 0
        return line.append(
            Component.literal("  ($rounded% ${if (below) "below" else "above"} market)")
                .withStyle(if (below) ChatFormatting.GREEN else ChatFormatting.RED),
        )
    }

    fun formatEmeralds(total: Long): String {
        if (total <= 0L) return "0 e"
        val stx = total / STX_VALUE
        val le = (total / LE_VALUE) % 64
        val eb = (total / EB_VALUE) % 64
        val e = total % EB_VALUE
        val parts = ArrayList<String>(4)
        if (stx > 0) parts.add("$stx stx")
        if (le > 0) parts.add("$le LE")
        if (eb > 0) parts.add("$eb EB")
        if (e > 0) parts.add("$e e")
        val base = parts.joinToString(" ")
        return if (total >= EB_VALUE) "$base (${"%,d".format(total)}²)" else base
    }

    fun queryOf(stack: ItemStack): Query? {
        if (stack.isEmpty) return null
        val rawName = stack.hoverName.string
        val lore = WynnItemRarity.loreLines(stack)
        val lcLore = lore.map { it.lowercase() }
        val name = tidy(rawName)
        if (name.isEmpty() || name.startsWith("unidentified", ignoreCase = true)) return null

        tieredQueryOf(stack, name, lcLore)?.let { return it }

        if (WynnItemRarity.of(stack) != null && WynnGearKind.of(stack) != null) {
            if (lcLore.firstOrNull { it.isNotEmpty() }?.contains("crafted") == true) return null
            val shiny = lcLore.any { "shiny" in it }
            return Query(name, 0, shiny)
        }

        return null
    }

    private fun tieredQueryOf(stack: ItemStack, name: String, lcLore: List<String>): Query? {
        powderName.find(name)?.let { m ->
            val tier = POWDER_TIERS[m.groupValues[2]] ?: return null
            return Query("${m.groupValues[1]} Powder", tier, false)
        }
        val tags = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.strings().orEmpty()
        if (MODEL_RUNE in tags) return Query(name, 0, false)
        if (MODEL_INGREDIENT in tags || MODEL_MATERIAL in tags) {
            val tier = tags.firstOrNull { it.startsWith(MODEL_TIER_PREFIX) }
                ?.removePrefix(MODEL_TIER_PREFIX)
                ?.toIntOrNull()
            return tier?.let { Query(name, it, false) }
        }
        if (name.endsWith(" Key") && lcLore.firstOrNull { it.isNotEmpty() }?.startsWith("grants access to the") == true) {
            return Query(spacer.replace(name, " "), 0, false)
        }
        return null
    }

    private fun tidy(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        val text = sectionCode.replace(raw, "")
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp < 0xE000) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(whitespace, " ").trim()
    }

    fun sortValue(stack: ItemStack): Double {
        if (stack.isEmpty) return 0.0
        val config = WynnOverhaulConfig.current
        val key = config.wynnventoryApiKey.trim()
        val query = queryOf(stack)
        val market = if (key.isNotEmpty() && query != null) lookup(query, key)?.stats?.reference() else null
        val each = market ?: listingOf(stack)?.price?.toDouble() ?: 0.0
        return each * stack.count
    }

    fun listingOf(stack: ItemStack): Listing? {
        if (stack.isEmpty) return null
        val lore = WynnItemRarity.loreLines(stack)
        if (lore.isEmpty()) return null
        val header = lore.indexOfFirst { it.trim() == "Price:" }
        if (header >= 0) {
            val value = lore.getOrNull(header + 1) ?: return null
            val match = tmPrice.find(value) ?: return null
            val price = match.groupValues[2].replace(",", "").toLongOrNull() ?: return null
            return Listing(price, npc = false)
        }
        for (line in lore) {
            val match = npcPrice.find(line.trim()) ?: continue
            val price = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            return Listing(price, npc = true)
        }
        return null
    }

    fun lookup(query: Query, apiKey: String): Entry? {
        val now = System.currentTimeMillis()
        if (rejectedKey != null && rejectedKey != apiKey) rejectedKey = null
        if (rejectedKey == apiKey) return Entry(Status.AUTH, null, now)

        val key = query.key
        val cached = cache[key]
        if (cached != null) {
            val ttl = when (cached.status) {
                Status.LOADING -> LOADING_TIMEOUT_MS
                Status.ERROR -> ERROR_TTL_MS
                else -> TTL_MS
            }
            if (now - cached.atMs < ttl) return cached
        }
        if (now < blockedUntilMs) return cached ?: Entry(Status.ERROR, null, now)

        if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        val loading = Entry(Status.LOADING, null, now)
        cache[key] = loading
        try {
            executor.execute { fetch(query, apiKey) }
        } catch (_: RejectedExecutionException) {
            cache.remove(key)
        }
        return loading
    }

    private fun fetch(query: Query, apiKey: String) {
        val key = query.key
        val result = try {
            val live = get(query, apiKey, "price")
            when {
                live.status == 200 -> Entry(Status.OK, parseStats(live.body, historic = false), System.currentTimeMillis())
                live.status == 404 -> {
                    val hist = get(query, apiKey, "history/latest")
                    when (hist.status) {
                        200 -> Entry(Status.OK, parseStats(hist.body, historic = true), System.currentTimeMillis())
                        404 -> Entry(Status.NONE, null, System.currentTimeMillis())
                        else -> failure(hist, apiKey)
                    }
                }
                else -> failure(live, apiKey)
            }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("Price check request failed for '{}': {}", query.name, t.toString())
            blockedUntilMs = System.currentTimeMillis() + BACKOFF_MS
            Entry(Status.ERROR, null, System.currentTimeMillis())
        }
        cache[key] = result
        try {
            Thread.sleep(REQUEST_SPACING_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private class Response(val status: Int, val body: String, val retryAfterSeconds: Long?)

    private fun get(query: Query, apiKey: String, suffix: String): Response {
        val base = "$BASE_URL/${encode(query.name)}/$suffix"
        val params = ArrayList<String>(2)
        if (query.tier > 0) params.add("tier=${query.tier}")
        params.add("shiny=${query.shiny}")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$base?${params.joinToString("&")}"))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .header("X-API-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val retry = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()
        return Response(response.statusCode(), response.body(), retry)
    }

    private fun failure(response: Response, apiKey: String): Entry {
        val now = System.currentTimeMillis()
        return when (response.status) {
            401, 403 -> {
                rejectedKey = apiKey
                Entry(Status.AUTH, null, now)
            }
            429 -> {
                val wait = (response.retryAfterSeconds ?: 60L).coerceIn(5L, 300L) * 1000L
                blockedUntilMs = now + wait
                Entry(Status.ERROR, null, now)
            }
            else -> {
                if (response.status >= 500) blockedUntilMs = now + BACKOFF_MS
                Entry(Status.ERROR, null, now)
            }
        }
    }

    private fun parseStats(body: String, historic: Boolean): Stats? {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() as? JsonObject ?: return null
        val data = root.get("data") as? JsonObject ?: return null
        val lowest = long(data, "lowest_price")
        val highest = long(data, "highest_price")
        val average = double(data, "average_price")
        val median = double(data, "p50_price") ?: double(data, "average_p50_ema_price")
        val count = long(data, "total_count")?.toInt() ?: 0
        if (lowest == null && average == null && median == null) return null
        return Stats(lowest, highest, average, median, count, historic)
    }

    private fun long(o: JsonObject, key: String): Long? = number(o.get(key))?.toLong()

    private fun double(o: JsonObject, key: String): Double? = number(o.get(key))?.toDouble()

    private fun number(e: JsonElement?): Number? {
        if (e == null || !e.isJsonPrimitive || !e.asJsonPrimitive.isNumber) return null
        return e.asNumber
    }

    private fun encode(name: String): String =
        URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20")

    private const val BASE_URL = "https://www.wynnventory.com/api/v2/market/items"
    private const val USER_AGENT = "WynnOverhaul-Fabric-Mod"
    private const val TTL_MS = 600_000L
    private const val ERROR_TTL_MS = 60_000L
    private const val LOADING_TIMEOUT_MS = 30_000L
    private const val BACKOFF_MS = 60_000L
    private const val REQUEST_SPACING_MS = 300L
    private const val QUEUE_CAPACITY = 24
    private const val MAX_CACHE_ENTRIES = 512
    private const val MEMO_MAX_AGE_MS = 500L
    private const val MODEL_RUNE = "resource_rune"
    private const val MODEL_INGREDIENT = "profession_ingredient"
    private const val MODEL_MATERIAL = "profession_material"
    private const val MODEL_TIER_PREFIX = "profession_tier_"
    private val POWDER_TIERS = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)
    private const val EB_VALUE = 64L
    private const val LE_VALUE = 4096L
    private const val STX_VALUE = 262144L
}
