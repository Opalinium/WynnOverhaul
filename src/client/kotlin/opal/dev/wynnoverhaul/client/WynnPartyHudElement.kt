package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import opal.dev.wynnoverhaul.WynnOverhaul
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class WynnPartyHudElement(private val id: String = "party", private val section: () -> WynnScoreboardTracker.Party? = { WynnScoreboardTracker.party }) : HudElement {
    private var loggedError = false
    private val palette = LinkedHashMap<String, Int>()
    private val shown = HashMap<String, Float>()
    private var lastNanos = 0L

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Party HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        val config = WynnOverhaulConfig.current
        if (!WynnOverhaulGate.inGame || !config.partyHudEnabled) return
        val party = section()
        if (party == null || party.members.isEmpty()) {
            palette.clear()
            shown.clear()
            return
        }

        val client = Minecraft.getInstance()
        val font = client.font
        val me = client.player?.gameProfile?.name.orEmpty()
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.25f)
        lastNanos = now
        val seconds = now / 1_000_000_000.0
        val bars = config.partyHealthBars
        val rowH = font.lineHeight + 2 + if (bars) BAR_H + 2 else 0
        val alive = party.members.count { it.online && !it.dead }
        val sub = listOfNotNull(party.level?.let { "Lv $it" }, "$alive/${party.members.size}").joinToString("  ")

        val rows = party.members.map { member ->
            Row(member, nameColor(member, config.partyNameColors, me), distanceText(client, member, me, config.partyShowDistance))
        }
        var contentW = font.width(party.title.uppercase()) + font.width(sub) + 32
        for (row in rows) {
            contentW = maxOf(
                contentW,
                font.width(row.member.name) + font.width(row.distance) + font.width(healthText(row.member)) + font.width(levelText(row.member)) + 40,
            )
        }
        val contentH = PAD * 2 + font.lineHeight + 3 + HEADER_GAP + rows.size * rowH
        val (boxW, boxH) = HudLayoutManager.stableSize(id, maxOf(contentW, MIN_W) + PAD * 2, contentH)

        val scale = HudLayoutManager.scale(id)
        val (baseX, baseY) = HudLayoutManager.resolve(id, graphics.guiWidth(), graphics.guiHeight())

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        OwTheme.hudPanel(graphics, ox, oy, boxW, boxH)
        HudStyle.header(graphics, font, ox + PAD, oy + PAD, boxW - PAD * 2, party.title, sub)
        var y = oy + PAD + font.lineHeight + 3 + HEADER_GAP
        val left = ox + PAD + 2
        val right = ox + boxW - PAD
        for (row in rows) {
            val member = row.member
            val inactive = member.dead || !member.online
            val level = levelText(member)
            val health = healthText(member)
            val levelX = right - font.width(level)
            val healthX = levelX - 5 - font.width(health)
            val nameArgb = if (inactive) OwTheme.TEXT_FAINT else row.color or OPAQUE

            val name = Component.literal(member.name)
            if (member.dead) name.withStyle(ChatFormatting.STRIKETHROUGH)
            graphics.fill(ox + PAD - 1, y, ox + PAD, y + font.lineHeight, nameArgb)
            graphics.text(font, name, left, y, nameArgb, true)
            val after = left + font.width(member.name) + 5
            if (row.distance.isNotEmpty() && after + font.width(row.distance) < healthX - 4) {
                graphics.text(font, row.distance, after, y, OwTheme.TEXT_FAINT, true)
            }
            when {
                member.dead -> graphics.text(font, "DEAD", levelX - 5 - font.width("DEAD"), y, OwTheme.BAD, true)
                !member.online -> graphics.text(font, "offline", levelX - 5 - font.width("offline"), y, OwTheme.TEXT_FAINT, true)
                health.isNotEmpty() -> graphics.text(font, health, healthX, y, healthColor(member), true)
            }
            if (level.isNotEmpty()) graphics.text(font, level, levelX, y, OwTheme.TEXT_DIM, true)

            if (bars) {
                val target = if (!member.online || member.dead) 0f else member.fraction ?: 1f
                val key = member.name.lowercase()
                val current = shown[key] ?: target
                val smooth = if (abs(target - current) < 0.005f) target else current + (target - current) * (1f - exp(-dt * BAR_SPEED))
                shown[key] = smooth
                var fill = healthColor(member, smooth)
                if (member.online && !member.dead && smooth < LOW && smooth > 0f) {
                    fill = HudStyle.alpha(fill, 0.65f + 0.35f * (0.5f + 0.5f * sin(seconds * 6.0).toFloat()))
                }
                HudStyle.bar(graphics, left, y + font.lineHeight + 1, right - left, BAR_H, smooth, fill, notches = false)
            }
            y += rowH
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private class Row(val member: WynnScoreboardTracker.PartyMember, val color: Int, val distance: String)

    private fun nameColor(member: WynnScoreboardTracker.PartyMember, mode: String, me: String): Int {
        val key = member.name.lowercase()
        if (member.name.equals(me, ignoreCase = true)) return SELF_COLOR
        return when (mode) {
            "OFF" -> OwTheme.TEXT_DIM and 0xFFFFFF
            "HEALTH" -> healthColor(member) and 0xFFFFFF
            else -> {
                val taken = palette.values.toSet()
                palette.getOrPut(key) { PALETTE.firstOrNull { it !in taken } ?: PALETTE[palette.size % PALETTE.size] }
            }
        }
    }

    private fun healthColor(member: WynnScoreboardTracker.PartyMember, fraction: Float? = member.fraction): Int {
        if (member.dead) return OwTheme.BAD
        val f = fraction ?: return member.healthColor ?: OwTheme.TEXT_DIM
        return when {
            f >= 0.6f -> OwTheme.GOOD
            f >= LOW -> OwTheme.WARN
            else -> OwTheme.BAD
        }
    }

    private fun distanceText(client: Minecraft, member: WynnScoreboardTracker.PartyMember, me: String, enabled: Boolean): String {
        if (!enabled || !member.online || member.dead) return ""
        if (member.name.equals(me, ignoreCase = true)) return "you"
        val self = client.player ?: return ""
        val other = client.level?.players()?.firstOrNull { it.gameProfile.name.equals(member.name, ignoreCase = true) } ?: return ""
        return "${self.distanceTo(other).toInt()}m"
    }

    private fun healthText(member: WynnScoreboardTracker.PartyMember): String =
        member.health?.takeIf { member.online && !member.dead }?.toString() ?: ""

    private fun levelText(member: WynnScoreboardTracker.PartyMember): String = (member.level?.toString() ?: member.tag)?.let { "[$it]" } ?: ""

    private companion object {
        const val PAD = 4
        const val HEADER_GAP = 2
        const val MIN_W = 130
        const val BAR_H = 4
        const val BAR_SPEED = 6f
        const val LOW = 0.3f
        const val SELF_COLOR = 0xFFD700
        const val OPAQUE = 0xFF000000.toInt()
        val PALETTE = listOf(0x5FB8F0, 0x6FD0E0, 0xB48AE8, 0x7FDDA0, 0xE88AB4, 0xEA9A5A, 0xB5DD5A, 0x7AA8F0, 0xCDB8F5, 0xF2C6A0)
    }
}
