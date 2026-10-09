package opal.dev.wynnoverhaul.client

import com.google.gson.JsonParser
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul

object WynnSkinStyles {
    private const val RESOURCE = "/assets/wynnoverhaul/animations/skin_styles.json"

    private val table: Map<String, Map<Int, String>> by lazy { load() }

    fun styleFor(stack: ItemStack): String? {
        val model = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.floats()?.firstOrNull() ?: return null
        val item = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return table[item]?.get(model.toInt())
    }

    private fun load(): Map<String, Map<Int, String>> {
        val stream = WynnSkinStyles::class.java.getResourceAsStream(RESOURCE) ?: return emptyMap()
        return try {
            stream.bufferedReader().use { reader ->
                JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (item, styles) ->
                    item to styles.asJsonObject.entrySet().associate { (cmd, style) -> cmd.toInt() to style.asString }
                }
            }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("Skin styles failed to load", t)
            emptyMap()
        }
    }
}
