package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft

object HudLayoutPresets {
    class Place(val corner: String, val x: Int, val y: Int, val width: Int = 0, val height: Int = 0)

    class Builtin(val id: String, val label: String, val places: Map<String, Place>)

    const val DEFAULT = "DEFAULT"
    const val SLOT_COUNT = 3
    private const val PREVIOUS = "PREVIOUS"

    private val NEAR_CROSSHAIR = mapOf(
        "spell_combo" to (0 to 30),
        "ultimates" to (0 to 52),
    )

    val BUILTINS = listOf(
        Builtin(DEFAULT, "Default", emptyMap()),
        Builtin(
            "BOTTOM_BARS",
            "Bottom bars",
            mapOf(
                "hp" to Place("BOTTOM_LEFT", 4, 44),
                "mana" to Place("BOTTOM_LEFT", 4, 24),
                "sprint" to Place("BOTTOM_LEFT", 4, 4),
                "resource_bar" to Place("BOTTOM_LEFT", 4, 64),
                "xp_bar" to Place("BOTTOM_RIGHT", 4, 4),
                "mount_energy" to Place("BOTTOM_RIGHT", 4, 24),
                "guild" to Place("TOP_LEFT", 4, 4),
                "compass" to Place("TOP_LEFT", 200, 4),
            ),
        ),
        Builtin(
            "TOP_STACK",
            "Top stack",
            mapOf(
                "hp" to Place("TOP_LEFT", 4, 4),
                "mana" to Place("TOP_LEFT", 4, 24),
                "resource_bar" to Place("TOP_LEFT", 4, 44),
                "sprint" to Place("TOP_LEFT", 4, 64),
                "xp_bar" to Place("TOP_LEFT", 4, 84),
                "mount_energy" to Place("TOP_LEFT", 4, 104),
                "guild" to Place("TOP_LEFT", 4, 124),
                "compass" to Place("TOP_LEFT", 200, 4),
            ),
        ),
        Builtin(
            "SOULS",
            "Souls",
            mapOf(
                "hp" to Place("TOP_LEFT", 12, 12, 260, 15),
                "mana" to Place("TOP_LEFT", 12, 32, 200, 15),
                "sprint" to Place("TOP_LEFT", 12, 52, 140, 15),
                "resource_bar" to Place("TOP_LEFT", 12, 72, 170, 15),
                "xp_bar" to Place("BOTTOM_LEFT", 4, 4, 200, 15),
                "mount_energy" to Place("TOP_LEFT", 12, 92, 140, 15),
                "guild" to Place("TOP_LEFT", 12, 112),
                "compass" to Place("TOP_LEFT", 300, 4),
            ),
        ),
    )

    val IDS: List<String> = BUILTINS.map { it.id } + (1..SLOT_COUNT).map { slotId(it) }

    fun slotId(n: Int): String = "SLOT_$n"

    fun isSlot(id: String): Boolean = id.startsWith("SLOT_")

    fun selected(config: WynnOverhaulConfig): String = if (config.hudLayoutPreset in IDS) config.hudLayoutPreset else DEFAULT

    fun label(id: String): String {
        BUILTINS.firstOrNull { it.id == id }?.let { return it.label }
        val filled = WynnOverhaulConfig.current.hudLayoutSlots[id]?.isNotEmpty() == true
        val name = "Custom ${id.removePrefix("SLOT_")}"
        return if (filled) name else "$name (empty)"
    }

    fun apply(config: WynnOverhaulConfig, id: String) {
        config.hudLayoutPreset = id
        if (isSlot(id)) {
            val stored = config.hudLayoutSlots[id]
            if (!stored.isNullOrEmpty()) {
                config.hudLayoutSlots[PREVIOUS] = currentSnapshot()
                applySlot(stored)
            }
        } else {
            val builtin = BUILTINS.firstOrNull { it.id == id } ?: BUILTINS.first()
            config.hudLayoutSlots[PREVIOUS] = currentSnapshot()
            applyBuiltin(builtin)
        }
        config.save()
    }

    fun canUndo(config: WynnOverhaulConfig): Boolean = config.hudLayoutSlots[PREVIOUS]?.isNotEmpty() == true

    fun undo(config: WynnOverhaulConfig): Boolean {
        val previous = config.hudLayoutSlots[PREVIOUS]
        if (previous.isNullOrEmpty()) return false
        val current = currentSnapshot()
        applySlot(previous)
        config.hudLayoutSlots[PREVIOUS] = current
        config.save()
        return true
    }

    private fun currentSnapshot(): MutableMap<String, WynnOverhaulConfig.HudElementLayout> {
        val snapshot = LinkedHashMap<String, WynnOverhaulConfig.HudElementLayout>()
        for (elementId in HudLayoutManager.allIds()) snapshot[elementId] = HudLayoutManager.snapshot(elementId)
        return snapshot
    }

    fun saveTo(config: WynnOverhaulConfig, id: String) {
        if (!isSlot(id)) return
        config.hudLayoutSlots[id] = currentSnapshot()
        config.hudLayoutPreset = id
        config.save()
    }

    fun slotLabel(id: String): String {
        val filled = WynnOverhaulConfig.current.hudLayoutSlots[id]?.isNotEmpty() == true
        val name = "Custom ${id.removePrefix("SLOT_")}"
        return if (filled) "$name (overwrite)" else "$name (empty)"
    }

    private fun applyBuiltin(builtin: Builtin) {
        for (id in HudLayoutManager.allIds()) {
            val place = builtin.places[id]
            if (place == null) HudLayoutManager.resetPlacement(id) else HudLayoutManager.placeAt(id, place.corner, place.x, place.y, place.width, place.height)
        }
        val window = Minecraft.getInstance().window
        for ((id, offset) in NEAR_CROSSHAIR) {
            if (id in HudLayoutManager.allIds()) HudLayoutManager.placeCentered(id, offset.first, offset.second, window.guiScaledWidth, window.guiScaledHeight)
        }
    }

    private fun applySlot(stored: Map<String, WynnOverhaulConfig.HudElementLayout>) {
        for (id in HudLayoutManager.allIds()) {
            val layout = stored[id]
            if (layout == null) HudLayoutManager.resetPlacement(id) else HudLayoutManager.restore(id, layout)
        }
    }
}
