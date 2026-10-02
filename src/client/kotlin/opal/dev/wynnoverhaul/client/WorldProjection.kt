package opal.dev.wynnoverhaul.client

import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.sqrt

object WorldProjection {
    class Ndc(val x: Float, val y: Float, val onScreen: Boolean)

    fun project(camPos: Vec3, viewProjection: Matrix4f, view: Matrix4f, clip: Vector4f, x: Double, y: Double, z: Double): Ndc {
        var dx = x - camPos.x
        var dy = y - camPos.y
        var dz = z - camPos.z
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len > MAX_DISTANCE) {
            val k = MAX_DISTANCE / len
            dx *= k
            dy *= k
            dz *= k
        }

        view.transform(dx.toFloat(), dy.toFloat(), dz.toFloat(), 1.0f, clip)
        val behind = -clip.z < FORWARD_MIN
        viewProjection.transform(dx.toFloat(), dy.toFloat(), dz.toFloat(), 1.0f, clip)

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
        val onScreen = !behind && !flip && ndcX >= -1f && ndcX < 1f && ndcY >= -1f && ndcY < 1f
        return Ndc(ndcX, ndcY, onScreen)
    }

    private const val EPS = 1.0e-4f
    private const val FORWARD_MIN = 0.05f
    private const val MAX_DISTANCE = 250000.0
}
