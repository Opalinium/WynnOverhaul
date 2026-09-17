package opal.dev.overwatch.client

enum class PowderElement(val displayName: String, val weaponSpecial: String, val armorSpecial: String, val colorArgb: Int) {
    EARTH("Earth", "Quake", "Rage", 0xFF3D8B37.toInt()),
    THUNDER("Thunder", "Chain Lightning", "Kill Streak", 0xFFE6C619.toInt()),
    WATER("Water", "Curse", "Concentration", 0xFF3AB0C9.toInt()),
    FIRE("Fire", "Courage", "Endurance", 0xFFC93A3A.toInt()),
    AIR("Air", "Wind Prison", "Dodge", 0xFFD8D8D8.toInt()),
}

data class PowderTier(
    val tier: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val neutralToElementPercent: Int,
    val health: Int,
    val addedDefense: Int,
    val removedDefense: Int,
) {
    val minItemLevel: Int get() = PowderGuideData.MIN_ITEM_LEVEL[tier - 1]
}

object PowderGuideData {

    val MIN_ITEM_LEVEL = listOf(1, 5, 15, 25, 40, 55, 70)

    val TIERS: Map<PowderElement, List<PowderTier>> = mapOf(
        PowderElement.EARTH to listOf(
            PowderTier(1, 4, 5, 17, 5, 2, 1),
            PowderTier(2, 6, 7, 21, 10, 5, 2),
            PowderTier(3, 7, 9, 25, 20, 9, 3),
            PowderTier(4, 8, 9, 31, 30, 14, 4),
            PowderTier(5, 9, 11, 38, 45, 22, 7),
            PowderTier(6, 11, 13, 46, 60, 29, 7),
            PowderTier(7, 12, 14, 52, 75, 37, 12),
        ),
        PowderElement.THUNDER to listOf(
            PowderTier(1, 1, 8, 9, 5, 2, 1),
            PowderTier(2, 1, 12, 11, 10, 4, 1),
            PowderTier(3, 2, 14, 13, 20, 8, 2),
            PowderTier(4, 2, 15, 17, 30, 13, 3),
            PowderTier(5, 3, 17, 22, 45, 20, 5),
            PowderTier(6, 4, 19, 28, 60, 28, 6),
            PowderTier(7, 5, 21, 32, 75, 36, 11),
        ),
        PowderElement.WATER to listOf(
            PowderTier(1, 3, 4, 13, 5, 3, 1),
            PowderTier(2, 5, 6, 15, 10, 6, 1),
            PowderTier(3, 6, 8, 17, 20, 11, 3),
            PowderTier(4, 7, 8, 21, 30, 16, 4),
            PowderTier(5, 8, 10, 26, 45, 23, 6),
            PowderTier(6, 10, 13, 32, 60, 32, 10),
            PowderTier(7, 11, 15, 38, 75, 40, 15),
        ),
        PowderElement.FIRE to listOf(
            PowderTier(1, 2, 5, 14, 5, 3, 1),
            PowderTier(2, 4, 7, 16, 10, 6, 1),
            PowderTier(3, 5, 9, 19, 20, 10, 2),
            PowderTier(4, 6, 9, 24, 30, 15, 3),
            PowderTier(5, 7, 11, 30, 45, 22, 5),
            PowderTier(6, 9, 14, 37, 60, 31, 9),
            PowderTier(7, 10, 16, 44, 75, 39, 14),
        ),
        PowderElement.AIR to listOf(
            PowderTier(1, 2, 6, 11, 5, 3, 1),
            PowderTier(2, 3, 9, 14, 10, 6, 2),
            PowderTier(3, 4, 11, 17, 20, 10, 3),
            PowderTier(4, 5, 11, 22, 30, 16, 5),
            PowderTier(5, 7, 12, 28, 45, 23, 7),
            PowderTier(6, 8, 15, 35, 60, 30, 8),
            PowderTier(7, 9, 17, 42, 75, 38, 13),
        ),
    )

    val WEAPON_SPECIAL_DESC: Map<PowderElement, String> = mapOf(
        PowderElement.EARTH to "Deal 240/280/320/360/400/440/480% Main Attack damage and briefly stun all mobs within 4.5-7.5 blocks.",
        PowderElement.THUNDER to "Deal 200/225/250/275/300/325/350% Main Attack damage to a mob, chaining to another within 14 blocks, up to 5-11 times.",
        PowderElement.WATER to "Curse all mobs within 8 blocks, making them take 10/12.5/15/17.5/20/22.5/25% more damage for 4 seconds.",
        PowderElement.FIRE to "Deal 110/125/140/155/170/185/200% Main Attack damage to mobs within 5 blocks; you and nearby allies gain a 10-25% damage boost for 4 seconds.",
        PowderElement.AIR to "Hold nearby mobs in place for up to 5 seconds; the next hit on them deals 100/125/150/175/200/225/250% more damage.",
    )

    val ARMOR_SPECIAL_DESC: Map<PowderElement, String> = mapOf(
        PowderElement.EARTH to "+0.4/0.5/0.6/0.7/0.8/0.9/1% Earth damage per % missing health below 75%. Cap: +300% Earth damage.",
        PowderElement.THUNDER to "+6/7.5/9/10.5/12/13.5/15% Thunder damage for 10s on kill. Cap: +200% Thunder damage.",
        PowderElement.WATER to "+0.05-0.2% Water damage per mana spent (1s per mana) when casting a spell. Cap: +120% Water damage.",
        PowderElement.FIRE to "+2/3/4/5/6/7/8% Fire damage for 8s when you take damage. Cap: +120% Fire damage.",
        PowderElement.AIR to "+2/3/4/5/6/7/8% Air damage per second near a hostile mob (within 5 blocks), decaying over 20s once clear. Cap: +120% Air damage.",
    )
}
