package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.text.SimpleDateFormat
import java.util.Date

object LootrunRecorder {

    @Volatile
    var recordedPointCount: Int = 0
        private set

    @Volatile
    var activePath: LootrunPath? = null

    private var lastState = LootrunModel.State.NOT_RUNNING
    private val points = ArrayList<Vec3>()

    fun tick(client: Minecraft) {
        val config = OverwatchConfig.current
        val state = LootrunModel.state

        if (config.lootrunEnabled && config.lootrunRecorderEnabled && state != LootrunModel.State.NOT_RUNNING) {
            val pos = client.player?.position()
            if (pos != null) {
                val last = points.lastOrNull()
                if (last == null || last.distanceToSqr(pos) >= MIN_DISTANCE_SQR) {
                    points.add(pos)
                    recordedPointCount = points.size
                }
            }
        }

        if (lastState != LootrunModel.State.NOT_RUNNING && state == LootrunModel.State.NOT_RUNNING) {
            finishRecording(client)
        }
        lastState = state
    }

    private fun finishRecording(client: Minecraft) {
        if (points.size >= MIN_POINTS_TO_SAVE) {
            val dimension = client.level?.dimension()?.identifier()?.toString() ?: "unknown"
            val name = "Run " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            LootrunPathStore.save(LootrunPath(name, dimension, points.map { LootrunPathPoint(it.x, it.y, it.z) }, System.currentTimeMillis()))
        }
        points.clear()
        recordedPointCount = 0
    }

    fun renderMatches(playerPos: Vec3): List<EntityTracker.Match> {
        val path = activePath ?: return emptyList()
        val vecs = path.points.map { Vec3(it.x, it.y, it.z) }
        val nearest = vecs.sortedBy { it.distanceToSqr(playerPos) }.take(MAX_RENDER_POINTS)
        return nearest.map { pos ->
            val box = AABB(pos.x - 0.15, pos.y, pos.z - 0.15, pos.x + 0.15, pos.y + 0.5, pos.z + 0.15)
            EntityTracker.Match(anchor = null, blockBox = box, label = "", colorArgb = PATH_COLOR, throughWalls = true)
        }
    }

    private const val MIN_DISTANCE_SQR = 2.25
    private const val MIN_POINTS_TO_SAVE = 10
    private const val MAX_RENDER_POINTS = 200
    private val PATH_COLOR = 0xFFB388FF.toInt()
}
