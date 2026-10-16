package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import java.util.WeakHashMap
import kotlin.math.min

object MenuSkin {
    private const val TILE_H = 22
    private const val GAP = 6
    private const val PAD = 16
    private const val WIDE_MIN = 150
    private const val SMALL_MAX = 60
    private const val SMALL_SIZE = 22
    private const val OPAQUE = 0xFF000000.toInt()
    private const val WORDMARK = "WYNNOVERHAUL"
    private const val WYNNCRAFT_ADDRESS = "play.wynncraft.com"
    private const val PLAY_H = 34
    private const val LINK_H = 16
    private const val BRANDING_CLEARANCE = 22

    private enum class Kind { TITLE, PAUSE }

    private class Slot(
        val widget: AbstractWidget?,
        val label: Component,
        val action: (() -> Unit)?,
        var x: Int = 0,
        var y: Int = 0,
        var w: Int = 0,
        var h: Int = TILE_H,
        val accent: Boolean = false,
        val subtle: Boolean = false,
    ) {
        fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private class State(
        val token: Any,
        val kind: Kind,
        val slots: List<Slot>,
        val panelX: Int,
        val panelY: Int,
        val panelW: Int,
        val panelH: Int,
        val headline: String,
    )

    private val states = WeakHashMap<Screen, State>()

    @JvmStatic
    fun hidesTitleBranding(): Boolean =
        WynnOverhaulConfig.current.customMenusEnabled && Minecraft.getInstance().gui.screen() is TitleScreen

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, width, height ->
            if (!WynnOverhaulConfig.current.customMenusEnabled) {
                states.remove(screen)
                return@register
            }
            val kind = when (screen) {
                is TitleScreen -> Kind.TITLE
                is PauseScreen -> Kind.PAUSE
                else -> return@register
            }
            val state = layout(screen, kind, width, height)
            states[screen] = state
            val token = state.token
            ScreenEvents.afterBackground(screen).register { _, graphics, mouseX, _, _ ->
                states[screen]?.takeIf { it.token === token }?.let { drawBackdrop(graphics, it, width, height, mouseX) }
            }
            ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, _ ->
                states[screen]?.takeIf { it.token === token }?.let { drawSlots(graphics, it, mouseX, mouseY) }
            }
            if (kind == Kind.TITLE) {
                ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, _, _, vertical ->
                    val current = states[screen]
                    if (current == null || current.token !== token || mouseX <= current.panelW || vertical == 0.0) return@register true
                    TitleScene.step(if (vertical > 0) -1 else 1)
                    false
                }
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                val current = states[screen]
                if (current == null || current.token !== token || event.button() != 0) return@register true
                val hit = current.slots.firstOrNull { it.action != null && it.contains(event.x(), event.y()) } ?: return@register true
                Minecraft.getInstance().soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f))
                hit.action?.invoke()
                false
            }
        }
    }

    private fun layout(screen: Screen, kind: Kind, width: Int, height: Int): State {
        Screens.getWidgets(screen).filterIsInstance<StringWidget>().forEach { it.visible = false }
        for (w in Screens.getWidgets(screen)) {
            val raw = w.message.string
            if (TextClean.hasPrivateUse(raw)) w.message = Component.literal(TextClean.clean(raw))
        }
        val widgets = Screens.getWidgets(screen)
            .filter { it.visible && it.width > 0 }
            .sortedWith(compareBy({ it.y }, { it.x }))
        if (kind == Kind.TITLE) return layoutTitle(screen, widgets, width, height)
        val real = widgets.map { Slot(it, it.message, null, accent = false) }.toMutableList()
        val slots = ArrayList<Slot>()
        if (kind == Kind.PAUSE) {
            slots += Slot(null, Component.literal("WynnOverhaul settings"), { Minecraft.getInstance().setScreenAndShow(WynnOverhaulHubScreen(screen)) }, accent = true)
        }
        slots.addAll(real)
        val main = slots.filter { it.widget == null || it.widget.width >= SMALL_MAX }
        val small = slots.filter { it.widget != null && it.widget.width < SMALL_MAX }

        val panelW: Int
        val panelX: Int
        val innerLeft: Int
        val top: Int
        val bottomLimit: Int
        val headline: String
        val panelY: Int
        val panelH: Int
        if (kind == Kind.TITLE) {
            panelW = (width * 0.30).toInt().coerceIn(200, 320).coerceAtMost(width)
            panelX = 0
            panelY = 0
            panelH = height
            innerLeft = PAD
            val font = Minecraft.getInstance().font
            val scale = min(2.4f, (panelW - PAD * 2).toFloat() / font.width(WORDMARK))
            top = maxOf(PAD + (9 * scale).toInt() + 52, height / 3)
            bottomLimit = height - PAD - (if (small.isEmpty()) 0 else SMALL_SIZE + GAP)
            headline = WORDMARK
        } else {
            panelW = 272
            panelX = (width - panelW) / 2
            innerLeft = panelX + PAD
            headline = "GAME MENU"
            val rows = rowsOf(main)
            val rowsH = rows * TILE_H + (rows - 1).coerceAtLeast(0) * GAP
            val extra = if (small.isEmpty()) 0 else SMALL_SIZE + GAP
            panelH = OwTheme.TITLE_BAR_H + PAD + rowsH + extra + PAD
            panelY = ((height - panelH) / 2).coerceAtLeast(0)
            top = panelY + OwTheme.TITLE_BAR_H + PAD
            bottomLimit = panelY + panelH - PAD - extra
        }
        val inner = panelW - PAD * 2
        var y = top
        val rowCount = rowsOf(main)
        val gap = if (rowCount > 1 && top + rowCount * TILE_H + (rowCount - 1) * GAP > bottomLimit) 2 else GAP
        var i = 0
        while (i < main.size) {
            val a = main[i]
            val aHalf = a.widget != null && a.widget.width < WIDE_MIN
            val b = if (aHalf) main.getOrNull(i + 1)?.takeIf { it.widget != null && it.widget.width < WIDE_MIN } else null
            if (b != null) {
                val half = (inner - GAP) / 2
                a.x = innerLeft; a.y = y; a.w = half
                b.x = innerLeft + half + GAP; b.y = y; b.w = inner - half - GAP
                i += 2
            } else {
                a.x = innerLeft; a.y = y; a.w = inner
                i++
            }
            y += TILE_H + gap
        }
        val smallTotal = small.size * SMALL_SIZE + (small.size - 1).coerceAtLeast(0) * GAP
        var sx = if (kind == Kind.TITLE) innerLeft else panelX + (panelW - smallTotal) / 2
        val sy = if (kind == Kind.TITLE) height - PAD - SMALL_SIZE else panelY + panelH - PAD - SMALL_SIZE
        for (s in small) {
            s.x = sx; s.y = sy; s.w = SMALL_SIZE; s.h = SMALL_SIZE
            sx += SMALL_SIZE + GAP
        }
        for (s in slots) s.widget?.let {
            it.x = s.x
            it.y = s.y
            it.setSize(s.w, s.h)
        }
        return State(Any(), kind, slots, panelX, panelY, panelW, panelH, headline)
    }

    private val TITLE_ORDER = listOf("multiplayer", "singleplayer", "options", "mods", "quit")

    private fun titleRank(text: String): Int = TITLE_ORDER.indexOfFirst { text.contains(it, true) }.let { if (it < 0) TITLE_ORDER.size else it }

    private fun layoutTitle(screen: Screen, widgets: List<AbstractWidget>, width: Int, height: Int): State {
        val font = Minecraft.getInstance().font
        val panelW = (width * 0.30).toInt().coerceIn(200, 320).coerceAtMost(width)
        val inner = panelW - PAD * 2
        val scale = min(2.4f, inner.toFloat() / font.width(WORDMARK))
        val top = maxOf(PAD + (9 * scale).toInt() + 52, height / 3)
        val slots = ArrayList<Slot>()
        val play = Slot(null, Component.literal("PLAY WYNNCRAFT"), { joinWynncraft(screen) }, x = PAD, y = top, w = inner, h = PLAY_H, accent = true)
        slots += play
        val small = ArrayList<Slot>()
        val links = ArrayList<Slot>()
        for (w in widgets) {
            val text = w.message.string
            if (text.contains("Copyright", true) || w is PlainTextButton) {
                if (w is PlainTextButton) w.visible = false
                continue
            }
            if (text.contains("Realms", true)) {
                w.visible = false
                w.active = false
                continue
            }
            if (w.width < SMALL_MAX) small += Slot(w, w.message, null) else links += Slot(w, w.message, null, subtle = true)
        }
        links.sortBy { titleRank(it.label.string) }
        var y = top + PLAY_H + 18
        for (l in links) {
            val quit = l.label.string.contains("quit", true)
            if (quit) y += 10
            l.x = PAD; l.y = y; l.w = inner; l.h = LINK_H
            y += LINK_H + 2
        }
        var sx = PAD
        val sy = height - PAD - SMALL_SIZE - BRANDING_CLEARANCE
        for (s in small) {
            s.x = sx; s.y = sy; s.w = SMALL_SIZE; s.h = SMALL_SIZE
            sx += SMALL_SIZE + GAP
        }
        slots += links
        slots += small
        val arrowY = height - PAD - SMALL_SIZE
        slots += Slot(null, Component.literal(">"), { TitleScene.step(1) }, x = width - PAD - SMALL_SIZE, y = arrowY, w = SMALL_SIZE, h = SMALL_SIZE)
        slots += Slot(null, Component.literal("<"), { TitleScene.step(-1) }, x = width - PAD - SMALL_SIZE * 2 - GAP, y = arrowY, w = SMALL_SIZE, h = SMALL_SIZE)
        for (s in slots) s.widget?.let {
            it.x = s.x
            it.y = s.y
            it.setSize(s.w, s.h)
        }
        return State(Any(), Kind.TITLE, slots, 0, 0, panelW, height, WORDMARK)
    }

    private fun joinWynncraft(parent: Screen) {
        val client = Minecraft.getInstance()
        val data = ServerData("Wynncraft", WYNNCRAFT_ADDRESS, ServerData.Type.OTHER)
        data.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED)
        ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(WYNNCRAFT_ADDRESS), data, false, null)
    }

    private fun rowsOf(main: List<Slot>): Int {
        var rows = 0
        var i = 0
        while (i < main.size) {
            val a = main[i]
            val aHalf = a.widget != null && a.widget.width < WIDE_MIN
            val b = if (aHalf) main.getOrNull(i + 1)?.takeIf { it.widget != null && it.widget.width < WIDE_MIN } else null
            i += if (b != null) 2 else 1
            rows++
        }
        return rows
    }

    private fun drawBackdrop(graphics: GuiGraphicsExtractor, state: State, width: Int, height: Int, mouseX: Int) {
        val font = Minecraft.getInstance().font
        if (state.kind == Kind.TITLE) {
            TitleScene.draw(graphics, width, height, state.panelW)
            graphics.fill(0, 0, state.panelW, height, OwTheme.PAGE_WASH)
            graphics.fill(state.panelW - 1, 0, state.panelW, height, OwTheme.HAIRLINE)
            graphics.fill(state.panelW, 0, state.panelW + 1, height, 0x40D4AF37)
            val scale = min(2.4f, (state.panelW - PAD * 2).toFloat() / font.width(state.headline))
            val textW = font.width(state.headline) * scale
            graphics.pose().pushMatrix()
            graphics.pose().translate((state.panelW - textW) / 2f, PAD + 6f)
            graphics.pose().scale(scale)
            graphics.text(font, state.headline, 0, 0, OwTheme.ACCENT, true)
            graphics.pose().popMatrix()
            val ruleY = PAD + 6 + (9 * scale).toInt() + 8
            graphics.fill(PAD + 20, ruleY, state.panelW - PAD - 20, ruleY + 1, OwTheme.ACCENT_DIM)
            val sub = "Minecraft ${net.minecraft.SharedConstants.getCurrentVersion().name()}"
            graphics.centeredText(font, sub, state.panelW / 2, ruleY + 8, OwTheme.TEXT_FAINT)
            val label = TitleScene.currentName
            val right = width - PAD
            val arrowsLeft = right - SMALL_SIZE * 2 - GAP
            graphics.text(font, label, arrowsLeft - GAP - font.width(label), height - PAD - SMALL_SIZE + (SMALL_SIZE - 8) / 2, OwTheme.TEXT_DIM, true)
            val dotsRight = arrowsLeft - GAP - font.width(label) - 12
            for (d in 0 until TitleScene.count) {
                val dx = dotsRight - (TitleScene.count - 1 - d) * 9
                val on = d == TitleScene.currentIndex
                graphics.fill(dx - 3, height - PAD - SMALL_SIZE / 2 - 2, dx + 3, height - PAD - SMALL_SIZE / 2 + 2, if (on) OwTheme.ACCENT else OwTheme.TEXT_FAINT)
            }
        } else {
            OwTheme.drawPage(graphics, state.panelX, state.panelY, state.panelW, state.panelH)
            graphics.fill(state.panelX, state.panelY + OwTheme.TITLE_BAR_H - 1, state.panelX + state.panelW, state.panelY + OwTheme.TITLE_BAR_H, OwTheme.HAIRLINE)
            graphics.centeredText(font, state.headline, state.panelX + state.panelW / 2, state.panelY + (OwTheme.TITLE_BAR_H - 8) / 2, OwTheme.TEXT)
        }
    }

    private fun drawSlots(graphics: GuiGraphicsExtractor, state: State, mouseX: Int, mouseY: Int) {
        val font = Minecraft.getInstance().font
        for (s in state.slots) {
            s.widget?.let {
                if (it.x != s.x || it.y != s.y || it.width != s.w || it.height != s.h) {
                    it.x = s.x
                    it.y = s.y
                    it.setSize(s.w, s.h)
                }
            }
            val active = s.widget?.isActive ?: true
            val hovered = active && s.contains(mouseX.toDouble(), mouseY.toDouble())
            val text = TextClean.clean(s.label.string)
            if (s.subtle) {
                val color = if (!active) OwTheme.TEXT_FAINT else if (hovered) OwTheme.ACCENT else OwTheme.TEXT_DIM
                graphics.fill(s.x, s.y, s.x + s.w, s.y + s.h, OwTheme.PAGE_WASH)
                graphics.centeredText(font, truncateToWidth(font, text, s.w - 8), s.x + s.w / 2, s.y + (s.h - 8) / 2, color)
                if (hovered) {
                    val tw = font.width(text) + 8
                    graphics.fill(s.x + (s.w - tw) / 2, s.y + s.h - 2, s.x + (s.w + tw) / 2, s.y + s.h - 1, OwTheme.ACCENT_DIM)
                }
                continue
            }
            val fill = (if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG) or OPAQUE
            val leaving = state.kind == Kind.PAUSE && s.widget != null && (text.contains("Disconnect", true) || text.contains("Save and Quit", true) || text.contains("Quit", true))
            val border = when {
                !active -> OwTheme.TILE_BORDER
                hovered -> OwTheme.BORDER_BRIGHT
                s.accent -> OwTheme.ACCENT
                leaving -> OwTheme.BAD
                else -> OwTheme.HAIRLINE
            }
            graphics.fill(s.x, s.y, s.x + s.w, s.y + s.h, fill)
            graphics.fill(s.x, s.y, s.x + s.w, s.y + 1, border)
            graphics.fill(s.x, s.y + s.h - 1, s.x + s.w, s.y + s.h, border)
            graphics.fill(s.x, s.y, s.x + 1, s.y + s.h, border)
            graphics.fill(s.x + s.w - 1, s.y, s.x + s.w, s.y + s.h, border)
            val color = when {
                !active -> OwTheme.TEXT_FAINT
                s.accent -> OwTheme.ACCENT
                leaving -> OwTheme.BAD
                else -> OwTheme.TEXT
            }
            val shown = if (s.w < SMALL_MAX) glyph(text) else truncateToWidth(font, text, s.w - 8)
            graphics.centeredText(font, shown, s.x + s.w / 2, s.y + (s.h - 8) / 2, color)
        }
    }

    private fun glyph(text: String): String = when {
        text.contains("Language", true) -> "Aa"
        text.contains("Accessib", true) -> "Ac"
        text.contains("Friend", true) -> "Fr"
        text.isBlank() -> "?"
        else -> text.split(' ').filter { it.isNotEmpty() }.let { w -> if (w.size > 1) "${w[0][0].uppercaseChar()}${w[1][0].uppercaseChar()}" else text.take(2) }
    }
}
