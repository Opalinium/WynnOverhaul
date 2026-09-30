package opal.dev.wynnoverhaul.client

import net.minecraft.resources.Identifier

object SpellIcons {
    private val KEYS = setOf(
        "bash", "charge", "uppercut", "warscream",
        "heal", "teleport", "meteor", "icesnake",
        "arrowstorm", "escape", "arrowbomb", "arrowshield",
        "spinattack", "dash", "multihit", "smokebomb",
        "totem", "haul", "aura", "uproot",
    )

    fun iconFor(name: String?): Identifier? {
        val key = name?.lowercase()?.filter { it.isLetter() } ?: return null
        if (key !in KEYS) return null
        return Identifier.fromNamespaceAndPath("wynnoverhaul", "spell/$key")
    }
}
