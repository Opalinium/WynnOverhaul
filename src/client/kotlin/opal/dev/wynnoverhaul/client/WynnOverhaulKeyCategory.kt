package opal.dev.wynnoverhaul.client

import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

object WynnOverhaulKeyCategory {
    val CATEGORY: KeyMapping.Category by lazy {
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("wynnoverhaul", "main"))
    }
}
