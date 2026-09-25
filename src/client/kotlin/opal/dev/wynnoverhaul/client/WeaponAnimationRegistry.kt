package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomModelData

data class WeaponAnimationEntry(
    var itemId: String = "",
    var itemModel: String? = null,
    var floats: List<Float> = emptyList(),
    var strings: List<String> = emptyList(),
    var name: String = "",
    var autoStyle: String = "",
    var style: String = WeaponAnimationRegistry.AUTO,
)

object WeaponAnimationRegistry {
    const val AUTO = "AUTO"

    private lateinit var registerKey: KeyMapping

    fun init() {
        registerKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.wynnoverhaul.weapon_anim",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                WynnOverhaulKeyCategory.CATEGORY,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        while (registerKey.consumeClick()) {
            val player = client.player ?: continue
            if (client.gui.screen() != null) continue
            val stack = ActiveWeapon.stack(player) ?: player.mainHandItem
            if (registerAndReport(stack)) client.setScreenAndShow(WynnOverhaulWeaponAnimationScreen(null))
        }
    }

    fun tryRegisterHovered(event: KeyEvent, stack: ItemStack): Boolean {
        if (registerKey.isUnbound || !registerKey.matches(event) || stack.isEmpty) return false
        registerAndReport(stack)
        return true
    }

    private fun registerAndReport(stack: ItemStack): Boolean {
        val player = Minecraft.getInstance().player
        val entry = register(stack)
        if (entry == null) {
            player?.sendSystemMessage(
                Component.literal("[WynnOverhaul] That isn't a weapon.").withStyle(ChatFormatting.RED),
            )
            return false
        }
        player?.sendSystemMessage(
            Component.literal("[WynnOverhaul] Registered ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" for weapon animations.").withStyle(ChatFormatting.GRAY)),
        )
        return true
    }

    fun entries(): MutableList<WeaponAnimationEntry> {
        val config = WynnOverhaulConfig.current
        return config.weaponAnimationEntries ?: mutableListOf<WeaponAnimationEntry>().also { config.weaponAnimationEntries = it }
    }

    fun keyOf(stack: ItemStack): String {
        val data = stack.get(DataComponents.CUSTOM_MODEL_DATA)
        val model = stack.get(DataComponents.ITEM_MODEL)?.toString() ?: BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return "$model|${data?.floats().orEmpty()}|${data?.strings().orEmpty()}"
    }

    private fun keyOf(entry: WeaponAnimationEntry): String =
        "${entry.itemModel ?: entry.itemId}|${entry.floats}|${entry.strings}"

    fun entryFor(stack: ItemStack): WeaponAnimationEntry? {
        val key = keyOf(stack)
        return entries().firstOrNull { keyOf(it) == key }
    }

    fun styleFor(stack: ItemStack): String? =
        entryFor(stack)?.style?.takeIf { it != AUTO }

    fun register(stack: ItemStack): WeaponAnimationEntry? {
        if (stack.isEmpty || WynnWeapons.attacksPerSecond(stack) == null) return null
        entryFor(stack)?.let { return it }
        val data = stack.get(DataComponents.CUSTOM_MODEL_DATA)
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val model = stack.get(DataComponents.ITEM_MODEL)?.toString()?.takeIf { it != itemId }
        val entry = WeaponAnimationEntry(
            itemId = itemId,
            itemModel = model,
            floats = data?.floats().orEmpty().toList(),
            strings = data?.strings().orEmpty().toList(),
            name = cleanName(stack.hoverName.string),
            autoStyle = WeaponAnimations.autoStyleKey(stack).orEmpty(),
        )
        entries().add(entry)
        changed()
        return entry
    }

    fun remove(entry: WeaponAnimationEntry) {
        entries().remove(entry)
        changed()
    }

    fun setStyle(entry: WeaponAnimationEntry, style: String) {
        entry.style = style
        changed()
    }

    fun cycle(entry: WeaponAnimationEntry, step: Int) {
        val keys = WeaponAnimations.styleKeys()
        val index = keys.indexOf(entry.style).takeIf { it >= 0 } ?: 0
        setStyle(entry, keys[Math.floorMod(index + step, keys.size)])
    }

    fun preview(entry: WeaponAnimationEntry): ItemStack {
        val item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(entry.itemId)).orElse(Items.BARRIER)
        val stack = ItemStack(item)
        if (entry.floats.isNotEmpty() || entry.strings.isNotEmpty()) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, CustomModelData(entry.floats, emptyList(), entry.strings, emptyList()))
        }
        entry.itemModel?.let { stack.set(DataComponents.ITEM_MODEL, Identifier.parse(it)) }
        return stack
    }

    fun modelSummary(entry: WeaponAnimationEntry): String {
        val parts = ArrayList<String>()
        parts += entry.itemModel ?: entry.itemId.removePrefix("minecraft:")
        entry.floats.forEach { parts += it.toString().removeSuffix(".0") }
        entry.strings.forEach { parts += it }
        return parts.joinToString(" / ")
    }

    private fun changed() {
        WynnOverhaulConfig.current.save()
    }

    private fun cleanName(raw: String): String =
        raw.filter { !Character.isSurrogate(it) && it !in ''..'' }.trim()
}
