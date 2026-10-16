package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object TitleScene {
    private const val FULL_BRIGHT = 15728880
    private const val SEAT_DROP = 0.3425f
    private const val STAND_DROP = 0.9625f
    private const val MARGIN = 80f
    private const val FADE_SECONDS = 0.7f

    private class Item(val id: Identifier, val drift: Float, val front: Boolean)

    private class Entry(val art: TitleArt, val baked: Baked, val items: List<Item>)

    private val entries = arrayOfNulls<Entry>(TITLE_ARTS.size)
    private var index = -1
    private var previous = -1
    private var fade = 1f
    private var skinLookup: Supplier<PlayerSkin>? = null
    private var figureFailed = false
    private var lastNanos = 0L

    val count: Int get() = TITLE_ARTS.size
    val currentIndex: Int get() = currentScene()
    val currentName: String get() = TITLE_ARTS[currentScene()].name

    private fun currentScene(): Int {
        if (index < 0) index = Math.floorMod(WynnOverhaulConfig.current.titleSceneIndex, count)
        return index
    }

    fun step(delta: Int) {
        val from = currentScene()
        previous = from
        index = Math.floorMod(from + delta, count)
        fade = 0f
        WynnOverhaulConfig.current.titleSceneIndex = index
        WynnOverhaulConfig.current.save()
    }

    private fun entry(i: Int): Entry {
        entries[i]?.let { return it }
        val art = TITLE_ARTS[i]
        val baked = art.bake()
        val split = baked.figure.behind.coerceIn(1, baked.layers.size)
        val base = "title/${art.name.lowercase().replace(' ', '_')}"
        val items = ArrayList<Item>()
        var pending: Raster? = null
        var pendingFront = false
        var counter = 0
        fun flush() {
            val raster = pending ?: return
            items += Item(upload(raster, "${base}_${counter++}"), 0f, pendingFront)
            pending = null
        }
        for ((n, layer) in baked.layers.withIndex()) {
            val front = n >= split
            if (layer.drift != 0f) {
                flush()
                items += Item(upload(layer.raster, "${base}_${counter++}"), layer.drift, front)
                continue
            }
            if (pending != null && pendingFront != front) flush()
            val target = pending ?: Raster(ART_W, ART_H).also {
                pending = it
                pendingFront = front
            }
            val src = layer.raster.px
            for (k in src.indices) {
                val c = src[k]
                if ((c ushr 24) != 0) target.blend(k % ART_W, k / ART_W, c)
            }
        }
        flush()
        return Entry(art, baked, items).also { entries[i] = it }
    }

    private fun upload(raster: Raster, name: String): Identifier {
        val image = NativeImage(ART_W, ART_H, false)
        val src = raster.px
        for (y in 0 until ART_H) for (x in 0 until ART_W) {
            val c = src[y * ART_W + x]
            image.setPixelABGR(x, y, (c and 0xFF00FF00.toInt()) or ((c shr 16) and 0xFF) or ((c and 0xFF) shl 16))
        }
        val id = Identifier.fromNamespaceAndPath("wynnoverhaul", name)
        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "wynnoverhaul title" }, image))
        return id
    }

    private class View(val graphics: GuiGraphicsExtractor, val width: Int, val height: Int, val sidebar: Int, val alpha: Float) {
        val scale = max(height / ART_H.toFloat(), (width + MARGIN) / ART_W)
        val originX = (width - ART_W * scale) / 2f + sidebar * 0.28f
        val originY = height - ART_H * scale
    }

    private class GuiFx(val view: View) : Fx {
        override fun layer(parallax: Float) {}

        private fun sx(x: Float) = (view.originX + x * view.scale).roundToInt()
        private fun sy(y: Float) = (view.originY + y * view.scale).roundToInt()

        override fun rect(x1: Float, y1: Float, x2: Float, y2: Float, argb: Int) {
            val a = (((argb ushr 24) * view.alpha).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)
            val l = sx(x1)
            val t = sy(y1)
            view.graphics.fill(l, t, max(l + 1, sx(x2)), max(t + 1, sy(y2)), a)
        }

        override fun glow(cx: Float, cy: Float, rx: Float, ry: Float, argb: Int, strength: Float) {
            val rings = 4
            val a0 = (argb ushr 24) * strength * view.alpha
            val cpx = sx(cx)
            val cpy = sy(cy)
            for (k in 0 until rings) {
                val f = 1f - k / rings.toFloat()
                val prx = rx * view.scale * f
                val pry = ry * view.scale * f
                val a = ((a0 / rings) * 1.1f).toInt().coerceIn(0, 255)
                if (a == 0) continue
                val color = (a shl 24) or (argb and 0xFFFFFF)
                var dy = -pry.toInt()
                while (dy <= pry.toInt()) {
                    val half = (sqrt(max(0f, 1f - (dy / pry) * (dy / pry))) * prx).toInt()
                    view.graphics.fill(cpx - half, cpy + dy, cpx + half, cpy + dy + 4, color)
                    dy += 4
                }
            }
        }

        override fun vgrad(x1: Float, y1: Float, x2: Float, y2: Float, top: Int, bottom: Int) {
            val l = sx(x1)
            val t = sy(y1)
            val scaled = { c: Int -> (((c ushr 24) * view.alpha).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF) }
            view.graphics.fillGradient(l, t, max(l + 1, sx(x2)), max(t + 1, sy(y2)), scaled(top), scaled(bottom))
        }

        override fun flash(argb: Int) {
            val a = (((argb ushr 24) * view.alpha).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)
            view.graphics.fill(0, 0, view.width, view.height, a)
        }
    }

    fun draw(graphics: GuiGraphicsExtractor, width: Int, height: Int, sidebar: Int) {
        val now = System.nanoTime()
        val t = now / 1_000_000_000.0
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastNanos = now
        if (fade < 1f) fade = (fade + dt / FADE_SECONDS).coerceAtMost(1f)
        val current = currentScene()
        if (fade < 1f && previous >= 0 && previous != current) drawScene(graphics, previous, width, height, sidebar, 1f, t, fade < 0.4f)
        drawScene(graphics, current, width, height, sidebar, if (previous >= 0 && fade < 1f) fade else 1f, t, fade >= 0.6f)
    }

    private fun drawScene(graphics: GuiGraphicsExtractor, i: Int, width: Int, height: Int, sidebar: Int, alpha: Float, t: Double, withFigure: Boolean) {
        val e = entry(i)
        val view = View(graphics, width, height, sidebar, alpha)
        val tint = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        val w = (ART_W * view.scale).roundToInt()
        val h = (ART_H * view.scale).roundToInt()
        val x = view.originX.roundToInt()
        val y = view.originY.roundToInt()
        for (item in e.items) if (!item.front) blitItem(graphics, item, x, y, w, h, t, tint)
        if (withFigure) drawFigure(graphics, view, e.baked.figure, t)
        for (item in e.items) if (item.front) blitItem(graphics, item, x, y, w, h, t, tint)
        e.art.effects(GuiFx(view), t)
    }

    private fun blitItem(graphics: GuiGraphicsExtractor, item: Item, x: Int, y: Int, w: Int, h: Int, t: Double, tint: Int) {
        val u = if (item.drift == 0f) 0f else ((t * item.drift) % ART_W).toFloat()
        graphics.blit(RenderPipelines.GUI_TEXTURED, item.id, x, y, u, 0f, w, h, ART_W, ART_H, ART_W, ART_H, tint)
    }

    private fun drawFigure(graphics: GuiGraphicsExtractor, view: View, figure: ArtFigure, t: Double) {
        if (figureFailed) return
        val client = Minecraft.getInstance()
        try {
            val lookup = skinLookup ?: client.skinManager.createLookup(client.gameProfile, false).also { skinLookup = it }
            val state = AvatarRenderState()
            state.skin = lookup.get()
            state.showHat = true
            state.showJacket = true
            state.showLeftPants = true
            state.showRightPants = true
            state.showLeftSleeve = true
            state.showRightSleeve = true
            state.isPassenger = figure.pose == ArtPose.SIT
            state.mainArm = HumanoidArm.RIGHT
            state.lightCoords = FULL_BRIGHT
            state.bodyRot = figure.bodyRot
            state.yRot = 0f
            state.xRot = when (figure.pose) {
                ArtPose.LOOK_UP -> -26f + 2f * sin(t * 0.5).toFloat()
                ArtPose.SIT -> 9f + 2f * sin(t * 0.9).toFloat()
                ArtPose.STAND -> 4f + 2f * sin(t * 0.6).toFloat()
            }
            state.scale = 1f
            state.ageScale = 1f
            state.ageInTicks = (t * 20.0).toFloat()
            state.boundingBoxWidth = 0.6f
            state.boundingBoxHeight = 1.8f
            state.eyeHeight = 1.62f
            state.outlineColor = 0
            state.shadowPieces.clear()
            val size = (figure.height * view.scale / 1.8f).coerceAtLeast(8f)
            val x = view.originX + figure.x * view.scale
            val ground = view.originY + figure.y * view.scale
            val drop = if (figure.pose == ArtPose.SIT) SEAT_DROP else STAND_DROP
            val cy = ground - size * drop
            graphics.entity(
                state, size, Vector3f(0f, 0.9f + 0.0625f, 0f), Quaternionf().rotateZ(PI.toFloat()), Quaternionf(),
                (x - size * 1.6f).toInt(), (cy - size * 1.3f).toInt(), (x + size * 1.8f).toInt(), (cy + size * 1.3f).toInt(),
            )
        } catch (e: Throwable) {
            figureFailed = true
        }
    }
}
