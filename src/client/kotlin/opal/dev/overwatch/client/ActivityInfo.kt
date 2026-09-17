package opal.dev.overwatch.client

import net.minecraft.world.item.ItemStack

data class ActivityInfo(
    val icon: ItemStack,
    val type: ActivityType,
    val name: String,
    val status: ActivityStatus,
    val specialInfo: String? = null,
    val description: String? = null,
    val length: ActivityLength? = null,
    val distance: ActivityDistance? = null,
    val difficulty: ActivityDifficulty? = null,
    val levelReq: Int = 0,
    val levelReqFulfilled: Boolean = true,
    val professionReqs: List<ProfessionRequirement> = emptyList(),
    val questReqs: List<QuestRequirement> = emptyList(),
    val rewards: Map<ActivityRewardType, List<String>> = emptyMap(),
    val trackingState: ActivityTrackingState = ActivityTrackingState.UNTRACKABLE,
)

data class ProfessionRequirement(val profession: String, val level: Int, val fulfilled: Boolean)
data class QuestRequirement(val questName: String, val fulfilled: Boolean)

enum class ActivityType(val displayName: String, val filterName: String, val colorArgb: Int) {
    QUEST("Quest", "Quest", 0xFF29CC96.toInt()),
    STORYLINE_QUEST("Quest", "Quest", 0xFF33B33B.toInt()),
    MINI_QUEST("Mini-Quest", "Mini-Quest", 0xFFB38FAD.toInt()),
    WORLD_EVENT("World Event", "World Event", 0xFF00BDBF.toInt()),
    SECRET_DISCOVERY("Secret Discovery", "Secret Discovery", 0xFFA1C3E6.toInt()),
    WORLD_DISCOVERY("World Discovery", "World Discovery", 0xFFA1C3E6.toInt()),
    TERRITORIAL_DISCOVERY("Territorial Discovery", "Territorial Discovery", 0xFFA1C3E6.toInt()),
    CAVE("Cave", "Cave", 0xFFFF8C19.toInt()),
    DUNGEON("Dungeon", "Dungeon", 0xFFCC6677.toInt()),
    RAID("Raid", "Raid", 0xFFD6401E.toInt()),
    BOSS_ALTAR("Boss Altar", "Boss Altar", 0xFFF2D349.toInt()),
    LOOTRUN_CAMP("Lootrun Camp", "Lootrun Camp", 0xFF3399CC.toInt()),
    MOUNT_ENCLOSURE("Mount Enclosure", "Mount Enclosure", 0xFF8F663D.toInt()),
    ;

    val isQuest: Boolean get() = this == QUEST || this == STORYLINE_QUEST || this == MINI_QUEST

    companion object {
        fun from(colorArgb: Int?, displayName: String): ActivityType? =
            entries.firstOrNull { it.displayName == displayName && (colorArgb == null || it.colorArgb == (0xFF000000.toInt() or colorArgb)) }
                ?: entries.firstOrNull { it.displayName == displayName }
    }
}

enum class ActivityStatus { STARTED, AVAILABLE, UNAVAILABLE, COMPLETED }

enum class ActivityLength { SHORT, MEDIUM, LONG }

enum class ActivityDistance { VERY_NEAR, NEAR, MEDIUM, FAR }

enum class ActivityDifficulty { EASY, MEDIUM, HARD }

enum class ActivityRewardType { XP, EMERALDS, ACCESS, ITEM }

enum class ActivityTrackingState { TRACKED, TRACKABLE, UNTRACKABLE }
