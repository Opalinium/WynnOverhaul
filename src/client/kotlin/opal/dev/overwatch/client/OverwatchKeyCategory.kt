package opal.dev.overwatch.client

import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

object OverwatchKeyCategory {
    val CATEGORY: KeyMapping.Category by lazy {
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("overwatch", "main"))
    }
}
