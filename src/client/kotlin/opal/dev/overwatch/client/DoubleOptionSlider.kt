package opal.dev.overwatch.client

import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component

internal class DoubleOptionSlider(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val label: String,
    private val min: Double,
    private val max: Double,
    private val decimals: Int,
    initial: Double,
    tooltip: Tooltip? = null,
    private val onChange: (Double) -> Unit,
) : AbstractSliderButton(x, y, width, height, Component.literal(""), ((initial - min) / (max - min)).coerceIn(0.0, 1.0)) {

    init {
        updateMessage()
        if (tooltip != null) setTooltip(tooltip)
    }

    override fun updateMessage() {
        val current = min + value * (max - min)
        setMessage(Component.literal("$label: ${"%.${decimals}f".format(current)}"))
    }

    override fun applyValue() {
        onChange(min + value * (max - min))
    }
}
