package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelPart
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object LimbBend {
    private const val STRIPS = 8
    private const val ZONE = 3f
    private const val STALE_NANOS = 120_000_000L
    private const val UNIT = 16f
    private const val EDGE = 1e-4f

    private var angle = 0f
    private var fullCos = 1f
    private var fullSin = 0f
    private val normalIn = Vector3f()
    private val normalOut = Vector3f()
    private val position = Vector3f()

    @JvmStatic
    fun configured(): Boolean {
        val config = WynnOverhaulConfig.current
        return config.locomotionEnabled || config.weaponAnimationsEnabled
    }

    @JvmStatic
    fun select(bend: Float, stamp: Long) {
        if (bend == 0f || !configured() || System.nanoTime() - stamp >= STALE_NANOS) {
            angle = 0f
            return
        }
        angle = bend
        fullCos = cos(bend)
        fullSin = sin(bend)
    }

    private const val HAND_JOINT = 4f
    private const val SPINE_EPS = 0.0005f

    private var spineOn = false
    private val spineP = FloatArray((Trunk.SEGMENTS + 1) * 3)
    private val spineR = FloatArray((Trunk.SEGMENTS + 1) * 9)

    @JvmStatic
    fun selectSpine(twist: Float, curl: Float, bend: Float, stamp: Long) {
        if (!configured() || System.nanoTime() - stamp >= STALE_NANOS ||
            (abs(twist) < SPINE_EPS && abs(curl) < SPINE_EPS && abs(bend) < SPINE_EPS)
        ) {
            spineOn = false
            return
        }
        Trunk.fillChain(twist, curl, bend, spineP, spineR)
        spineOn = true
    }

    private fun compileSpine(polygons: Array<ModelPart.Polygon>, cube: ModelPart.Cube, pose: PoseStack.Pose, buffer: VertexConsumer, light: Int, overlay: Int, color: Int): Boolean {
        val top = cube.minY
        val bottom = cube.maxY
        val height = bottom - top
        if (abs(height - Trunk.LENGTH) > 0.01f) return false
        val cx = (cube.minX + cube.maxX) * 0.5f
        val cz = (cube.minZ + cube.maxZ) * 0.5f
        val seg = height / Trunk.SEGMENTS
        val matrix = pose.pose()
        for (polygon in polygons) {
            val n = polygon.normal()
            for (vertex in polygon.vertices()) {
                val u = ((bottom - vertex.y()) / height).coerceIn(0f, 1f)
                val j = (u * Trunk.SEGMENTS + 0.5f).toInt().coerceIn(0, Trunk.SEGMENTS)
                val ox = vertex.x() - cx
                val oy = vertex.y() - (bottom - j * seg)
                val oz = vertex.z() - cz
                val r = j * 9
                val px = cx + spineP[j * 3] + spineR[r] * ox + spineR[r + 1] * oy + spineR[r + 2] * oz
                val py = spineP[j * 3 + 1] - Trunk.HIP + bottom + spineR[r + 3] * ox + spineR[r + 4] * oy + spineR[r + 5] * oz
                val pz = cz + spineP[j * 3 + 2] + spineR[r + 6] * ox + spineR[r + 7] * oy + spineR[r + 8] * oz
                matrix.transformPosition(px / UNIT, py / UNIT, pz / UNIT, position)
                normalIn.set(
                    spineR[r] * n.x() + spineR[r + 1] * n.y() + spineR[r + 2] * n.z(),
                    spineR[r + 3] * n.x() + spineR[r + 4] * n.y() + spineR[r + 5] * n.z(),
                    spineR[r + 6] * n.x() + spineR[r + 7] * n.y() + spineR[r + 8] * n.z(),
                )
                pose.transformNormal(normalIn, normalOut)
                buffer.addVertex(
                    position.x, position.y, position.z, color, vertex.u(), vertex.v(),
                    overlay, light, normalOut.x, normalOut.y, normalOut.z,
                )
            }
        }
        return true
    }

    @JvmStatic
    fun freshBend(part: ModelPart): Float {
        val holder = (part as Any) as BendHolder
        val bend = holder.limbBend
        return if (bend != 0f && System.nanoTime() - holder.limbBendStamp < STALE_NANOS) bend else 0f
    }

    @JvmStatic
    fun applyHandFrame(part: ModelPart, stack: PoseStack) {
        if (!configured()) return
        val bend = freshBend(part)
        if (bend == 0f) return
        stack.translate(0f, HAND_JOINT / UNIT, 0f)
        stack.mulPose(Axis.XP.rotation(bend))
        stack.translate(0f, -HAND_JOINT / UNIT, 0f)
    }

    @JvmStatic
    fun subdivide(polygons: Array<ModelPart.Polygon>): Array<ModelPart.Polygon> {
        val out = ArrayList<ModelPart.Polygon>(polygons.size + STRIPS * 4)
        for (polygon in polygons) {
            val n = polygon.normal()
            val vertices = polygon.vertices()
            if (abs(n.y()) > 0.5f || vertices.size != 4) {
                out += polygon
                continue
            }
            val partner = IntArray(4) { -1 }
            for (i in 0 until 4) {
                for (j in 0 until 4) {
                    if (i != j && abs(vertices[i].x() - vertices[j].x()) < EDGE && abs(vertices[i].z() - vertices[j].z()) < EDGE &&
                        abs(vertices[i].y() - vertices[j].y()) > EDGE
                    ) {
                        partner[i] = j
                    }
                }
            }
            if (partner.any { it < 0 }) {
                out += polygon
                continue
            }
            val top = BooleanArray(4) { vertices[it].y() < vertices[partner[it]].y() }
            for (s in 0 until STRIPS) {
                val t0 = s.toFloat() / STRIPS
                val t1 = (s + 1).toFloat() / STRIPS
                val strip = Array(4) { i ->
                    if (top[i]) lerp(vertices[i], vertices[partner[i]], t0) else lerp(vertices[partner[i]], vertices[i], t1)
                }
                out += ModelPart.Polygon(strip, n)
            }
        }
        return out.toTypedArray()
    }

    private fun lerp(a: ModelPart.Vertex, b: ModelPart.Vertex, t: Float): ModelPart.Vertex =
        ModelPart.Vertex(
            a.x() + (b.x() - a.x()) * t,
            a.y() + (b.y() - a.y()) * t,
            a.z() + (b.z() - a.z()) * t,
            a.u() + (b.u() - a.u()) * t,
            a.v() + (b.v() - a.v()) * t,
        )

    @JvmStatic
    fun compile(polygons: Array<ModelPart.Polygon>, cube: ModelPart.Cube, pose: PoseStack.Pose, buffer: VertexConsumer, light: Int, overlay: Int, color: Int): Boolean {
        if (spineOn && compileSpine(polygons, cube, pose, buffer, light, overlay, color)) return true
        val bend = angle
        if (bend == 0f) return false
        val jointY = (cube.minY + cube.maxY) * 0.5f
        val jointZ = (cube.minZ + cube.maxZ) * 0.5f
        val matrix = pose.pose()
        val lowY = jointY - ZONE * 0.5f
        val highY = jointY + ZONE * 0.5f
        for (polygon in polygons) {
            val n = polygon.normal()
            var cachedState = -1
            for (vertex in polygon.vertices()) {
                val vy = vertex.y()
                val state = if (vy <= lowY) 0 else if (vy >= highY) 1 else 2
                val c: Float
                val s: Float
                when (state) {
                    0 -> {
                        c = 1f
                        s = 0f
                    }
                    1 -> {
                        c = fullCos
                        s = fullSin
                    }
                    else -> {
                        val u = (vy - jointY) / ZONE + 0.5f
                        val theta = bend * u * u * (3f - 2f * u)
                        c = cos(theta)
                        s = sin(theta)
                    }
                }
                if (state == 0) {
                    matrix.transformPosition(vertex.x() / UNIT, vy / UNIT, vertex.z() / UNIT, position)
                } else {
                    val ry = vy - jointY
                    val rz = vertex.z() - jointZ
                    matrix.transformPosition(
                        vertex.x() / UNIT,
                        (jointY + ry * c - rz * s) / UNIT,
                        (jointZ + ry * s + rz * c) / UNIT,
                        position,
                    )
                }
                if (state == 2 || state != cachedState) {
                    normalIn.set(n.x(), n.y() * c - n.z() * s, n.y() * s + n.z() * c)
                    pose.transformNormal(normalIn, normalOut)
                    cachedState = state
                }
                buffer.addVertex(
                    position.x, position.y, position.z, color, vertex.u(), vertex.v(),
                    overlay, light, normalOut.x, normalOut.y, normalOut.z,
                )
            }
        }
        return true
    }
}
