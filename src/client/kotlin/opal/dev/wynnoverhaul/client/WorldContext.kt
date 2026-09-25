package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.world.level.storage.LevelResource

object WorldContext {
    fun key(client: Minecraft): String? = try {
        val singleplayer = client.singleplayerServer
        if (singleplayer != null) {
            val dir = singleplayer.getWorldPath(LevelResource.ROOT).fileName?.toString()
            if (dir.isNullOrBlank()) null else "sp/$dir"
        } else {
            val ip = client.currentServer?.ip?.trim()?.lowercase()
            if (ip.isNullOrBlank()) null else "mp/${ip.substringBefore(':')}"
        }
    } catch (t: Throwable) {
        null
    }

    fun isWynncraft(client: Minecraft): Boolean =
        client.currentServer?.ip?.contains("wynncraft", ignoreCase = true) == true
}
