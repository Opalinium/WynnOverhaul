package opal.dev.wynnoverhaul.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import opal.dev.wynnoverhaul.WynnOverhaul
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DiscordRichPresence : ClientModInitializer {
    @Volatile
    private var connection: DiscordIpcConnection? = null
    private var nextConnectAttemptNanos = 0L
    private var nextUpdateNanos = 0L
    private var sessionStartEpochSeconds: Long = Instant.now().epochSecond
    private var loggedConnectError = false
    private val connecting = AtomicBoolean(false)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "wynnoverhaul-discord").apply { isDaemon = true }
    }

    override fun onInitializeClient() {
        WynnOverhaulConfig.ensureLoaded()
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        ClientLifecycleEvents.CLIENT_STOPPING.register { disconnect() }
    }

    private fun onTick(mc: Minecraft) {
        val config = WynnOverhaulConfig.current
        if (!config.discordRpcEnabled) {
            disconnect()
            return
        }

        val now = System.nanoTime()
        if (connection == null) {
            if (now < nextConnectAttemptNanos) return
            nextConnectAttemptNanos = now + RECONNECT_INTERVAL_NANOS
            if (connecting.compareAndSet(false, true)) {
                worker.execute {
                    try {
                        connect()
                    } finally {
                        connecting.set(false)
                    }
                }
            }
            return
        }

        if (now < nextUpdateNanos) return
        nextUpdateNanos = now + UPDATE_INTERVAL_NANOS
        updatePresence(mc)
    }

    private fun connect() {
        try {
            val conn = DiscordIpcConnection.connect(APPLICATION_ID)
            if (conn == null) {
                if (!loggedConnectError) {
                    loggedConnectError = true
                    WynnOverhaul.LOGGER.warn("WynnOverhaul Discord RPC: couldn't find a Discord client to connect to")
                }
                return
            }
            connection = conn
            sessionStartEpochSeconds = Instant.now().epochSecond
            nextUpdateNanos = 0L
            loggedConnectError = false
        } catch (t: Throwable) {
            if (!loggedConnectError) {
                loggedConnectError = true
                WynnOverhaul.LOGGER.warn("WynnOverhaul Discord RPC: couldn't connect ({})", t.message)
            }
        }
    }

    private fun updatePresence(mc: Minecraft) {
        val conn = connection ?: return
        val config = WynnOverhaulConfig.current
        val player = mc.player

        val activity = JsonObject()
        val timestamps = JsonObject()
        timestamps.addProperty("start", sessionStartEpochSeconds)
        activity.add("timestamps", timestamps)

        val server = mc.currentServer?.name?.takeIf { it.isNotBlank() }
            ?: mc.currentServer?.ip?.takeIf { it.isNotBlank() }
            ?: if (mc.singleplayerServer != null) "Singleplayer" else null

        val assets = JsonObject()
        assets.addProperty("large_image", LARGE_IMAGE_KEY)
        assets.addProperty("large_text", server ?: "WynnOverhaul")
        activity.add("assets", assets)

        val onWynncraft = player != null && mc.level != null && WorldContext.isWynncraft(mc)
        if (!onWynncraft) WynnLevelTracker.clear()

        if (player == null || mc.level == null) {
            activity.addProperty("details", "In the main menu")
        } else if (!onWynncraft) {
            activity.addProperty("details", "Playing Minecraft")
            activity.addProperty("state", server ?: "Not on Wynncraft")
        } else {
            if (config.discordShowRegion) WynnRegions.ensureLoaded()

            activity.addProperty("details", if (config.discordShowActivity) currentActivity() else "Playing Wynncraft")

            val stateParts = ArrayList<String>(2)
            if (config.discordShowLevel) WynnLevelTracker.level?.let { stateParts.add("Lv. $it") }
            if (config.discordShowRegion) WynnRegions.nearestRegion(player.x, player.z)?.let { stateParts.add(it) }
            activity.addProperty("state", if (stateParts.isEmpty()) (server ?: "Wynncraft") else stateParts.joinToString(" · "))
        }

        if (config.discordShowButton) {
            val button = JsonObject()
            button.addProperty("label", BUTTON_LABEL)
            button.addProperty("url", BUTTON_URL)
            val buttons = JsonArray()
            buttons.add(button)
            activity.add("buttons", buttons)
        }

        worker.execute {
            try {
                conn.sendActivity(CURRENT_PID, activity)
            } catch (t: Throwable) {
                WynnOverhaul.LOGGER.warn("WynnOverhaul Discord RPC: failed to update presence ({})", t.message)
                if (connection === conn) disconnect()
            }
        }
    }

    private fun currentActivity(): String {
        if (LootrunModel.state != LootrunModel.State.NOT_RUNNING) {
            return when (LootrunModel.state) {
                LootrunModel.State.CHOOSING_BEACON -> "On a Lootrun · choosing a beacon"
                LootrunModel.State.IN_TASK -> "On a Lootrun · " + when (LootrunModel.taskType) {
                    LootrunModel.TaskType.LOOT -> "looting chests"
                    LootrunModel.TaskType.SLAY -> "slaying"
                    LootrunModel.TaskType.DESTROY -> "destroying the objective"
                    LootrunModel.TaskType.DEFEND -> "defending"
                    LootrunModel.TaskType.UNKNOWN, null -> "in a task"
                }
                LootrunModel.State.NOT_RUNNING -> "On a Lootrun"
            }
        }
        WynnScoreboardTracker.current?.let { return "${it.type}: ${it.name}" }
        return "Adventuring"
    }

    private fun disconnect() {
        val conn = connection ?: return
        connection = null
        conn.close()
    }

    private companion object {
        const val APPLICATION_ID = 1549796946862940180L
        const val LARGE_IMAGE_KEY = "wynnoverhaul"
        const val BUTTON_LABEL = "Get WynnOverhaul"
        const val BUTTON_URL = "https://github.com/Opalinium/WynnOverhaul"
        const val RECONNECT_INTERVAL_NANOS = 15_000_000_000L
        const val UPDATE_INTERVAL_NANOS = 15_000_000_000L
        val CURRENT_PID: Int = ProcessHandle.current().pid().toInt()
    }
}
