package opal.dev.overwatch.client

data class MountTypeInfo(
    val displayName: String,
    val itemName: String,
    val summonItemName: String,
    val enclosure: String,
    val combatLevelReq: Int,
    val questReq: String,
    val characteristics: List<String>,
)

data class MountStatInfo(val displayName: String, val description: String)

object MountGuideData {

    val TYPES = listOf(
        MountTypeInfo(
            "Horse",
            "Saddle",
            "Whistle",
            "Ternaves Ranch, Wynn",
            9,
            "Stable Story",
            listOf("Highest top speed and good handling", "Slow acceleration"),
        ),
        MountTypeInfo(
            "Wyvern",
            "Reins",
            "Flute",
            "Bantisu Quarters, Gavel",
            84,
            "The Canyon Guides",
            listOf("Can fly above the ground, up to a maximum height", "Slower speed and acceleration"),
        ),
        MountTypeInfo(
            "Adasaur",
            "Harness",
            "Ocarina",
            "Aldwell Sanctuary, Fruma",
            115,
            "Burning Bonds",
            listOf("Quick acceleration and high jump/step height", "Poor handling, loses significant speed when turning"),
        ),
    )

    val STATS = listOf(
        MountStatInfo("Speed", "Increases the mount's maximum speed."),
        MountStatInfo("Acceleration", "Determines how quickly the mount can reach its maximum speed."),
        MountStatInfo("Jump Height / Altitude", "Increases jump height (Horse/Adasaur), or max flying height above ground (Wyvern)."),
        MountStatInfo("Energy", "Increases the mount's maximum Energy."),
        MountStatInfo("Handling", "How quickly the mount responds to turns, speed lost while turning, and step-up height."),
        MountStatInfo("Toughness", "Decreases the chance of being startled (dismounted) when taking damage."),
        MountStatInfo("Boost", "Increases the speed or Energy gained from boost powerups."),
        MountStatInfo("Training", "Increases the number of levels gained per stat powerup."),
    )

    const val NEW_MOUNT_POTENTIAL = 240
    const val NEW_MOUNT_STAT_LEVEL = 1
    const val NEW_MOUNT_STAT_LIMIT = 10
    const val NEW_MOUNT_STAT_MAX = 30
    const val BREEDING_AVERAGE_LIMIT = 20

    val FEEDING_TIME = listOf(
        10 to "1 minute",
        12 to "5 minutes",
        13 to "15 minutes",
        14 to "30 minutes",
        15 to "1 hour",
        16 to "2 hours",
        17 to "3 hours",
        18 to "4 hours",
        19 to "5 hours",
        20 to "6 hours (breeding-eligible)",
    )
}
