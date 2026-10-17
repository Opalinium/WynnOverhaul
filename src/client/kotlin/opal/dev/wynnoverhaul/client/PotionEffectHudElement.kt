package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Hud
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import opal.dev.wynnoverhaul.WynnOverhaul

class PotionEffectHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Potion effect HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame) return
        val config = WynnOverhaulConfig.current
        if (!config.customPotionHudEnabled) return
        val player = Minecraft.getInstance().player

        val rows = ArrayList<Row>()
        if (player != null) {
            for (instance in player.activeEffects) {
                if (instance.showIcon()) rows.add(formatEffectRow(instance))
            }
        }
        val listed = HashSet<String>()
        for (effect in WynnStatusEffectTracker.effects) {
            listed.add(effect.name.lowercase())
            rows.add(formatListedRow(effect))
        }
        for (buff in WynnBuffTracker.active) {
            if (buff.name.lowercase() !in listed) rows.add(formatBuffRow(buff))
        }
        if (rows.isEmpty()) {
            order.clear()
            return
        }
        stabilise(rows)

        val font = Minecraft.getInstance().font

        val (boxW, _) = HudLayoutManager.stableSize(ID, rows.maxOf { font.width(it.text) + font.width(it.timer) + TIMER_GAP } + ICON_SIZE + 6, LINE_HEIGHT * rows.size)

        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        val now = System.currentTimeMillis()
        var y = oy
        for (row in rows) {
            val expiring = config.potionHudExpiryPulse && row.seconds in 0..EXPIRY_SECONDS
            val pulse = if (expiring) 0.55f + 0.45f * (0.5f + 0.5f * kotlin.math.sin(now / PULSE_PERIOD).toFloat()) else 1f
            graphics.fill(ox, y + 1, ox + boxW, y + LINE_HEIGHT - 1, if (row.harmful) HARMFUL_BG else ROW_BG)
            graphics.fill(ox, y + 1, ox + 2, y + LINE_HEIGHT - 1, HudStyle.alpha(row.color, pulse))
            if (row.harmful) graphics.outline(ox, y + 1, boxW, LINE_HEIGHT - 2, HudStyle.alpha(COLOR_HARMFUL, 0.55f * pulse))
            if (row.sprite != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite, ox + 3, y, ICON_SIZE, ICON_SIZE, HudStyle.alpha(0xFFFFFFFF.toInt(), pulse))
            }
            graphics.text(font, row.text, ox + ICON_SIZE + 6, y + TEXT_Y_OFFSET, HudStyle.alpha(row.color, pulse), true)
            val timerColor = if (expiring) HudStyle.alpha(COLOR_HARMFUL, pulse) else OwTheme.TEXT_DIM
            graphics.text(font, row.timer, ox + boxW - font.width(row.timer) - 4, y + TEXT_Y_OFFSET, timerColor, true)
            y += LINE_HEIGHT
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private data class Row(val key: String, val text: String, val timer: String, val color: Int, val sprite: Identifier?, val harmful: Boolean, val seconds: Int)

    private val order = LinkedHashMap<String, Int>()
    private var nextOrder = 0

    private fun stabilise(rows: MutableList<Row>) {
        for (row in rows) order.getOrPut(row.key) { nextOrder++ }
        order.keys.retainAll(rows.mapTo(HashSet()) { it.key })
        rows.sortWith(compareBy<Row>({ it.harmful }, { order.getValue(it.key) }))
    }

    private fun formatEffectRow(instance: MobEffectInstance): Row {
        val effect = instance.effect.value()
        val amplifier = instance.amplifier
        val label = if (amplifier > 0) "${effect.displayName.string} ${romanNumeral(amplifier)}" else effect.displayName.string
        val duration = if (instance.isInfiniteDuration) INFINITE_SYMBOL else formatDuration(instance.duration / 20)
        val color = when (effect.category) {
            MobEffectCategory.BENEFICIAL -> COLOR_BENEFICIAL
            MobEffectCategory.HARMFUL -> COLOR_HARMFUL
            else -> COLOR_NEUTRAL
        }
        val seconds = if (instance.isInfiniteDuration) -1 else instance.duration / 20
        return Row("effect:${instance.effect.registeredName}", label, duration, color, Hud.getMobEffectSprite(instance.effect), effect.category == MobEffectCategory.HARMFUL, seconds)
    }

    private fun formatBuffRow(buff: WynnBuffTracker.Buff): Row {
        val sign = if (buff.amount >= 0) "+" else ""
        val color = if (buff.amount >= 0) COLOR_BENEFICIAL else COLOR_HARMFUL
        return Row("buff:${buff.name}", "$sign${buff.amount} ${buff.name}", formatDuration(buff.remainingSeconds), color, null, buff.amount < 0, buff.remainingSeconds)
    }

    private fun formatListedRow(effect: WynnStatusEffectTracker.Effect): Row {
        val harmful = effect.modifier.startsWith("-") || effect.name.lowercase() in DEBUFF_NAMES
        val color = if (harmful) COLOR_HARMFUL else COLOR_BENEFICIAL
        val label = if (effect.modifier.isEmpty()) effect.name else "${effect.modifier} ${effect.name}"
        val seconds = if (effect.indefinite) -1 else effect.displaySeconds()
        val timer = if (effect.indefinite) INFINITE_SYMBOL else formatDuration(seconds)
        return Row("listed:${effect.name}", label, timer, color, null, harmful, seconds)
    }

    private fun romanNumeral(amplifier: Int): String = ROMAN.getOrElse(amplifier) { (amplifier + 1).toString() }

    private fun formatDuration(totalSeconds: Int): String {
        return HudStyle.clock(totalSeconds)
    }

    private companion object {
        const val ID = "potion_effects"
        const val LINE_HEIGHT = 18
        const val ICON_SIZE = 18
        const val TEXT_Y_OFFSET = 5
        const val TIMER_GAP = 12
        const val ROW_BG = 0x6E0E0B08
        const val HARMFUL_BG = 0x6E2A0E0E
        const val EXPIRY_SECONDS = 5
        const val PULSE_PERIOD = 140.0
        const val INFINITE_SYMBOL = "∞"
        val COLOR_BENEFICIAL = OwTheme.GOOD
        val COLOR_HARMFUL = OwTheme.BAD
        val COLOR_NEUTRAL = OwTheme.TEXT_DIM
        val DEBUFF_NAMES = setOf("poison", "wither", "weakness", "weakened", "slowness", "blindness", "nausea", "hunger", "bleed", "bleeding", "burning", "frozen", "cursed", "curse", "stunned", "silenced", "vulnerable", "exposed", "marked", "rooted", "confused", "fatigue", "mining fatigue", "darkness", "levitation", "slow", "snared", "shocked")
        val ROMAN = listOf("", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")
    }
}
