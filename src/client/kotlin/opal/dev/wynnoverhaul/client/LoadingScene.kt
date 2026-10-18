package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.gui.screens.ProgressScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

object LoadingScene {
    const val BACKGROUND = 0xFF17120C.toInt()
    private const val NANOS = 1_000_000_000.0
    private const val FADE_IN_NANOS = 380_000_000L
    private const val JOIN_GAP_NANOS = 400_000_000L
    private const val REVEAL_NANOS = 850_000_000L
    private const val TIP_NANOS = 6_500_000_000L
    private const val MOTES = 44
    private const val WORDMARK = "WYNNOVERHAUL"
    private const val WORDMARK_SCALE = 2.2f
    private const val RULE_W = 150
    private const val MIN_TIP_HEIGHT = 230
    private const val MIN_CAPTION_HEIGHT = 240
    private const val GOLD_MOTE = 0xE0B040

    private val TIPS = listOf(
        "Alt+click an inventory slot to lock it. Alt+click it again to unlock.",
        "Every HUD panel can be moved and resized in the HUD designer.",
        "Weapon, spell and gathering animations are YAML files in the config folder. /owanim reload applies your edits.",
        "The SYS tab in chat keeps every system message so nothing scrolls away.",
        "The party panel also lists the players in a guild war you are fighting.",
        "Pings are shared with your party in chat as compass coordinates.",
        "The compass footer shows your coordinates and the Wynncraft world you are on.",
        "Dump Open Container (F8 by default) copies the items, names and lore of the open menu.",
    )

    private val tokens = WeakHashMap<Screen, Any>()
    private var sessionStart = 0L
    private var fadeStart = 0L
    private var lastSeen = 0L
    private var wasLoading = false
    private var overlayStart = 0L
    private var overlayLastSeen = 0L
    private var revealStart = 0L

    fun enabled(): Boolean = WynnOverhaulConfig.current.customLoadingEnabled

    fun matches(screen: Screen?): Boolean =
        screen is ConnectScreen || screen is ServerReconfigScreen || screen is LevelLoadingScreen ||
            screen is GenericMessageScreen || screen is ProgressScreen

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (!enabled() || !matches(screen)) {
                tokens.remove(screen)
                return@register
            }
            val now = System.nanoTime()
            if (now - lastSeen > JOIN_GAP_NANOS) {
                sessionStart = now
                fadeStart = now
            }
            lastSeen = now
            val token = Any()
            tokens[screen] = token
            ScreenEvents.afterBackground(screen).register { s, graphics, _, _, _ ->
                if (tokens[s] === token) drawScreen(graphics, s)
            }
            ScreenEvents.afterExtract(screen).register { s, graphics, _, _, _ ->
                if (tokens[s] === token) drawFadeIn(graphics)
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val loading = enabled() && matches(client.gui.screen())
            if (wasLoading && !loading && client.player != null) {
                revealStart = System.nanoTime()
                UiSounds.reveal()
            }
            wasLoading = loading
        }
    }

    val revealElement = HudElement { graphics, _ ->
        val elapsed = System.nanoTime() - revealStart
        if (revealStart != 0L && elapsed < REVEAL_NANOS) {
            val remaining = 1f - elapsed.toFloat() / REVEAL_NANOS
            val alpha = (remaining * remaining * 255f).toInt().coerceIn(0, 255)
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha shl 24)
        }
    }

    private fun caption(screen: Screen): String = when (screen) {
        is ConnectScreen -> "CONNECTING"
        is ServerReconfigScreen -> "SWITCHING WORLDS"
        is LevelLoadingScreen -> "LOADING TERRAIN"
        else -> ""
    }

    private fun drawScreen(graphics: GuiGraphicsExtractor, screen: Screen) {
        val now = System.nanoTime()
        lastSeen = now
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        val t = (now - sessionStart) / NANOS
        drawAmbient(graphics, w, h, t, 1f)
        drawBrand(graphics, w, h / 12, 1f, t, if (h >= MIN_CAPTION_HEIGHT) caption(screen) else "")
        if (h >= MIN_TIP_HEIGHT) drawTip(graphics, w, h, now)
    }

    private fun drawFadeIn(graphics: GuiGraphicsExtractor) {
        val elapsed = System.nanoTime() - fadeStart
        if (elapsed >= FADE_IN_NANOS) return
        val remaining = 1f - elapsed.toFloat() / FADE_IN_NANOS
        val alpha = (remaining * remaining * 255f).toInt().coerceIn(0, 255)
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha shl 24)
    }

    fun drawOverlay(graphics: GuiGraphicsExtractor, fade: Float, progress: Float) {
        val now = System.nanoTime()
        if (now - overlayLastSeen > JOIN_GAP_NANOS * 5) overlayStart = now
        overlayLastSeen = now
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        val t = (now - overlayStart) / NANOS
        drawAmbient(graphics, w, h, t, fade)
        drawBrand(graphics, w, h / 2 - 34, fade, t, "LOADING")
        val barW = min(220, w - 60)
        val barX = (w - barW) / 2
        val barY = h / 2 + 22
        OwShape.well(graphics, barX, barY, barW, 7, 1, HudStyle.alpha(OwTheme.TILE_BG, fade), HudStyle.alpha(OwShape.WELL_EDGE, fade))
        val fill = ((barW - 2) * progress.coerceIn(0f, 1f)).toInt()
        if (fill > 0) {
            graphics.fillGradient(barX + 1, barY + 1, barX + 1 + fill, barY + 6, HudStyle.alpha(HudStyle.lighten(OwTheme.ACCENT, 0.2f), fade), HudStyle.alpha(HudStyle.darken(OwTheme.ACCENT_DIM, 0.1f), fade))
            graphics.fill(barX + 1, barY + 1, barX + 1 + fill, barY + 2, HudStyle.alpha(OwShape.HIGHLIGHT, fade))
            graphics.fill(barX + fill, barY + 1, barX + 1 + fill, barY + 6, HudStyle.alpha(HudStyle.lighten(OwTheme.ACCENT, 0.5f), fade))
        }
    }

    private fun drawAmbient(graphics: GuiGraphicsExtractor, w: Int, h: Int, t: Double, fade: Float) {
        graphics.fillGradient(0, 0, w, h, HudStyle.alpha(0xFF17120C.toInt(), fade), HudStyle.alpha(0xFF060403.toInt(), fade))
        graphics.fillGradient(0, 0, w, h / 2, HudStyle.alpha(0x18D4AF37, fade), 0x00D4AF37)
        for (i in 0 until MOTES) {
            val seed = i * 0.6180339887
            val x = frac(seed * 7.31) * w + sin(t * 0.4 + i) * 6.0
            val rise = 7.0 + (i % 5) * 3.0
            val y = h - ((t * rise + frac(seed * 3.7) * h) % h)
            val edge = min(1.0, min(y / (h * 0.25), (h - y) / (h * 0.25)))
            val twinkle = 0.55 + 0.45 * sin(t * (1.1 + (i % 4) * 0.3) + i * 1.7)
            val a = (0.5 * edge * twinkle * fade).coerceIn(0.0, 1.0)
            val size = if (i % 3 == 0) 2 else 1
            graphics.fill(x.toInt(), y.toInt(), x.toInt() + size, y.toInt() + size, ((a * 255).toInt() shl 24) or GOLD_MOTE)
        }
        graphics.fillGradient(0, 0, w, h / 5, HudStyle.alpha(0xA0000000.toInt(), fade), 0x00000000)
        graphics.fillGradient(0, h - h / 4, w, h, 0x00000000, HudStyle.alpha(0xB0000000.toInt(), fade))
        for (i in 0 until 10) {
            val a = HudStyle.alpha(((0x50 - i * 8) shl 24), fade)
            graphics.fill(i * 3, 0, i * 3 + 3, h, a)
            graphics.fill(w - i * 3 - 3, 0, w - i * 3, h, a)
        }
    }

    private fun drawBrand(graphics: GuiGraphicsExtractor, w: Int, top: Int, fade: Float, t: Double, caption: String) {
        val font = Minecraft.getInstance().font
        val y = maxOf(14, top)
        val scale = min(WORDMARK_SCALE, (w * 0.5f) / font.width(WORDMARK))
        val textW = font.width(WORDMARK) * scale
        graphics.pose().pushMatrix()
        graphics.pose().translate((w - textW) / 2f, y.toFloat())
        graphics.pose().scale(scale)
        graphics.text(font, WORDMARK, 0, 0, HudStyle.alpha(OwTheme.ACCENT, fade), true)
        graphics.pose().popMatrix()
        val ruleY = y + (9 * scale).toInt() + 6
        val ruleX = (w - RULE_W) / 2
        val gold = HudStyle.alpha(OwTheme.ACCENT_DIM, fade)
        OwShape.ornamentRule(graphics, ruleX, ruleY, RULE_W, gold)
        val sweep = ((t * 0.55) % 1.0).toFloat()
        val center = ruleX + 4 + ((RULE_W - 8) * sweep).toInt()
        for (d in -10..10) {
            val a = (1f - abs(d) / 10f) * fade * 0.9f
            graphics.fill(center + d, ruleY, center + d + 1, ruleY + 1, HudStyle.alpha(OwTheme.ACCENT, a))
        }
        if (caption.isNotEmpty()) graphics.centeredText(font, caption, w / 2, ruleY + 8, HudStyle.alpha(OwTheme.TEXT_DIM, fade))
    }

    private fun drawTip(graphics: GuiGraphicsExtractor, w: Int, h: Int, now: Long) {
        val font = Minecraft.getInstance().font
        val elapsed = now - sessionStart
        val slot = elapsed / TIP_NANOS
        val phase = (elapsed % TIP_NANOS).toFloat() / TIP_NANOS
        val a = min(1f, min(phase * 8f, (1f - phase) * 8f))
        val tip = TIPS[Math.floorMod(slot + (sessionStart / 1_000_000_000L), TIPS.size.toLong()).toInt()]
        val lines = HudStyle.wrap(font, tip, min(w - 40, 340))
        val top = h - 22 - lines.size * 10
        graphics.centeredText(font, "TIP", w / 2, top - 11, HudStyle.alpha(OwTheme.ACCENT, a))
        for ((i, line) in lines.withIndex()) {
            graphics.centeredText(font, line, w / 2, top + i * 10, HudStyle.alpha(OwTheme.TEXT_DIM, a))
        }
    }

    private fun frac(v: Double): Double = v - floor(v)
}
