package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.RegistryOps
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul

object WynnOverhaulItemDebug {
    fun tryCopyToClipboard(stack: ItemStack): Boolean {
        if (!WynnOverhaulConfig.current.debugItemCopyEnabled) return false
        if (stack.isEmpty) return false
        val id = BuiltInRegistries.ITEM.getKey(stack.item)
        val registryAccess = Minecraft.getInstance().level?.registryAccess()
        val dump = buildString {
            appendLine("Item: $id x${stack.count}")
            appendLine("HoverName: ${stack.hoverName}")
            if (registryAccess != null) {
                val ops = RegistryOps.create(NbtOps.INSTANCE, registryAccess)
                ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent { tag ->
                    append(NbtUtils.prettyPrint(tag, true))
                }
            }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(dump)
        WynnOverhaul.LOGGER.info("WynnOverhaul debug: copied {} to clipboard ({} chars)", id, dump.length)
        return true
    }
}
