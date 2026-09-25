package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnDirectMessages {
    class Pm(val partner: String, val outgoing: Boolean, val body: String)

    class Line(val text: String, val outgoing: Boolean, val at: Long)

    class Conversation(val name: String) {
        var lastAt = 0L
        var lastIncomingAt = 0L
        var unread = 0
        val lines = ArrayDeque<Line>()
    }

    class Suggestion(val name: String, val group: String)

    private const val PM_MARK = ''
    private const val MAX_LINES = 100
    private const val MAX_CONVERSATIONS = 24
    private const val SMART_WINDOW_MS = 3 * 60 * 1000L
    private const val GUILD_CAPTURE_MS = 1500L
    private const val PER_GROUP = 5
    private const val SENT_WINDOW_MS = 30_000L
    private val NAME_PATTERN = Regex("^[A-Za-z0-9_]{3,16}$")
    private val TOKEN_PATTERN = Regex("[A-Za-z0-9_]{3,16}")
    private val SEND_COMMANDS = setOf("msg", "w", "tell", "whisper", "m", "pm", "message")
    private val REPLY_COMMANDS = setOf("r", "reply")

    private val conversations = LinkedHashMap<String, Conversation>()
    private val sentTo = HashMap<String, Long>()

    @Volatile
    private var guildTokens: Set<String> = emptySet()
    private var guildCaptureUntil = 0L
    private var guildRequested = false
    private var lastWorldKey: String? = null
    private var lastIncomingPartner: String? = null

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onMessage(message) }
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ -> allow(message) }
        ClientSendMessageEvents.COMMAND.register { command -> onCommand(command) }
    }

    fun tick(client: Minecraft) {
        val key = client.currentServer?.ip?.lowercase() + "/" + client.player?.uuid
        if (key != lastWorldKey) {
            lastWorldKey = key
            conversations.clear()
            guildTokens = emptySet()
            guildRequested = false
            lastIncomingPartner = null
            PlayerLookup.clear()
            sentTo.clear()
        }
    }

    fun parse(raw: String): Pm? {
        val idx = raw.indexOf(PM_MARK)
        if (idx <= 0) return null
        val left = cleanName(raw.substring(0, idx))
        val rest = raw.substring(idx + 1)
        val colon = rest.indexOf(':')
        if (colon <= 0) return null
        val right = cleanName(rest.substring(0, colon))
        val body = rest.substring(colon + 1).trim()
        if (left.isEmpty() || right.isEmpty() || left.length > 40 || right.length > 40) return null
        val me = Minecraft.getInstance().player?.name?.string.orEmpty()
        val pm = when {
            isMe(left, me) -> Pm(right, true, body)
            isMe(right, me) -> Pm(left, false, body)
            else -> Pm(left, false, body)
        }
        return if (NAME_PATTERN.matches(pm.partner) && !isMe(pm.partner, me)) pm else null
    }

    private fun isMe(name: String, me: String): Boolean =
        name.equals(me, ignoreCase = true) || name.equals("you", ignoreCase = true) || name.equals("me", ignoreCase = true)

    private fun cleanName(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            if (c in '\uD800'..'\uDFFF' || c in ''..'') continue
            sb.append(c)
        }
        return sb.toString().replace(Regex("§."), "").trim().trim('[', ']', '(', ')').trim()
    }

    private fun conversation(name: String): Conversation {
        val key = name.lowercase()
        val existing = conversations[key]
        if (existing != null) return existing
        val created = Conversation(name)
        conversations[key] = created
        if (conversations.size > MAX_CONVERSATIONS) {
            val oldest = conversations.values.minByOrNull { it.lastAt }
            if (oldest != null && oldest !== created) conversations.remove(oldest.name.lowercase())
        }
        return created
    }

    private fun onMessage(message: Component) {
        if (!OverwatchGate.onWynncraft) return
        val raw = message.string
        val pm = parse(raw)
        if (pm != null) {
            val nickname = looksLikeNickname(message, pm)
            PlayerLookup.verify(pm.partner, isKnownPlayer(pm.partner)) { real -> if (real || nickname) record(pm) }
            return
        }
        if (System.currentTimeMillis() < guildCaptureUntil) {
            val tokens = TOKEN_PATTERN.findAll(raw).map { it.value }.toSet()
            guildTokens = guildTokens + tokens
        }
    }

    private fun isKnownPlayer(name: String): Boolean {
        val lower = name.lowercase()
        return conversations.containsKey(lower) ||
            onlineNames().any { it.lowercase() == lower } ||
            PartyFriendModel.friends.any { it.lowercase() == lower } ||
            PartyFriendModel.partyMembers.any { it.lowercase() == lower }
    }

    private fun looksLikeNickname(message: Component, pm: Pm): Boolean {
        val target = pm.partner.lowercase()
        if (pm.outgoing && System.currentTimeMillis() - (sentTo[target] ?: 0L) < SENT_WINDOW_MS) return true
        return message.toFlatList().any { part ->
            val style = part.style
            (style.clickEvent != null || style.hoverEvent != null) && part.string.lowercase().contains(target)
        }
    }

    private fun record(pm: Pm) {
        val now = System.currentTimeMillis()
        val convo = conversation(pm.partner)
        convo.lastAt = now
        convo.lines.addLast(Line(pm.body, pm.outgoing, now))
        while (convo.lines.size > MAX_LINES) convo.lines.removeFirst()
        if (!pm.outgoing) {
            convo.lastIncomingAt = now
            lastIncomingPartner = pm.partner
            if (!isViewing(pm.partner)) convo.unread++
        }
    }

    private fun allow(message: Component): Boolean {
        if (System.currentTimeMillis() >= guildCaptureUntil) return true
        return parse(message.string) != null
    }

    private fun onCommand(command: String) {
        if (!OverwatchGate.onWynncraft) return
        val parts = command.trim().split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val head = parts[0].lowercase()
        val target = when {
            head in SEND_COMMANDS && parts.size >= 2 -> parts[1]
            head in REPLY_COMMANDS -> lastIncomingPartner
            else -> null
        } ?: return
        val convo = conversation(target)
        convo.lastAt = System.currentTimeMillis()
        sentTo[target.lowercase()] = convo.lastAt
    }

    private fun isViewing(partner: String): Boolean =
        Minecraft.getInstance().gui.screen() is net.minecraft.client.gui.screens.ChatScreen &&
            WynnChatChannels.directTarget?.equals(partner, ignoreCase = true) == true

    fun noteOutgoing(name: String) {
        val now = System.currentTimeMillis()
        conversation(name).lastAt = now
        sentTo[name.lowercase()] = now
    }

    fun markRead(name: String) {
        conversations[name.lowercase()]?.unread = 0
    }

    fun recent(): List<Conversation> = conversations.values.sortedByDescending { it.lastAt }

    fun unreadTotal(): Int = conversations.values.sumOf { it.unread }

    fun smartTarget(): String? {
        val now = System.currentTimeMillis()
        return conversations.values
            .filter { now - it.lastAt < SMART_WINDOW_MS }
            .maxByOrNull { it.lastAt }
            ?.name
    }

    fun smartWindowOpen(name: String): Boolean {
        val convo = conversations[name.lowercase()] ?: return false
        return System.currentTimeMillis() - convo.lastAt < SMART_WINDOW_MS
    }

    fun requestGuildMembers() {
        if (guildRequested || !OverwatchGate.onWynncraft) return
        val connection = Minecraft.getInstance().connection ?: return
        guildRequested = true
        guildCaptureUntil = System.currentTimeMillis() + GUILD_CAPTURE_MS
        connection.sendCommand("guild list")
    }

    private fun onlineNames(): List<String> {
        val client = Minecraft.getInstance()
        val connection = client.connection ?: return emptyList()
        val me = client.player?.name?.string
        return connection.listedOnlinePlayers
            .map { it.profile.name() }
            .filter { NAME_PATTERN.matches(it) && !it.equals(me, ignoreCase = true) }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    fun suggestions(filter: String): List<Suggestion> {
        val query = filter.trim().lowercase()
        val online = onlineNames()
        val onlineLower = online.associateBy { it.lowercase() }
        val out = ArrayList<Suggestion>()
        val seen = HashSet<String>()

        fun matches(name: String): Boolean = query.isEmpty() || name.lowercase().startsWith(query) || name.lowercase().contains(query)

        fun add(group: String, names: Collection<String>) {
            var count = 0
            val ordered = names.sortedWith(compareBy({ !it.lowercase().startsWith(query) }, { it.lowercase() }))
            for (name in ordered) {
                if (!matches(name) || !seen.add(name.lowercase())) continue
                out.add(Suggestion(name, group))
                count++
                if (query.isEmpty() && count >= PER_GROUP) break
            }
        }

        val recentNames = recent().map { it.name }
        val ordered = recentNames.filter { matches(it) }
        for (name in ordered) {
            if (seen.add(name.lowercase())) out.add(Suggestion(name, "Recent"))
            if (query.isEmpty() && out.size >= PER_GROUP) break
        }
        add("Friends", PartyFriendModel.friends.filter { it.lowercase() in onlineLower }.map { onlineLower.getValue(it.lowercase()) })
        add("Party", PartyFriendModel.partyMembers.filter { it.lowercase() in onlineLower }.map { onlineLower.getValue(it.lowercase()) })
        add("Guild", guildTokens.filter { it.lowercase() in onlineLower }.map { onlineLower.getValue(it.lowercase()) })
        add("Online", online)
        return out
    }

    fun applyView(open: Boolean) {
        val client = Minecraft.getInstance()
        val chat = client.gui.hud.chat
        val target = WynnChatChannels.directTarget
        if (open && target != null && OverwatchConfig.current.chatConversationView) {
            chat.setVisibleMessageFilter { message ->
                val pm = parse(message.content().string)
                pm != null && pm.partner.equals(target, ignoreCase = true)
            }
        } else {
            chat.setVisibleMessageFilter { true }
        }
        chat.rescaleChat()
    }
}
