package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import java.util.UUID

object WynnWorldTracker {
    private val WORLD_ENTRY: UUID = UUID.fromString("16ff7452-714f-2752-b3cd-c3cb2068f6af")
    private val WORLD_NAME = Regex("""Global \[(.+?)]""")

    fun current(): String? {
        val connection = Minecraft.getInstance().connection ?: return null
        val text = connection.getPlayerInfo(WORLD_ENTRY)?.tabListDisplayName?.string ?: return null
        return WORLD_NAME.find(text)?.groupValues?.get(1)
    }
}
