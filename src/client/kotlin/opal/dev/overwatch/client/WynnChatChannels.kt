package opal.dev.overwatch.client

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

object WynnChatChannels {
    enum class Channel(val id: String, val label: String, val color: ChatFormatting, val command: String?) {
        ALL("all", "ALL", ChatFormatting.WHITE, null),
        PARTY("party", "PARTY", ChatFormatting.YELLOW, "p"),
        GUILD("guild", "GUILD", ChatFormatting.AQUA, "g"),
    }

    @Volatile
    var current: Channel = Channel.ALL
        private set

    @Volatile
    var directTarget: String? = null
        private set

    @Volatile
    var directAuto: Boolean = false
        private set

    fun register() {
        current = Channel.entries.firstOrNull { it.id == OverwatchConfig.current.chatChannel } ?: Channel.ALL
        ClientSendMessageEvents.ALLOW_CHAT.register { message -> route(message) }
        ClientReceiveMessageEvents.GAME.register { message, _ -> matchIncoming(message) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("wct")
                    .then(
                        ClientCommands.argument("action", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(Channel.entries.map { it.id }.stream(), builder)
                            }
                            .executes { ctx ->
                                val action = StringArgumentType.getString(ctx, "action").lowercase()
                                val channel = Channel.entries.firstOrNull { it.id == action }
                                if (channel == null) {
                                    printChat("Unknown action! Usage: /wct <all|party|guild>", ChatFormatting.RED)
                                    0
                                } else {
                                    select(channel)
                                    1
                                }
                            },
                    )
                    .executes { _ ->
                        printChat("Chat channel: ${current.label}. Usage: /wct <all|party|guild>", ChatFormatting.GRAY)
                        1
                    },
            )
            dispatcher.register(
                ClientCommands.literal("a").then(
                    ClientCommands.argument("msg", StringArgumentType.greedyString()).executes { ctx ->
                        overrideNext(Channel.ALL)
                        Minecraft.getInstance().connection?.sendChat(StringArgumentType.getString(ctx, "msg"))
                        1
                    },
                ),
            )
        }
    }

    fun selectDirect(name: String, auto: Boolean = false) {
        directTarget = name
        directAuto = auto
        WynnDirectMessages.markRead(name)
        WynnDirectMessages.applyView(Minecraft.getInstance().gui.screen() is net.minecraft.client.gui.screens.ChatScreen)
    }

    fun clearDirect() {
        if (directTarget == null) return
        directTarget = null
        directAuto = false
        WynnDirectMessages.applyView(Minecraft.getInstance().gui.screen() is net.minecraft.client.gui.screens.ChatScreen)
    }

    fun applySmartReply() {
        val config = OverwatchConfig.current
        val target = directTarget
        if (target != null) {
            if (directAuto && !WynnDirectMessages.smartWindowOpen(target)) clearDirect()
            return
        }
        if (!config.chatSmartReply || current != Channel.ALL || !OverwatchGate.onWynncraft) return
        val smart = WynnDirectMessages.smartTarget() ?: return
        selectDirect(smart, auto = true)
    }

    fun select(channel: Channel) {
        directTarget = null
        directAuto = false
        current = channel
        OverwatchConfig.current.chatChannel = channel.id
        OverwatchConfig.current.save()
        printChat(
            Component.literal("You are now in the ").append(Component.literal(channel.label).withStyle(channel.color)),
        )
    }

    fun overrideNext(channel: Channel?) {
        override = channel
    }

    private var override: Channel? = null
    private var lastOverride: Channel? = null
    private var lastOverrideTime = 0L

    private fun route(message: String): Boolean {
        if (!OverwatchGate.isInGame()) {
            override = null
            lastOverride = null
            return true
        }
        val overridden = consumeOverride() ?: chainedOverride()
        val target = directTarget
        if (overridden == null && target != null) {
            val connection = Minecraft.getInstance().connection ?: return true
            connection.sendCommand("msg $target $message")
            WynnDirectMessages.noteOutgoing(target)
            return false
        }
        val channel = overridden ?: current
        if (channel == Channel.ALL) return true
        val connection = Minecraft.getInstance().connection ?: return true
        connection.sendCommand("${channel.command} $message")
        return false
    }

    private fun consumeOverride(): Channel? {
        val next = override ?: return null
        override = null
        lastOverride = next
        lastOverrideTime = System.currentTimeMillis()
        return next
    }

    private fun chainedOverride(): Channel? {
        val last = lastOverride ?: return null
        if (System.currentTimeMillis() >= lastOverrideTime + CHAIN_WINDOW_MS) return null
        return last
    }

    private fun matchIncoming(message: Component) {
        if (!OverwatchGate.onWynncraft) return
        val text = CLEAN_CODES.replace(message.string, "")
        for ((pattern, channelId) in OVERRIDE_PATTERNS) {
            if (!pattern.containsMatchIn(text)) continue
            override = channelId?.let { id -> Channel.entries.firstOrNull { it.id == id } }
            return
        }
    }

    private fun printChat(text: String, color: ChatFormatting) {
        printChat(Component.literal(text).withStyle(color))
    }

    private fun printChat(text: Component) {
        Minecraft.getInstance().player?.sendSystemMessage(text)
    }

    private val CLEAN_CODES = Regex("(?i)§[0-9A-FK-OR]")

    private val OVERRIDE_PATTERNS: List<Pair<Regex, String?>> = listOf(
        Regex("""(?s). Type the item name or type 'cancel' to.+cancel:.""") to "all",
        Regex("""(?s). Type the price in emeralds or formatted .+ \(e\.g '10eb', '10stx 5eb'\) or type .+ 'cancel' to cancel:.""") to "all",
        Regex("""Party Finder: Type in chat the description you want to use for your party \(max 140 characters or cancel\):""") to "all",
        Regex("""(?s). You moved and your chat input was canceled\.""") to null,
    )

    private const val CHAIN_WINDOW_MS = 100L
}
