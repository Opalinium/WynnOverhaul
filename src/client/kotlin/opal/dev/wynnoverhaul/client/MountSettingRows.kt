package opal.dev.wynnoverhaul.client

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object MountSettingRows {
    data class Option(val label: String, val active: Boolean)

    data class Setting(
        val slot: Int,
        val title: String,
        val description: String,
        val options: List<Option>,
        val hint: String,
    ) {
        val isInfo: Boolean get() = description.isEmpty() && options.isEmpty() && hint.isEmpty()
    }

    private const val INACTIVE_COLOR = 0x555555

    fun parse(slot: Int, stack: ItemStack): Setting {
        val title = TextClean.clean(stack.hoverName.string)
        val lines = WynnItemRarity.loreLines(stack)
        val components = stack.get(DataComponents.LORE)?.lines()
        val description = ArrayList<String>()
        val options = ArrayList<Option>()
        val hints = ArrayList<String>()
        for ((i, raw) in lines.withIndex()) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            when {
                text.startsWith("-") -> {
                    val color = components?.getOrNull(i)?.let { LoreColors.lastColor(it) }
                    options.add(Option(text.removePrefix("-").trim(), color != null && color != INACTIVE_COLOR))
                }
                text.contains("Click") -> hints.add(text)
                else -> description.add(text)
            }
        }
        return Setting(slot, title, description.joinToString(" "), options, hints.joinToString("  ·  "))
    }
}
