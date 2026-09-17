package opal.dev.overwatch.client

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object LootrunParticleFeature {

    @Volatile
    var taskCenter: Vec3? = null
        private set

    @Volatile
    var taskCenterAtMillis: Long = 0
        private set

    val match: EntityTracker.Match?
        get() {
            val center = taskCenter ?: return null
            if (LootrunModel.state == LootrunModel.State.NOT_RUNNING) return null
            if (System.currentTimeMillis() - taskCenterAtMillis > STALE_MILLIS) return null
            val box = AABB(center.x - 5.0, center.y, center.z - 5.0, center.x + 5.0, center.y + 2.0, center.z + 5.0)
            return EntityTracker.Match(anchor = null, blockBox = box, label = "Task", colorArgb = TASK_COLOR, throughWalls = true)
        }

    fun onParticle(x: Double, y: Double, z: Double) {
        if (!OverwatchConfig.current.lootrunTaskMarkerEnabled) return
        if (LootrunModel.state == LootrunModel.State.NOT_RUNNING) return
        val center = LootrunParticleVerifier.onPosition(x, y, z) ?: return
        taskCenter = center
        taskCenterAtMillis = System.currentTimeMillis()
    }

    private const val STALE_MILLIS = 120_000L
    private val TASK_COLOR = 0xFF55FFFF.toInt()
}
