package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player

object PlayerIdentity {
    fun isReal(client: Minecraft, player: Player): Boolean {
        val connection = client.connection ?: return true
        val info = connection.getPlayerInfo(player.uuid) ?: return false
        return connection.listedOnlinePlayers.contains(info)
    }
}
