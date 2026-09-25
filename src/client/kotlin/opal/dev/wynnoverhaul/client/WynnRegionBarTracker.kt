package opal.dev.wynnoverhaul.client

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.world.BossEvent

object WynnRegionBarTracker : ClientboundBossEventPacket.Handler {
    data class RegionState(val name: String)

    @Volatile
    var active: RegionState? = null
        private set

    private var activeId: UUID? = null

    fun clear() {
        active = null
        activeId = null
        WynnLocationToasts.reset()
        WynnOverhaulToastQueue.clear()
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
            if (confirmed(state.name)) {
                adopt(id, state)
                WynnOverhaulGate.noteActionBar()
            }
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
        if (parsed != null && confirmed(parsed.name)) {
            adopt(id, parsed)
            WynnOverhaulGate.noteActionBar()
        } else if (id == activeId && parsed != null) {
            activeId = null
        } else if (id == activeId) {
            activeId = null
        }
    }

    private fun adopt(id: UUID, state: RegionState) {
        val previous = active?.name
        activeId = id
        active = state
        if (previous != state.name) WynnLocationToasts.onRegionChanged(previous, state.name)
    }

    private fun confirmed(name: String): Boolean {
        val player = Minecraft.getInstance().player ?: return true
        val nearest = try {
            WynnRegions.nearestRegion(player.x, player.z)
        } catch (t: Throwable) {
            null
        } ?: return true
        return nearest.equals(name, ignoreCase = true) ||
            nearest.contains(name, ignoreCase = true) ||
            name.contains(nearest, ignoreCase = true)
    }

    fun ownsOverlayLine(raw: String): Boolean {
        val name = active?.name ?: return false
        return raw.contains(name, ignoreCase = true)
    }

    private fun parse(raw: String): RegionState? {
        if (!hasPrivateUse(raw)) return null
        if (raw.indexOf(SECTION_SIGN) >= 0) return null
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (cp in 0x20..0x7E) {
                if (cp in DIGIT_START..DIGIT_END) return null
                sb.appendCodePoint(cp)
            } else {
                sb.append(' ')
            }
            i += Character.charCount(cp)
        }
        val name = sb.toString().replace(WHITESPACE_RUN, " ").trim()
        if (name.length < MIN_NAME_LENGTH || !name.any { it.isLetter() }) return null
        return RegionState(name)
    }

    private fun hasPrivateUse(text: String): Boolean {
        for (i in text.indices) {
            val c = text[i]
            if ((c >= '\uE000' && c <= '\uF8FF') || Character.isSurrogate(c)) return true
        }
        return false
    }

    private val WHITESPACE_RUN = Regex("\\s+")

    private const val SECTION_SIGN = '§'
    private const val DIGIT_START = 0x30
    private const val DIGIT_END = 0x39
    private const val MIN_NAME_LENGTH = 3
}
