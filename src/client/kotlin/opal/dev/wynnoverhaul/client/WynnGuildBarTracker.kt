package opal.dev.wynnoverhaul.client

import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.world.BossEvent
import opal.dev.wynnoverhaul.WynnOverhaul

object WynnGuildBarTracker : ClientboundBossEventPacket.Handler {
    data class GuildState(val name: String, val level: Int, val xpPercent: Int)

    @Volatile
    var active: GuildState? = null
        private set

    private var activeId: UUID? = null

    fun clear() {
        active = null
        activeId = null
    }

    override fun add(
        id: UUID,
        name: Component,
        progress: Float,
        color: BossEvent.BossBarColor,
        overlay: BossEvent.BossBarOverlay,
        darkenScreen: Boolean,
        playBossMusic: Boolean,
        createWorldFog: Boolean,
    ) {
        parse(name.string)?.let { state ->
            adopt(id, state)
            WynnOverhaulGate.noteActionBar()
        }
    }

    override fun remove(id: UUID) {
        if (id == activeId) {
            activeId = null
        }
    }

    override fun updateProgress(id: UUID, progress: Float) {
    }

    override fun updateName(id: UUID, name: Component) {
        val parsed = parse(name.string)
        if (parsed != null) {
            adopt(id, parsed)
            WynnOverhaulGate.noteActionBar()
        } else if (id == activeId) {
            activeId = null
        }
    }

    private fun adopt(id: UUID, state: GuildState) {
        activeId = id
        active = state
    }

    fun ownsOverlayLine(raw: String): Boolean {
        val name = active?.name ?: return false
        return raw.contains(name, ignoreCase = true)
    }

    private fun parse(raw: String): GuildState? {
        val match = GUILD_PATTERN.matchEntire(raw) ?: return null
        val level = match.groupValues[1].toIntOrNull() ?: return null
        val name = match.groupValues[2].trim()
        val xp = match.groupValues[3].toIntOrNull() ?: return null
        if (name.isEmpty() || level !in 1..200 || xp !in 0..100) return null
        return GuildState(name, level, xp)
    }

    private const val S = "\u00A7"
    private val GUILD_PATTERN = Regex(
        "^" + S + "7Lv\\. (\\d+)" + S + "f - " + S + "l" + S + "b(.+)" + S + "f - " + S + "7(\\d+)% XP$",
    )
}
