package opal.dev.wynnoverhaul.client

import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft

data class NodeRecord(val label: String, val profession: String = "", val availableAtMillis: Long = 0L)

object TrackerNodeStore {
    private val store = WorldKeyedJsonStore<NodeRecord>(
        "wynnoverhaul-nodes.json",
        object : TypeToken<MutableMap<String, MutableMap<String, NodeRecord>>>() {}.type,
        "tracked gathering nodes",
    )

    fun markDirty() = store.markDirty()
    fun sync(client: Minecraft, nodes: MutableMap<Long, NodeRecord>) = store.sync(client, nodes)
    fun detach(nodes: MutableMap<Long, NodeRecord>) = store.detach(nodes)
}
