package opal.dev.wynnoverhaul.client

import net.minecraft.network.chat.Component

object WynnUltimateTracker {
    @Volatile
    private var component: Component? = null

    fun update(value: Component?) {
        component = value
    }

    fun active(): Component? = component

    fun clear() {
        component = null
    }
}
