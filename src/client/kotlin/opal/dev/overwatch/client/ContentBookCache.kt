package opal.dev.overwatch.client

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import opal.dev.overwatch.Overwatch
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ContentBookCache {
    private val PATH = FabricLoader.getInstance().configDir.resolve("overwatch-contentbook.json")
    private val GSON = GsonBuilder().create()
    private val MAP_TYPE = object : TypeToken<MutableMap<String, PersistedEntry>>() {}.type

    private const val REFRESH_INTERVAL_MILLIS = 20 * 60 * 1000L

    var snapshot: List<ActivityInfo>? = null
        private set
    private var lastFullScanAt: Long = 0L
    private var contextKey: String? = null

    fun needsRefresh(): Boolean = snapshot == null || System.currentTimeMillis() - lastFullScanAt > REFRESH_INTERVAL_MILLIS

    fun attach(client: Minecraft) {
        val key = contextKey(client) ?: return
        if (key == contextKey) return
        contextKey = key
        val entry = readAll()[key]
        snapshot = entry?.activities?.mapNotNull { it.toActivityInfo() }
        lastFullScanAt = entry?.scannedAtMillis ?: 0L
        clearTracking()
    }

    fun clearTracking(): List<ActivityInfo>? {
        val current = snapshot ?: return null
        if (current.none { it.trackingState == ActivityTrackingState.TRACKED }) return null
        val updated = current.map { info ->
            if (info.trackingState == ActivityTrackingState.TRACKED) info.copy(trackingState = ActivityTrackingState.TRACKABLE) else info
        }
        snapshot = updated
        persist()
        return updated
    }

    fun commit(activities: List<ActivityInfo>) {
        snapshot = activities
        lastFullScanAt = System.currentTimeMillis()
        persist()
    }

    fun commitIfFirstEver(activities: List<ActivityInfo>) {
        if (snapshot == null && activities.isNotEmpty()) commit(activities)
    }

    fun find(type: ActivityType, name: String): Boolean =
        snapshot?.any { it.name == name && (it.type == type || (type.isQuest && it.type.isQuest)) } == true

    fun markCompleted(name: String): List<ActivityInfo>? {
        val current = snapshot ?: return null
        var changed = false
        val updated = current.map { info ->
            if (info.type.isQuest && info.name == name && info.status != ActivityStatus.COMPLETED) {
                changed = true
                info.copy(
                    status = ActivityStatus.COMPLETED,
                    trackingState = if (info.trackingState == ActivityTrackingState.TRACKED) ActivityTrackingState.TRACKABLE else info.trackingState,
                )
            } else {
                info
            }
        }
        if (!changed) return null
        snapshot = updated
        persist()
        return updated
    }

    fun reconcileLiveTracked(liveName: String): List<ActivityInfo>? {
        val current = snapshot ?: return null
        if (current.any { it.trackingState == ActivityTrackingState.TRACKED && it.name == liveName }) return null
        val matchIndex = current.indexOfFirst { it.name == liveName && it.trackingState != ActivityTrackingState.UNTRACKABLE }
        if (matchIndex < 0) return null
        val updated = current.mapIndexed { index, info ->
            when {
                index == matchIndex -> info.copy(trackingState = ActivityTrackingState.TRACKED)
                info.trackingState == ActivityTrackingState.TRACKED -> info.copy(trackingState = ActivityTrackingState.TRACKABLE)
                else -> info
            }
        }
        snapshot = updated
        persist()
        return updated
    }

    fun applyTrackToggle(type: ActivityType, name: String): List<ActivityInfo>? {
        val current = snapshot ?: return null
        val updated = current.map { info ->
            when {
                info.name == name && (info.type == type || (type.isQuest && info.type.isQuest)) ->
                    info.copy(trackingState = if (info.trackingState == ActivityTrackingState.TRACKED) ActivityTrackingState.TRACKABLE else ActivityTrackingState.TRACKED)
                info.trackingState == ActivityTrackingState.TRACKED -> info.copy(trackingState = ActivityTrackingState.TRACKABLE)
                else -> info
            }
        }
        snapshot = updated
        persist()
        return updated
    }

    private fun persist() {
        val key = contextKey ?: return
        val activities = snapshot ?: return
        try {
            val all = readAll()
            all[key] = PersistedEntry(lastFullScanAt, activities.map { PersistedActivity.from(it) })
            PATH.parent?.let { Files.createDirectories(it) }
            PATH.writeText(GSON.toJson(all))
        } catch (t: Throwable) {
            Overwatch.LOGGER.error("Failed to save content book cache", t)
        }
    }

    private fun readAll(): MutableMap<String, PersistedEntry> = try {
        if (!PATH.exists()) mutableMapOf() else GSON.fromJson(PATH.readText(), MAP_TYPE) ?: mutableMapOf()
    } catch (t: Throwable) {
        Overwatch.LOGGER.error("overwatch-contentbook.json unreadable, starting a fresh store", t)
        mutableMapOf()
    }

    private fun contextKey(client: Minecraft): String? {
        val player = client.player ?: return null
        val server = WorldContext.key(client) ?: return null
        return "$server/${player.uuid}"
    }

    private data class PersistedEntry(val scannedAtMillis: Long, val activities: List<PersistedActivity>)

    private data class PersistedActivity(
        val type: String,
        val name: String,
        val status: String,
        val specialInfo: String?,
        val description: String?,
        val length: String?,
        val distance: String?,
        val difficulty: String?,
        val levelReq: Int,
        val levelReqFulfilled: Boolean,
        val professionReqs: List<ProfessionRequirement>,
        val questReqs: List<QuestRequirement>,
        val rewards: Map<String, List<String>>,
        val trackingState: String,
    ) {
        fun toActivityInfo(): ActivityInfo? {
            val activityType = runCatching { ActivityType.valueOf(type) }.getOrNull() ?: return null
            return ActivityInfo(
                icon = ItemStack(Items.BOOK),
                type = activityType,
                name = name,
                status = runCatching { ActivityStatus.valueOf(status) }.getOrDefault(ActivityStatus.AVAILABLE),
                specialInfo = specialInfo,
                description = description,
                length = length?.let { runCatching { ActivityLength.valueOf(it) }.getOrNull() },
                distance = distance?.let { runCatching { ActivityDistance.valueOf(it) }.getOrNull() },
                difficulty = difficulty?.let { runCatching { ActivityDifficulty.valueOf(it) }.getOrNull() },
                levelReq = levelReq,
                levelReqFulfilled = levelReqFulfilled,
                professionReqs = professionReqs,
                questReqs = questReqs,
                rewards = rewards.mapNotNull { (k, v) -> runCatching { ActivityRewardType.valueOf(k) }.getOrNull()?.let { it to v } }.toMap(),
                trackingState = runCatching { ActivityTrackingState.valueOf(trackingState) }.getOrDefault(ActivityTrackingState.UNTRACKABLE),
            )
        }

        companion object {
            fun from(info: ActivityInfo) = PersistedActivity(
                type = info.type.name,
                name = info.name,
                status = info.status.name,
                specialInfo = info.specialInfo,
                description = info.description,
                length = info.length?.name,
                distance = info.distance?.name,
                difficulty = info.difficulty?.name,
                levelReq = info.levelReq,
                levelReqFulfilled = info.levelReqFulfilled,
                professionReqs = info.professionReqs,
                questReqs = info.questReqs,
                rewards = info.rewards.mapKeys { it.key.name },
                trackingState = info.trackingState.name,
            )
        }
    }
}
