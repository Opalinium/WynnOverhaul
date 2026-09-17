package opal.dev.overwatch.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractWidget
import kotlin.math.roundToInt

fun truncateToWidth(font: Font, text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (font.width(text) <= maxWidth) return text
    val ellipsis = "…"
    val ellipsisWidth = font.width(ellipsis)
    return font.plainSubstrByWidth(text, (maxWidth - ellipsisWidth).coerceAtLeast(0)) + ellipsis
}

fun owNumberField(
    left: Int,
    width: Int,
    font: Font,
    label: String,
    min: Double,
    max: Double,
    decimals: Int,
    initial: Double,
    onChange: (Double) -> Unit,
): List<Pair<AbstractWidget, Int>> {
    val labelWidth = (width * 0.55).toInt()
    val labelWidget = OwLabel(left, 0, labelWidth, OwTheme.ROW_H, label)
    val field = OwTextField(font, left + labelWidth + OwTheme.GAP, 0, width - labelWidth - OwTheme.GAP, OwTheme.ROW_H)
    field.setMaxLength(10)
    field.setValue(if (decimals <= 0) initial.roundToInt().toString() else "%.${decimals}f".format(initial))
    field.setResponder { raw -> raw.toDoubleOrNull()?.let { onChange(it.coerceIn(min, max)) } }
    return listOf(labelWidget to 0, field to OwTheme.ROW_H)
}
