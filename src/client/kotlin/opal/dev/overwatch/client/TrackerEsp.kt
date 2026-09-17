package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import opal.dev.overwatch.Overwatch
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.floor

object TrackerEsp {

    class Waypoint(
        val onScreen: Boolean,
        val ndcX: Float,
        val ndcY: Float,
        val distance: Float,
        val label: String,
        val argb: Int,
        val isChest: Boolean,
    )

    @Volatile
    var waypoints: List<Waypoint> = emptyList()
        private set

    private var loggedError = false

    private class LosEntry(var visible: Boolean, var atNanos: Long)
    private val losCache = HashMap<Long, LosEntry>()
    private val liveLosKeys = HashSet<Long>()

    fun capture(ctx: LevelRenderContext) {
        val config = OverwatchConfig.current
        val client = Minecraft.getInstance()
        if (client.level == null || ScreenGate.blockedByScreen(client)) {
            if (waypoints.isNotEmpty()) waypoints = emptyList()
            return
        }
        val trackerActive = config.trackerEnabled && config.trackerWaypointsEnabled
        val liveMatches = if (trackerActive) EntityTracker.current else emptyList()
        val discoveredMatches = if (trackerActive) EntityTracker.discovered else emptyList()
        val questMatches = QuestBeaconTracker.current
        val lootrunMatches = if (config.lootrunEnabled && LootrunModel.state != LootrunModel.State.NOT_RUNNING) {
            val playerPos = client.player?.position()
            val pathMatches = if (playerPos != null) LootrunRecorder.renderMatches(playerPos) else emptyList()
            LootrunBeaconTracker.current + listOfNotNull(LootrunParticleFeature.match) + pathMatches
        } else {
            emptyList()
        }
        if (liveMatches.isEmpty() && discoveredMatches.isEmpty() && questMatches.isEmpty() && lootrunMatches.isEmpty()) {
            if (waypoints.isNotEmpty()) waypoints = emptyList()
            return
        }

        try {
            val level = client.level ?: return
            val player = client.player
            val cam = ctx.levelState().cameraRenderState
            val camPos = cam.pos ?: return
            val vp = Matrix4f(cam.projectionMatrix).mul(cam.viewRotationMatrix)
            val clip = Vector4f()

            val now = System.nanoTime()
            liveLosKeys.clear()
            val out = ArrayList<Waypoint>(liveMatches.size + discoveredMatches.size + questMatches.size + lootrunMatches.size)

            for (match in liveMatches) {
                projectWaypoint(match, level, player, camPos, vp, clip, now, out)
            }
            if (discoveredMatches.isNotEmpty() && player != null) {
                val guidanceRangeSqr = config.trackerDiscoveredChestGuidanceRange * config.trackerDiscoveredChestGuidanceRange
                for (match in discoveredMatches) {
                    if (player.distanceToSqr(match.center()) > guidanceRangeSqr) continue
                    projectWaypoint(match, level, player, camPos, vp, clip, now, out)
                }
            }
            for (match in questMatches) {
                projectWaypoint(match, level, player, camPos, vp, clip, now, out)
            }
            for (match in lootrunMatches) {
                projectWaypoint(match, level, player, camPos, vp, clip, now, out)
            }

            waypoints = out
            if (losCache.size > liveLosKeys.size) losCache.keys.retainAll(liveLosKeys)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Entity tracker ESP projection failed", t)
            }
        }
    }

    private fun projectWaypoint(
        match: EntityTracker.Match,
        level: ClientLevel,
        player: LocalPlayer?,
        camPos: Vec3,
        vp: Matrix4f,
        clip: Vector4f,
        now: Long,
        out: MutableList<Waypoint>,
    ) {
        if (match.anchor?.isAlive == false) return
        val center = match.center()
        if (!match.throughWalls) {
            val key = losKey(match)
            liveLosKeys.add(key)
            if (!cachedLineOfSight(key, level, camPos, center, now)) return
        }

        val box = match.box()
        val ax = (box.minX + box.maxX) * 0.5
        val az = (box.minZ + box.maxZ) * 0.5
        val ay = box.maxY + WAYPOINT_Y_OFFSET

        vp.transform((ax - camPos.x).toFloat(), (ay - camPos.y).toFloat(), (az - camPos.z).toFloat(), 1.0f, clip)

        var w = clip.w
        var flip = false
        if (w <= EPS) {
            flip = true
            w = if (w > -EPS) -EPS else w
        }
        var ndcX = clip.x / w
        var ndcY = clip.y / w
        if (flip) {
            ndcX = -ndcX
            ndcY = -ndcY
        }
        val onScreen = !flip && ndcX in -EDGE..EDGE && ndcY in -EDGE..EDGE
        val distance = player?.position()?.distanceTo(center)?.toFloat() ?: 0f

        out.add(Waypoint(onScreen, ndcX, ndcY, distance, match.label, match.colorArgb, isChest = match.anchor == null))
    }

    private fun losKey(match: EntityTracker.Match): Long {
        match.anchor?.let { return it.uuid.mostSignificantBits xor it.uuid.leastSignificantBits }
        val b = match.box()
        return BlockPos(floor(b.minX).toInt(), floor(b.minY).toInt(), floor(b.minZ).toInt()).asLong()
    }

    private fun cachedLineOfSight(key: Long, level: ClientLevel, from: Vec3, to: Vec3, now: Long): Boolean {
        val entry = losCache[key]
        if (entry != null && now - entry.atNanos < LOS_REFRESH_NANOS) return entry.visible
        val visible = hasLineOfSight(level, from, to)
        if (entry != null) {
            entry.visible = visible
            entry.atNanos = now
        } else {
            losCache[key] = LosEntry(visible, now)
        }
        return visible
    }

    private fun hasLineOfSight(level: ClientLevel, from: Vec3, to: Vec3): Boolean {
        val hit = level.clip(
            ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()),
        )
        return hit.type == HitResult.Type.MISS || hit.location.distanceToSqr(to) < 1.0
    }

    private const val LOS_REFRESH_NANOS = 150_000_000L
    private const val EPS = 1.0e-4f
    private const val EDGE = 0.97f
    private const val WAYPOINT_Y_OFFSET = 0.35
}
