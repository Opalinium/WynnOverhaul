package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import org.joml.Quaternionf
import org.joml.Vector3f
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.min

class WynnOverhaulAnimationPreviewScreen(parent: Screen? = null) : OwScreen(Component.literal("Animation Preview"), parent) {
    override val panelWidth: Int get() = (width * 0.82).toInt().coerceIn(480, 900).coerceAtMost((width - 20).coerceAtLeast(300))
    override val panelHeight: Int get() = (height * 0.85).toInt().coerceIn(300, 540)

    private var style: String = ""
    private var spell: String? = null
    private var progress = 0f
    private var playing = true
    private var turn = 0.0
    private var speed = 1.0
    private var dragging = false
    private var info: WeaponAnimations.PreviewInfo? = null
    private var lastFrame = 0L
    private var progressSlider: OwSlider? = null
    private val styles = WeaponAnimations.previewStyles()
    private val spells = listOf<String?>(null) + WeaponAnimations.spellNames()

    private val viewLeft: Int get() = contentLeft
    private val viewWidth: Int get() = (contentWidth * VIEW_SHARE).toInt()
    private val controlsLeft: Int get() = viewLeft + viewWidth + OwTheme.PAD
    private val controlsWidth: Int get() = contentLeft + contentWidth - controlsLeft

    override fun init() {
        super.init()
        if (style.isEmpty() || style !in styles) style = WeaponAnimations.heldStyleKey()?.takeIf { it in styles } ?: styles.firstOrNull().orEmpty()
        if (info == null) restart()
        val left = controlsLeft
        val w = controlsWidth
        val rows = ArrayList<Pair<AbstractWidget, Int>>()
        rows += Selector(left, 0, w, OwTheme.ROW_H - 2, "Style", { WeaponAnimations.styleLabel(style) }) { step ->
            style = styles[Math.floorMod(styles.indexOf(style) + step, styles.size)]
            restart()
        } to OwTheme.ROW_H
        rows += Selector(left, 0, w, OwTheme.ROW_H - 2, "Animation", { spellLabel(spell) }) { step ->
            spell = spells[Math.floorMod(spells.indexOf(spell) + step, spells.size)]
            restart()
        } to OwTheme.ROW_H
        val slider = OwSlider(left, 0, w, OwTheme.ROW_H - 2, 0.0, 1.0, 2, progress.toDouble(), "Progress") {
            progress = it.toFloat()
            playing = false
            WeaponAnimations.setPreviewProgress(progress)
        }
        progressSlider = slider
        rows += slider to OwTheme.ROW_H
        rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal(if (playing) "Pause" else "Play"), accent = playing) {
            playing = !playing
            rebuildWidgets()
        } to OwTheme.ROW_H
        rows += OwSlider(left, 0, w, OwTheme.ROW_H - 2, MIN_SPEED, MAX_SPEED, 2, speed, "Playback speed") { speed = it } to OwTheme.ROW_H
        rows += OwLabel(left, 0, w, 12, "Drag the figure to rotate it.", OwTheme.TEXT_FAINT) to 14
        rows += OwLabel(left, 0, w, 12, "Movesets play as the style chosen here, with the weapon you hold.", OwTheme.TEXT_FAINT) to 14
        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    private fun spellLabel(name: String?): String {
        if (name == null) return "Attack chain"
        val key = AnimationLibrary.tables().spellKeys[name] ?: name
        val base = key.removePrefix("SPELL:").split('_').joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        return if (WeaponAnimations.hasOwnSpell(style, name)) base else "$base (generic)"
    }

    private fun restart() {
        progress = 0f
        info = WeaponAnimations.beginPreview(style, spell)
        WeaponAnimations.setPreviewProgress(0f)
        progressSlider?.setNormalized(0.0)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        advance()
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        drawViewport(graphics)
    }

    private fun advance() {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastFrame = now
        val total = info?.seconds ?: return
        if (playing) {
            progress += dt * speed.toFloat() / total
            if (progress >= 1f) progress -= 1f
            progressSlider?.setNormalized(progress.toDouble())
        }
        WeaponAnimations.setPreviewProgress(progress)
    }

    private fun drawViewport(graphics: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font
        val x1 = viewLeft
        val y1 = contentTop
        val x2 = viewLeft + viewWidth
        val y2 = contentBottom
        OwShape.well(graphics, x1, y1, x2 - x1, y2 - y1, 2, OwTheme.PANEL)
        val player = Minecraft.getInstance().player ?: return
        val size = (min(x2 - x1, y2 - y1) * ENTITY_SHARE).toInt()
        drawFigure(graphics, player, x1 + 1, y1 + 1, x2 - 1, y2 - 1, size)
        val current = info
        val ready = WynnOverhaulConfig.current.weaponAnimationsEnabled && WynnOverhaulGate.isInGame()
        val text = when {
            !ready -> "Join Wynncraft with weapon animations on to preview."
            current == null -> "Nothing to preview for this selection."
            else -> {
                val stroke = current.labels.getOrNull(WeaponAnimations.previewStroke()).orEmpty()
                "%s  %.2fs / %.2fs".format(stroke, progress * current.seconds, current.seconds)
            }
        }
        graphics.text(font, truncateToWidth(font, text, x2 - x1 - 12), x1 + 6, y2 - 14, OwTheme.TEXT_DIM)
    }

    private fun drawFigure(graphics: GuiGraphicsExtractor, player: LocalPlayer, x1: Int, y1: Int, x2: Int, y2: Int, size: Int) {
        val state = Minecraft.getInstance().entityRenderDispatcher.getRenderer(player).createRenderState(player, 1f)
        state.shadowPieces.clear()
        state.outlineColor = 0
        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f + Math.toDegrees(turn).toFloat()
            state.yRot = 0f
            state.xRot = 0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1f
        }
        val translation = Vector3f(0f, state.boundingBoxHeight / 2f + ENTITY_OFFSET, 0f)
        graphics.entity(state, size.toFloat(), translation, Quaternionf().rotateZ(Math.PI.toFloat()), Quaternionf(), x1, y1, x2, y2)
    }

    private fun inViewport(x: Double, y: Double): Boolean =
        x >= viewLeft && x < viewLeft + viewWidth && y >= contentTop && y < contentBottom

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (event.button() == 0 && inViewport(event.x(), event.y())) {
            dragging = true
            return true
        }
        return super.mouseClicked(event, doubled)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) {
            turn += dx * DRAG_RADIANS
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    override fun removed() {
        WeaponAnimations.endPreview()
        super.removed()
    }

    private class Selector(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val title: String,
        private val value: () -> String,
        private val onStep: (Int) -> Unit,
    ) : AbstractWidget(x, y, width, height, Component.literal(title)) {
        override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
            val font = Minecraft.getInstance().font
            val border = if (isHovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE
            OwShape.raised(graphics, x, y, width, height, OwTheme.TILE_BG, border, hovered = isHovered)
            graphics.text(font, "<", x + 7, y + (height - 8) / 2, OwTheme.TEXT_DIM)
            graphics.text(font, ">", x + width - 7 - font.width(">"), y + (height - 8) / 2, OwTheme.TEXT_DIM)
            val text = truncateToWidth(font, "$title: ${value()}", width - ARROW_W * 2)
            graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, OwTheme.TEXT)
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            if (!active || !visible || !isMouseOver(event.x(), event.y())) return false
            val back = event.button() == 1 || event.x() < x + ARROW_W
            onStep(if (back) -1 else 1)
            return true
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }

    private companion object {
        const val VIEW_SHARE = 0.5
        const val ENTITY_SHARE = 0.24
        const val ENTITY_OFFSET = 0.0625f
        const val DRAG_RADIANS = 0.012
        const val ARROW_W = 14
        const val MIN_SPEED = 0.1
        const val MAX_SPEED = 2.0
    }
}
