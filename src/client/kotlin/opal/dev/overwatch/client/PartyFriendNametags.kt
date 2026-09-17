package opal.dev.overwatch.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

object PartyFriendNametags {

    fun decorate(player: Player, original: Component): Component {
        val config = OverwatchConfig.current
        if (!config.customPartyNametagsEnabled) return original
        val client = Minecraft.getInstance()
        if (player === client.player) return original

        val name = player.gameProfile.name
        val prefix = when {
            PartyFriendModel.partyMembers.contains(name) -> PARTY_PREFIX
            PartyFriendModel.friends.contains(name) -> FRIEND_PREFIX
            else -> return original
        }
        return Component.empty().append(prefix).append(original)
    }

    private val PARTY_PREFIX: Component = Component.literal("[Party] ").withStyle(ChatFormatting.LIGHT_PURPLE)
    private val FRIEND_PREFIX: Component = Component.literal("[Friend] ").withStyle(ChatFormatting.AQUA)
}
