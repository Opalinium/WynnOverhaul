package opal.dev.overwatch.client

import java.util.UUID
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.world.BossEvent
import opal.dev.overwatch.Overwatch

object WynnResourceBarTracker : ClientboundBossEventPacket.Handler {
    private const val S = "\u00A7"

    enum class ResourceBarKind(
        val id: String,
        val displayName: String,
        val colorArgb: Int,
    ) {
        BLOOD_POOL("blood_pool", "Blood Pool", -0x4FC4D8),
        COMMANDER("commander", "Commander", -0xD26320),
        CORRUPTED("corrupted", "Corrupted", -0x95D058),
        DISTORTION("distortion", "Distortion", -0x2A6601),
        FOCUS("focus", "Focus", -0x1F67),
        HOLY_POWER("holy_power", "Holy Power", -0x801C01),
        MANA_BANK("mana_bank", "Mana Bank", -0x801C01),
        MANTRA("mantra", "Mantra", -0xBAA83),
        MIRROR_IMAGE("mirror_image", "Mirror Image", -0x8420),
        MOMENTUM("momentum", "Momentum", -0x2995),
        NIGHTCLOAK_KNIVES("nightcloak_knives", "Nightcloak Knives", -0x1F5F01),
        OPHANIM("ophanim", "Ophanim", -0x801C01),
    }

    data class ResourceBarState(
        val kind: ResourceBarKind,
        val progress: Float,
        val displayText: String,
    ) {
        val fraction: Float
            get() = progress.coerceIn(0f, 1f)
    }

    @Volatile
    var active: ResourceBarState? = null
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
        parse(name)?.let { kind ->
            activeId = id
            active = ResourceBarState(kind, progress, displayText(kind, name.getString()))
            OverwatchGate.noteActionBar()
        }
    }

    override fun remove(id: UUID) {
        if (id == activeId) {
            activeId = null
        }
    }

    override fun updateProgress(id: UUID, progress: Float) {
        val current = active
        if (id == activeId && current != null) {
            active = current.copy(progress = progress)
        }
    }

    override fun updateName(id: UUID, name: Component) {
        if (id == activeId) {
            val kind = parse(name)
            if (kind != null) {
                active = ResourceBarState(kind, active?.progress ?: 0f, displayText(kind, name.getString()))
            } else {
                activeId = null
            }
        }
    }

    private fun parse(name: Component): ResourceBarKind? {
        val raw = name.getString()
        for (kind in KINDS) {
            try {
                if (KIND_PATTERNS.getValue(kind).matches(raw)) return kind
            } catch (e: IllegalArgumentException) {
                Overwatch.LOGGER.warn("Overwatch resource bar tracker: bad regex for {}", kind.name, e)
            }
        }
        return null
    }

    private fun displayText(kind: ResourceBarKind, raw: String): String {
        val match = KIND_PATTERNS.getValue(kind).matchEntire(raw) ?: return ""
        return when (kind) {
            ResourceBarKind.BLOOD_POOL -> match.groupValues[1] + "%"
            ResourceBarKind.CORRUPTED -> match.groupValues[1] + "%"
            ResourceBarKind.DISTORTION -> match.groupValues[1]
            ResourceBarKind.FOCUS -> match.groupValues[1] + "/" + match.groupValues[2]
            ResourceBarKind.HOLY_POWER -> (match.groupValues[1]) + "%"
            ResourceBarKind.MANA_BANK -> match.groupValues[1] + "/" + match.groupValues[2]
            ResourceBarKind.COMMANDER -> match.groupValues[2] + "s"
            ResourceBarKind.MOMENTUM -> if (match.groups["max"] != null) "MAX" else match.groupValues[1]
            ResourceBarKind.NIGHTCLOAK_KNIVES -> match.groupValues[1]
            ResourceBarKind.MIRROR_IMAGE -> countMatches(match.groupValues[1], "\uE040").toString() + "/7"
            ResourceBarKind.OPHANIM -> match.groups["healed"]?.value?.plus("%") ?: ""
            ResourceBarKind.MANTRA ->
                match.groups["lunatic"]?.value + "/" + match.groups["heretic"]?.value + "/" + match.groups["fanatic"]?.value
        }
    }

    private fun countMatches(text: String, needle: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(needle, index)
            if (index == -1) return count
            count++
            index += needle.length
        }
    }

    private val KINDS: List<ResourceBarKind> = ResourceBarKind.entries.toList()

    private val KIND_PATTERNS: Map<ResourceBarKind, Regex> = mapOf(
        ResourceBarKind.BLOOD_POOL to Regex(S + "cBlood Pool " + S + "4\\[" + S + "c(\\d+)%" + S + "4\\]"),
        ResourceBarKind.CORRUPTED to Regex(S + "cCorrupted " + S + "4\\[" + S + "c(\\d+)%" + S + "4]"),
        ResourceBarKind.DISTORTION to Regex(S + "#d599ffff\uE035 Distortion: " + S + "b(\\d+)"),
        ResourceBarKind.FOCUS to Regex(S + "eFocus " + S + "6\\[" + S + "e(\\d+)/(\\d+)" + S + "6]"),
        ResourceBarKind.HOLY_POWER to Regex(S + "bHoly Power " + S + "3\\[" + S + "b(\\d+)%" + S + "3\\]"),
        ResourceBarKind.MANA_BANK to Regex(S + "bMana Bank " + S + "3\\[(\\d+)/(\\d+)\\]"),
        ResourceBarKind.COMMANDER to Regex(S + "(c|a)Commander: ([0-9]+)s"),
        ResourceBarKind.MOMENTUM to
            Regex(S + "f(?<momentum>\\d+)" + S + "7 Momentum(?<max> " + S + "8\\[" + S + "8MAX" + S + "8\\])?"),
        ResourceBarKind.NIGHTCLOAK_KNIVES to Regex(S + "d(\\d+) Nightcloak Knives"),
        ResourceBarKind.MIRROR_IMAGE to Regex("^" + S + "dMirror Image: (?<clones>(((" + S + "[a7])?)\uE040){0,7})"),
        ResourceBarKind.OPHANIM to Regex(
            "^" + S + "710s Healed: " + S + "f(?<healed>\\d+)% " + S + "[3468]\\[(?<orbs>(((" + S + "[bce7])?)?){0,7})(" + S + "[3468])?\\]$",
        ),
        ResourceBarKind.MANTRA to Regex(
            S + "#f4557dff\uE024 Lunatic " + S + "(?<lunaticCap>.)\\+(?<lunatic>\\d+)%" + S + "8 \\| " +
                S + "#99e9ffff\uE022 Heretic " + S + "(?<hereticCap>.)\\+(?<heretic>\\d+)%" + S + "8 \\| " +
                S + "#ffc251ff\uE023 Fanatic " + S + "(?<fanaticCap>.)\\+(?<fanatic>\\d+)%",
        ),
    )
}