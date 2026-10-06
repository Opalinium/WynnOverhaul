package opal.dev.wynnoverhaul.client

import net.minecraft.world.item.ItemStack

object GatheringTool {
    private val NAME = Regex("""\b(Pickaxe|Axe|Scythe|Rod)\s+T\d+""")
    private var lastStack: ItemStack? = null
    private var lastKind: String? = null

    fun kind(stack: ItemStack?): String? {
        if (stack == null || stack.isEmpty) return null
        if (stack === lastStack) return lastKind
        lastStack = stack
        lastKind = NAME.find(TextClean.clean(stack.hoverName.string))?.groupValues?.get(1)?.uppercase()
        return lastKind
    }
}
