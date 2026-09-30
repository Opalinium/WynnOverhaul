package opal.dev.wynnoverhaul.client

import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft

data class ChestRecord(val label: String, val tier: Int = 0, val availableAtMillis: Long = 0L)

object TrackerChestStore {
    private val store = WorldKeyedJsonStore<ChestRecord>(
        "wynnoverhaul-chests.json",
        object : TypeToken<MutableMap<String, MutableMap<String, ChestRecord>>>() {}.type,
        "tracked chests",
    )

    fun markDirty() = store.markDirty()
    fun sync(client: Minecraft, chests: MutableMap<Long, ChestRecord>) = store.sync(client, chests)
    fun detach(chests: MutableMap<Long, ChestRecord>) = store.detach(chests)
}
