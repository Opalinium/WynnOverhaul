package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object PartyFriendModel {
    @Volatile
    var partyMembers: Set<String> = emptySet()
        private set

    @Volatile
    var friends: Set<String> = emptySet()
        private set

    private var lastWorldKey: String? = null
    private var pendingRequest = false

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onMessage(message) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onMessage(message) }
    }

    fun tick(client: Minecraft) {
        val key = worldKey(client)
        if (key != lastWorldKey) {
            lastWorldKey = key
            partyMembers = emptySet()
            friends = emptySet()
            pendingRequest = key != null
        }
        if (pendingRequest && WynnScoreboardTracker.sidebarText.isNotEmpty()) {
            pendingRequest = false
            requestData(client)
        }
    }

    private fun requestData(client: Minecraft) {
        val connection = client.connection ?: return
        connection.sendCommand("party list")
        connection.sendCommand("friend list")
    }

    private fun worldKey(client: Minecraft): String? {
        val player = client.player ?: return null
        val server = client.currentServer?.ip?.trim()?.lowercase()
        val singleplayer = try {
            client.singleplayerServer != null
        } catch (t: Throwable) {
            false
        }
        val serverKey = if (singleplayer) "sp" else server.takeUnless { it.isNullOrBlank() } ?: return null
        return "$serverKey/${player.uuid}"
    }

    private fun onMessage(message: Component) {
        val text = TextClean.clean(message.string)
        if (text.isEmpty()) return

        PARTY_LIST.find(text)?.let { partyMembers = splitNames(it.groupValues[1]).toSet(); return }
        if (PARTY_COMMAND_FAILED.matches(text) || PARTY_LEFT.matches(text) || PARTY_KICKED.matches(text) || PARTY_DISBANDED.matches(text)) {
            partyMembers = emptySet()
            return
        }
        PARTY_SOMEONE_JOINED.find(text)?.let { partyMembers = partyMembers + it.groupValues[1].trim(); return }
        PARTY_OTHER_LEFT.find(text)?.let { partyMembers = partyMembers - it.groupValues[1].trim(); return }
        PARTY_OTHER_KICKED.find(text)?.let { partyMembers = partyMembers - it.groupValues[1].trim(); return }

        FRIEND_LIST.find(text)?.let { friends = splitNames(it.groupValues[1]).toSet(); return }
        FRIEND_ADD.find(text)?.let { friends = friends + it.groupValues[1].trim(); return }
        FRIEND_REMOVE.find(text)?.let { friends = friends - it.groupValues[1].trim(); return }
    }

    private fun splitNames(raw: String): List<String> =
        raw.split(",").map { it.trim().removePrefix("and ").trim() }.filter { it.isNotEmpty() }

    private val PARTY_LIST = Regex("""^Party members: (.*)$""")
    private val PARTY_COMMAND_FAILED = Regex("""^You must be in a party to use this\.?$""")
    private val PARTY_LEFT = Regex("""^You have left your current party\.?$""")
    private val PARTY_KICKED = Regex("""^You have been kicked from your party\.?$""")
    private val PARTY_DISBANDED = Regex("""^Your party has been disbanded\.?$""")
    private val PARTY_SOMEONE_JOINED = Regex("""^(.+) has joined your party, say hello!$""")
    private val PARTY_OTHER_LEFT = Regex("""^(.+) has left the party!$""")
    private val PARTY_OTHER_KICKED = Regex("""^(.+) has been kicked from the party!$""")

    private val FRIEND_LIST = Regex("""^.+'s? friends \([^)]*\): (.*)$""")
    private val FRIEND_ADD = Regex("""^(.+) has been added to your friends!$""")
    private val FRIEND_REMOVE = Regex("""^(.+) has been removed from your friends!$""")
}
