package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft

object WynnClassTracker {
    enum class Family { RIGHT_FIRST, LEFT_FIRST }

    @Volatile
    var family = Family.RIGHT_FIRST
        private set

    @Volatile
    var classKey: String? = null
        private set

    fun refresh(client: Minecraft) {
        if (classKey != null) return
        val stack = client.player?.mainHandItem ?: return
        if (stack.isEmpty) return
        val kind = WeaponAnimations.classKind(stack) ?: WeaponAnimations.autoKind(stack) ?: return
        applyKind(kind.name)
    }

    fun noteCastSpell(name: String) {
        val key = CLASS_BY_SPELL[name.lowercase().filter { it.isLetter() }] ?: return
        classKey = key
        family = if (key == "archer") Family.LEFT_FIRST else Family.RIGHT_FIRST
    }

    fun baseNames(): List<String>? = classKey?.let { BASE_SPELLS[it] }

    fun clear() {
        family = Family.RIGHT_FIRST
        classKey = null
    }

    private fun applyKind(kindName: String) {
        val key = when (kindName) {
            "SPEAR" -> "warrior"
            "DAGGER" -> "assassin"
            "WAND" -> "mage"
            "BOW" -> "archer"
            "RELIK" -> "shaman"
            else -> return
        }
        classKey = key
        family = if (key == "archer") Family.LEFT_FIRST else Family.RIGHT_FIRST
    }

    private val BASE_SPELLS = mapOf(
        "warrior" to listOf("Bash", "Charge", "Uppercut", "War Scream"),
        "mage" to listOf("Heal", "Teleport", "Meteor", "Ice Snake"),
        "archer" to listOf("Arrow Storm", "Escape", "Arrow Bomb", "Arrow Shield"),
        "assassin" to listOf("Spin Attack", "Dash", "Multihit", "Smoke Bomb"),
        "shaman" to listOf("Totem", "Haul", "Aura", "Uproot"),
    )

    private val CLASS_BY_SPELL = mapOf(
        "bash" to "warrior", "charge" to "warrior", "uppercut" to "warrior", "warscream" to "warrior",
        "heal" to "mage", "teleport" to "mage", "meteor" to "mage", "icesnake" to "mage",
        "arcanetransfer" to "mage", "ophanim" to "mage",
        "arrowstorm" to "archer", "escape" to "archer", "arrowbomb" to "archer", "bombarrow" to "archer",
        "arrowshield" to "archer", "phantomray" to "archer", "grapplinghook" to "archer",
        "guardianangels" to "archer",
        "spinattack" to "assassin", "dash" to "assassin", "multihit" to "assassin",
        "smokebomb" to "assassin", "lacerate" to "assassin", "backstab" to "assassin", "bamboozle" to "assassin",
        "totem" to "shaman", "haul" to "shaman", "aura" to "shaman", "uproot" to "shaman",
        "switchmasks" to "shaman",
    )
}
