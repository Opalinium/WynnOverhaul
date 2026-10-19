package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import kotlin.math.exp

object DialogueLetterbox {
    private const val BAR_SHARE = 0.085f
    private const val RATE = 6f
    private const val EDGE_FADE = 10

    private var amount = 0f
    private var last = System.nanoTime()

    val element = HudElement { graphics, _ ->
        val now = System.nanoTime()
        val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
        last = now
        val config = WynnOverhaulConfig.current
        val wanted = WynnOverhaulGate.inGame && config.customHudEnabled && config.dialogueLetterboxEnabled &&
            WynnDialogueTracker.dialogueVisible(System.currentTimeMillis())
        val target = if (wanted) 1f else 0f
        amount += (target - amount) * (1f - exp(-dt * RATE))
        if (kotlin.math.abs(target - amount) < 0.005f) amount = target
        if (amount <= 0f) return@HudElement
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        val bar = (h * BAR_SHARE * HudStyle.smoothstep(amount)).toInt()
        if (bar <= 0) return@HudElement
        graphics.fill(0, 0, w, bar, 0xFF000000.toInt())
        graphics.fillGradient(0, bar, w, bar + EDGE_FADE, 0x90000000.toInt(), 0x00000000)
        graphics.fill(0, bar - 1, w, bar, HudStyle.alpha(OwTheme.ACCENT_DIM, 0.55f * amount))
        graphics.fill(0, h - bar, w, h, 0xFF000000.toInt())
        graphics.fillGradient(0, h - bar - EDGE_FADE, w, h - bar, 0x00000000, 0x90000000.toInt())
        graphics.fill(0, h - bar, w, h - bar + 1, HudStyle.alpha(OwTheme.ACCENT_DIM, 0.55f * amount))
    }
}
