package opal.dev.wynnoverhaul.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Items
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.EquipmentAssets
import net.minecraft.world.item.equipment.Equippable
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

object TitleLoadout {
    private const val CHECK_TICKS = 100
    private const val ICON = 18
    private const val GAP = 3
    private const val RETRY_NANOS = 5_000_000_000L
    private val ARMOR = listOf(
        "head" to EquipmentSlot.HEAD,
        "chest" to EquipmentSlot.CHEST,
        "legs" to EquipmentSlot.LEGS,
        "feet" to EquipmentSlot.FEET,
    )
    private val SLOT_ORDER = listOf("head", "chest", "legs", "feet", "weapon", "acc0", "acc1", "acc2", "acc3")

    private class Saved(val name: String, val level: Int?, val classKey: String?, val stacks: Map<String, ItemStack>)

    private val file: Path get() = FabricLoader.getInstance().configDir.resolve("wynnoverhaul").resolve("loadout.json")

    private var ticks = 0
    private var lastSignature = ""
    private var saved: Saved? = null
    private var loaded = false
    private var lastAttempt = 0L

    fun tick(client: Minecraft) {
        if (!WynnOverhaulConfig.current.titleLoadoutEnabled || !WynnOverhaulGate.inGame) return
        if (++ticks < CHECK_TICKS) return
        ticks = 0
        val player = client.player ?: return
        val level = client.level ?: return
        val stacks = LinkedHashMap<String, ItemStack>()
        for ((slot, equipment) in ARMOR) player.getItemBySlot(equipment).takeIf { !it.isEmpty }?.let { stacks[slot] = it }
        val inventory = player.inventory
        (0..8).map { inventory.getItem(it) }.firstOrNull { !it.isEmpty && WynnWeapons.attacksPerSecond(it) != null }?.let { stacks["weapon"] = it }
        for (i in 0..3) inventory.getItem(9 + i).takeIf { !it.isEmpty }?.let { stacks["acc$i"] = it }
        val levelValue = WynnLevelTracker.level
        val classKey = WynnClassTracker.classKey
        val signature = stacks.entries.joinToString(";") { "${it.key}=${ItemStack.hashItemAndComponents(it.value)}" } + "|$levelValue|$classKey"
        if (signature == lastSignature) return
        lastSignature = signature
        val ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        val json = JsonObject()
        json.addProperty("name", client.user.name)
        levelValue?.let { json.addProperty("level", it) }
        classKey?.let { json.addProperty("classKey", it) }
        val stackJson = JsonObject()
        for ((slot, stack) in stacks) {
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent { stackJson.add(slot, it) }
        }
        json.add("stacks", stackJson)
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.toString())
        }.onFailure { WynnOverhaul.LOGGER.warn("Could not save the title loadout", it) }
        saved = null
        loaded = false
        ItemPackCache.buildAsync(stacks.map { (slot, stack) -> specFor(slot, stack) })
    }

    private fun specFor(slot: String, stack: ItemStack): StackSpec {
        val itemKey = BuiltInRegistries.ITEM.getKey(stack.item)
        val model = stack.get(DataComponents.ITEM_MODEL) ?: itemKey
        val cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA)
        val asset = stack.get(DataComponents.EQUIPPABLE)?.assetId()?.map { it.identifier().path }?.orElse(null)
        return StackSpec(
            slot,
            model.path,
            cmd?.floats() ?: emptyList(),
            cmd?.flags() ?: emptyList(),
            cmd?.strings() ?: emptyList(),
            cmd?.colors() ?: emptyList(),
            asset,
            stack.damageValue,
            stack.maxDamage,
        )
    }

    private fun bindItemComponents(lookup: HolderLookup.Provider) {
        if (runCatching { Items.STONE.components() }.isSuccess) return
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach { pending ->
            if (pending.key() == Registries.ITEM) pending.apply()
        }
    }

    private fun load(): Saved? {
        if (loaded) return saved
        val now = System.nanoTime()
        if (lastAttempt != 0L && now - lastAttempt < RETRY_NANOS) return null
        lastAttempt = now
        if (!Files.exists(file)) return null
        saved = runCatching {
            val json = JsonParser.parseString(Files.readString(file)).asJsonObject
            val lookup = VanillaRegistries.createLookup()
            bindItemComponents(lookup)
            val ops = RegistryOps.create(JsonOps.INSTANCE, lookup)
            val stacks = LinkedHashMap<String, ItemStack>()
            json.getAsJsonObject("stacks")?.entrySet()?.forEach { (slot, element) ->
                ItemStack.CODEC.parse(ops, element).result().ifPresent { stacks[slot] = it }
            }
            Saved(
                json.get("name")?.asString ?: "",
                json.get("level")?.takeIf { !it.isJsonNull }?.asInt,
                json.get("classKey")?.takeIf { !it.isJsonNull }?.asString,
                stacks,
            )
        }.onFailure { WynnOverhaul.LOGGER.warn("Could not load the title loadout", it) }.getOrNull()
        loaded = saved != null
        return saved
    }

    fun available(): Boolean = WynnOverhaulConfig.current.titleLoadoutEnabled && load() != null

    private fun display(slot: String, stack: ItemStack): ItemStack {
        val info = ItemPackCache.slotInfo(slot) ?: return stack
        val copy = stack.copy()
        if (info.model) copy.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(ItemPackCache.NS, "s_$slot"))
        val asset = info.equipment
        val equippable = copy.get(DataComponents.EQUIPPABLE)
        if (asset != null && equippable != null) {
            val key = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(ItemPackCache.NS, asset))
            copy.set(
                DataComponents.EQUIPPABLE,
                Equippable(
                    equippable.slot(), equippable.equipSound(), Optional.of(key), equippable.cameraOverlay(), equippable.allowedEntities(),
                    equippable.dispensable(), equippable.swappable(), equippable.damageOnHurt(), equippable.equipOnInteract(),
                    equippable.canBeSheared(), equippable.shearingSound(),
                ),
            )
        }
        return copy
    }

    fun applyToFigure(state: AvatarRenderState) {
        val data = if (WynnOverhaulConfig.current.titleLoadoutEnabled) load() else null
        fun stack(slot: String): ItemStack = data?.stacks?.get(slot)?.let { display(slot, it) } ?: ItemStack.EMPTY
        val head = stack("head")
        val headSkinned = !head.isEmpty && ItemPackCache.slotInfo("head")?.model == true
        state.headEquipment = if (headSkinned) ItemStack.EMPTY else head
        state.headItem.clear()
        if (headSkinned) {
            Minecraft.getInstance().itemModelResolver.updateForTopItem(state.headItem, head, ItemDisplayContext.HEAD, null, null, 0)
        }
        state.chestEquipment = stack("chest")
        state.legsEquipment = stack("legs")
        state.feetEquipment = stack("feet")
        val weapon = stack("weapon")
        state.rightHandItemStack = weapon
        state.rightHandItemState.clear()
        if (!weapon.isEmpty) {
            Minecraft.getInstance().itemModelResolver.updateForTopItem(state.rightHandItemState, weapon, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, null, null, 0)
            state.rightArmPose = HumanoidModel.ArmPose.ITEM
        }
    }

    fun drawCard(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        val data = if (WynnOverhaulConfig.current.titleLoadoutEnabled) load() else null
        if (data == null || data.stacks.isEmpty()) return
        val font = Minecraft.getInstance().font
        val entries = SLOT_ORDER.mapNotNull { slot -> data.stacks[slot]?.let { slot to display(slot, it) } }
        val title = data.name
        val detail = listOfNotNull(data.level?.let { "Lv. $it" }, data.classKey?.replaceFirstChar { it.uppercase() }).joinToString("  ")
        val iconsW = entries.size * ICON + (entries.size - 1) * GAP
        val w = maxOf(iconsW, font.width(title), font.width(detail)) + 16
        val h = 8 + 11 + (if (detail.isEmpty()) 0 else 11) + 4 + ICON + 8
        val x = width - 16 - w
        val y = 16
        graphics.fill(x, y, x + w, y + h, 0xB00C0906.toInt())
        graphics.fill(x, y, x + w, y + 1, OwTheme.HAIRLINE)
        graphics.fill(x, y + h - 1, x + w, y + h, OwTheme.HAIRLINE)
        graphics.fill(x, y, x + 1, y + h, OwTheme.HAIRLINE)
        graphics.fill(x + w - 1, y, x + w, y + h, OwTheme.HAIRLINE)
        var ty = y + 8
        graphics.text(font, title, x + 8, ty, OwTheme.ACCENT, true)
        ty += 11
        if (detail.isNotEmpty()) {
            graphics.text(font, detail, x + 8, ty, OwTheme.TEXT_DIM, true)
            ty += 11
        }
        ty += 4
        var ix = x + 8 + (w - 16 - iconsW) / 2
        for ((_, stack) in entries) {
            graphics.fill(ix, ty, ix + ICON, ty + ICON, 0x40000000)
            graphics.item(stack, ix + 1, ty + 1)
            ix += ICON + GAP
        }
    }
}
