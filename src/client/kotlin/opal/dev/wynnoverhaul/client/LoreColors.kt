package opal.dev.wynnoverhaul.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

object LoreColors {
    fun lastColor(component: Component): Int? {
        var color: Int? = null
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                if (text.any { !it.isWhitespace() && !it.isSurrogate() && it.code !in 0xE000..0xF8FF }) {
                    color = style.color?.value
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return color
    }
}
