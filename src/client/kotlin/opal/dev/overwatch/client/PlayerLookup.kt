package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object PlayerLookup {
    private const val API_URL = "https://api.mojang.com/users/profiles/minecraft/"
    private const val USER_AGENT = "Overwatch-Fabric-Mod"
    private const val NEGATIVE_TTL_MS = 10 * 60 * 1000L
    private const val MAX_ENTRIES = 512

    private enum class State { PENDING, REAL, FAKE }

    private class Entry(var state: State, var at: Long) {
        val waiting = ArrayList<(Boolean) -> Unit>()
    }

    private val cache = LinkedHashMap<String, Entry>()

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build()

    fun clear() {
        cache.clear()
    }

    fun verify(name: String, knownReal: Boolean, onResult: (Boolean) -> Unit) {
        val key = name.lowercase()
        val now = System.currentTimeMillis()
        val entry = cache[key]
        if (knownReal) {
            val known = entry ?: Entry(State.REAL, now).also { cache[key] = it }
            known.state = State.REAL
            known.at = now
            val callbacks = ArrayList(known.waiting)
            known.waiting.clear()
            for (callback in callbacks) callback(true)
            onResult(true)
            return
        }
        if (entry != null) {
            when {
                entry.state == State.REAL -> return onResult(true)
                entry.state == State.PENDING -> return run { entry.waiting.add(onResult) }
                now - entry.at < NEGATIVE_TTL_MS -> return onResult(false)
            }
        }
        val created = Entry(State.PENDING, now)
        created.waiting.add(onResult)
        cache[key] = created
        if (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        query(name) { result -> Minecraft.getInstance().execute { settle(key, created, result) } }
    }

    private fun settle(key: String, entry: Entry, result: Boolean?) {
        val real = result == true
        when (result) {
            null -> if (cache[key] === entry) cache.remove(key)
            else -> {
                entry.state = if (real) State.REAL else State.FAKE
                entry.at = System.currentTimeMillis()
            }
        }
        val callbacks = ArrayList(entry.waiting)
        entry.waiting.clear()
        for (callback in callbacks) callback(real)
    }

    private fun query(name: String, done: (Boolean?) -> Unit) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL + name))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete { response, error ->
            val status = response?.statusCode()
            done(
                when {
                    error != null || status == null -> null
                    status == 200 -> true
                    status == 204 || status == 404 -> false
                    else -> null
                },
            )
        }
    }
}
