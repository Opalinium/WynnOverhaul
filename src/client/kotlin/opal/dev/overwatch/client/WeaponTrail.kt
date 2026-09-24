package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.sprite.Material
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow

object WeaponTrail {


    private const val LIFE_NANOS = 260_000_000L
    private const val GHOST_GAP_NANOS = 30_000_000L
    private const val GHOST_COUNT = 6
    private const val MAX_SAMPLES = 40
    private const val MIN_INTERVAL_NANOS = 6_000_000L
    private const val BASE_ALPHA = 0.34f
    private const val TINT = 0xE4EAF2
    private const val MIN_MOVE = 5f
    private const val SPRITE_SIZE = 16
    private const val SPRITE_ANGLE = -0.7853982f
    private const val SPRITE_DIAGONAL = 1.4142135f
    private const val MIN_SIZE = 8f
    private const val MAX_SIZE = 320f
    private const val EPS = 1.0e-4f

    private class Sample(
        val nanos: Long,
        val tx: Double,
        val ty: Double,
        val tz: Double,
        val hx: Double,
        val hy: Double,
        val hz: Double,
        val strength: Float,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastNanos = 0L

    private val viewProjection = Matrix4f()
    private val viewInverse = Matrix4f()
    private var camX = 0.0
    private var camY = 0.0
    private var camZ = 0.0
    private var cameraReady = false

    private val clip = Vector4f()
    private val scratch = Vector3f()
    private val screen = FloatArray(4)

    private var sprite: TextureAtlasSprite? = null
    private var spriteKey: String? = null

    fun init() {
        LevelRenderEvents.END_MAIN.register { ctx -> capture(ctx) }
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "weapon_trail"),
            HudElement { graphics, _ -> draw(graphics) },
        )
    }

    private fun capture(ctx: LevelRenderContext) {
        val cam = ctx.levelState().cameraRenderState
        val pos = cam.pos ?: return
        camX = pos.x
        camY = pos.y
        camZ = pos.z
        viewProjection.set(cam.projectionMatrix).mul(cam.viewRotationMatrix)
        viewInverse.set(cam.viewRotationMatrix).invert()
        cameraReady = true
    }

    fun setWeapon(stack: ItemStack) {
        val key = WeaponAnimationRegistry.keyOf(stack)
        if (key == spriteKey && sprite != null) return
        if (key != spriteKey) samples.clear()
        spriteKey = key
        sprite = try {
            val client = Minecraft.getInstance()
            val player = client.player
            if (player == null) {
                null
            } else {
                val state = ItemStackRenderState()
                client.itemModelResolver.updateForLiving(state, stack, ItemDisplayContext.GUI, player)
                pickSprite(state)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private val layersField = ItemStackRenderState::class.java.getDeclaredField("layers").also { it.isAccessible = true }
    private val countField = ItemStackRenderState::class.java.getDeclaredField("activeLayerCount").also { it.isAccessible = true }
    private val particleField = ItemStackRenderState.LayerRenderState::class.java.getDeclaredField("particleMaterial").also { it.isAccessible = true }

    private fun coverage(s: TextureAtlasSprite): Float {
        val c = s.contents()
        var solid = 0
        for (y in 0 until c.height()) for (x in 0 until c.width()) if (!c.isTransparent(0, x, y)) solid++
        return solid.toFloat() / (c.width() * c.height()).coerceAtLeast(1)
    }

    private fun pickSprite(state: ItemStackRenderState): TextureAtlasSprite? {
        val layers = layersField.get(state) as Array<*>
        val count = countField.getInt(state)
        val found = LinkedHashSet<TextureAtlasSprite>()
        for (i in 0 until minOf(count, layers.size)) {
            val layer = layers[i] as? ItemStackRenderState.LayerRenderState ?: continue
            for (quad in layer.prepareQuadList()) found += quad.materialInfo().sprite()
            (particleField.get(layer) as? Material.Baked)?.let { found += it.sprite() }
        }
        val scored = found.filter { it.contents().name() != MissingTextureAtlasSprite.getLocation() }.map { it to coverage(it) }
        return scored.filter { it.second > 0.02f }.minByOrNull { it.second }?.first ?: scored.firstOrNull()?.first
    }

    fun clear() {
        samples.clear()
    }

    fun addWorld(tx: Double, ty: Double, tz: Double, hx: Double, hy: Double, hz: Double, strength: Float) {
        val now = System.nanoTime()
        if (now - lastNanos < MIN_INTERVAL_NANOS) return
        lastNanos = now
        samples.addLast(Sample(now, tx, ty, tz, hx, hy, hz, strength))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    fun addView(tailX: Float, tailY: Float, tailZ: Float, tipX: Float, tipY: Float, tipZ: Float, strength: Float) {
        if (!cameraReady) return
        scratch.set(tailX, tailY, tailZ).mulPosition(viewInverse)
        val ax = camX + scratch.x
        val ay = camY + scratch.y
        val az = camZ + scratch.z
        scratch.set(tipX, tipY, tipZ).mulPosition(viewInverse)
        addWorld(ax, ay, az, camX + scratch.x, camY + scratch.y, camZ + scratch.z, strength)
    }

    private fun draw(graphics: GuiGraphicsExtractor) {
        if (samples.isEmpty() || !cameraReady) return
        val config = OverwatchConfig.current
        if (!config.weaponAnimationTrail) {
            samples.clear()
            return
        }
        val now = System.nanoTime()
        while (samples.isNotEmpty() && now - samples.first().nanos > LIFE_NANOS) samples.removeFirst()
        val texture = sprite ?: return
        if (samples.size < 2) return

        val w = graphics.guiWidth().toFloat()
        val h = graphics.guiHeight().toFloat()
        val intensity = config.weaponAnimationTrailIntensity.toFloat()
        val newest = samples.last()
        var lastX = Float.NaN
        var lastY = Float.NaN
        if (project(newest, w, h)) {
            lastX = screen[2]
            lastY = screen[3]
        }

        var cursor = samples.size - 1
        var ghost = 1
        while (ghost <= GHOST_COUNT && cursor >= 0) {
            val wanted = ghost * GHOST_GAP_NANOS
            while (cursor >= 0 && now - samples[cursor].nanos < wanted) cursor--
            if (cursor < 0) break
            val s = samples[cursor]
            if (project(s, w, h)) {
                val dx = screen[2] - screen[0]
                val dy = screen[3] - screen[1]
                val length = hypot(dx, dy)
                val moved = if (lastX.isNaN()) MIN_MOVE else hypot(screen[2] - lastX, screen[3] - lastY)
                if (length > 4f && moved >= MIN_MOVE) {
                    val fade = (1f - ghost.toFloat() / (GHOST_COUNT + 1)).pow(1.3f)
                    val age = ((now - s.nanos).toFloat() / LIFE_NANOS).coerceIn(0f, 1f)
                    val alpha = (fade * (1f - age) * BASE_ALPHA * s.strength * intensity).coerceIn(0f, 1f)
                    if (alpha > 0.02f) {
                        drawGhost(graphics, texture, dx, dy, length, (screen[0] + screen[2]) * 0.5f, (screen[1] + screen[3]) * 0.5f, alpha)
                        lastX = screen[2]
                        lastY = screen[3]
                    }
                }
            }
            ghost++
        }
    }

    private fun drawGhost(graphics: GuiGraphicsExtractor, texture: TextureAtlasSprite, dx: Float, dy: Float, length: Float, cx: Float, cy: Float, alpha: Float) {
        val size = (length / SPRITE_DIAGONAL).coerceIn(MIN_SIZE, MAX_SIZE)
        val angle = atan2(dy, dx) - SPRITE_ANGLE
        val color = ((alpha * 255f).toInt() shl 24) or TINT
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(cx, cy)
        pose.rotate(angle)
        pose.scale(size / SPRITE_SIZE)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, -SPRITE_SIZE / 2, -SPRITE_SIZE / 2, SPRITE_SIZE, SPRITE_SIZE, color)
        pose.popMatrix()
    }

    private fun project(s: Sample, w: Float, h: Float): Boolean {
        if (!projectPoint(s.tx, s.ty, s.tz, w, h, 0)) return false
        return projectPoint(s.hx, s.hy, s.hz, w, h, 2)
    }

    private fun projectPoint(x: Double, y: Double, z: Double, w: Float, h: Float, at: Int): Boolean {
        viewProjection.transform((x - camX).toFloat(), (y - camY).toFloat(), (z - camZ).toFloat(), 1f, clip)
        if (clip.w <= EPS) return false
        screen[at] = (clip.x / clip.w * 0.5f + 0.5f) * w
        screen[at + 1] = (0.5f - clip.y / clip.w * 0.5f) * h
        return true
    }
}
