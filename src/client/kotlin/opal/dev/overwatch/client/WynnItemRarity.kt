package opal.dev.overwatch.client

import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import java.util.Optional

enum class WynnRarity(val colorRgb: Int, val displayName: String) {
    NORMAL(0xFFFFFF, "Normal"),
    UNIQUE(0xFFFF55, "Unique"),
    RARE(0xFF55FF, "Rare"),
    LEGENDARY(0x55FFFF, "Legendary"),
    FABLED(0xFF5555, "Fabled"),
    MYTHIC(0xAA00AA, "Mythic"),
}

object WynnItemRarity {
    private val byColor = WynnRarity.entries.associateBy { it.colorRgb }

    fun of(stack: ItemStack): WynnRarity? {
        if (stack.isEmpty) return null
        val color = firstStyledColor(stack.hoverName) ?: return null
        return byColor[color]
    }

    private fun firstStyledColor(text: FormattedText): Int? {
        var found: Int? = null
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, _ ->
                if (found == null) found = style.color?.value
                Optional.empty()
            },
            Style.EMPTY,
        )
        return found
    }
}
