package opal.dev.overwatch.client

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object WaypointIcons {
    private val eggCache = HashMap<Identifier, ItemStack>()

    val chest: ItemStack by lazy { ItemStack(Items.CHEST) }
    val beacon: ItemStack by lazy { ItemStack(Items.BEACON) }
    val task: ItemStack by lazy { ItemStack(Items.COMPASS) }
    val fallbackMob: ItemStack by lazy { ItemStack(Items.IRON_SWORD) }
    val mining: ItemStack by lazy { ItemStack(Items.IRON_PICKAXE) }
    val woodcutting: ItemStack by lazy { ItemStack(Items.IRON_AXE) }
    val farming: ItemStack by lazy { ItemStack(Items.IRON_HOE) }
    val fishing: ItemStack by lazy { ItemStack(Items.FISHING_ROD) }

    private val activityStacks = HashMap<ActivityType, ItemStack>()

    fun node(profession: String): ItemStack = when (profession.uppercase()) {
        "WOODCUTTING" -> woodcutting
        "FARMING" -> farming
        "FISHING" -> fishing
        else -> mining
    }

    fun activity(type: ActivityType): ItemStack = activityStacks.getOrPut(type) {
        ItemStack(
            when (type) {
                ActivityType.QUEST -> Items.WRITTEN_BOOK
                ActivityType.STORYLINE_QUEST -> Items.ENCHANTED_BOOK
                ActivityType.MINI_QUEST -> Items.BOOK
                ActivityType.WORLD_EVENT -> Items.FIREWORK_ROCKET
                ActivityType.SECRET_DISCOVERY, ActivityType.WORLD_DISCOVERY, ActivityType.TERRITORIAL_DISCOVERY -> Items.SPYGLASS
                ActivityType.CAVE -> Items.LANTERN
                ActivityType.DUNGEON -> Items.SKELETON_SKULL
                ActivityType.RAID -> Items.DRAGON_HEAD
                ActivityType.BOSS_ALTAR -> Items.NETHER_STAR
                ActivityType.LOOTRUN_CAMP -> Items.CAMPFIRE
                ActivityType.MOUNT_ENCLOSURE -> Items.SADDLE
            },
        )
    }

    fun mob(entity: Entity?): ItemStack {
        val key = entity?.let { BuiltInRegistries.ENTITY_TYPE.getKey(it.type) } ?: return fallbackMob
        return eggCache.getOrPut(key) {
            val egg = Identifier.fromNamespaceAndPath(key.namespace, key.path + "_spawn_egg")
            val item: Item? = BuiltInRegistries.ITEM.getOptional(egg).orElse(null)
            if (item != null) ItemStack(item) else fallbackMob
        }
    }
}
