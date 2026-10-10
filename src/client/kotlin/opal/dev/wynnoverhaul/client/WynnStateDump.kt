package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.AABB
import opal.dev.wynnoverhaul.mixin.client.BossHealthOverlayAccessor
import opal.dev.wynnoverhaul.mixin.client.ItemDisplayAccessor
import net.minecraft.world.inventory.AbstractContainerMenu
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import net.minecraft.network.chat.FontDescription

object WynnStateDump {
    @Volatile
    private var lastActionBar: Component? = null

    @Volatile
    private var lastContainer: String? = null

    @Volatile
    private var lastCaughtDialogue: String? = null

    @Volatile
    private var lastMissedDialogue: String? = null

    private val dialogueLog = ArrayDeque<String>()
    private var lastMissLogMillis = 0L
    private var openTitle: Component? = null
    private var openScreenName = ""
    private var openMenu: AbstractContainerMenu? = null
    private lateinit var dumpKey: KeyMapping

    fun register() {
        dumpKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.wynnoverhaul.dump_container", InputConstants.Type.KEYSYM, DEFAULT_DUMP_KEY, WynnOverhaulKeyCategory.CATEGORY),
        )
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (overlay) {
                lastActionBar = message
                noteDialogue(message)
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.gui.screen() == null) {
                var pressed = false
                while (dumpKey.consumeClick()) pressed = true
                if (pressed) run(DEFAULT_RADIUS)
            }
        }
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*> && screen !is InventoryScreen) {
                openTitle = screen.title
                openScreenName = screen.javaClass.simpleName
                openMenu = screen.menu
                ScreenEvents.remove(screen).register { lastContainer = describeContainer(screen.title, screen.javaClass.simpleName, screen.menu, DumpMode.LEGACY) }
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                if (dumpKey.matches(event) && screen !is InventoryScreen) {
                    dumpOpenContainer()
                    false
                } else {
                    true
                }
            }
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("owdump")
                    .then(
                        ClientCommands.literal("dialogue").executes {
                            runDialogue()
                            1
                        },
                    )
                    .then(
                        ClientCommands.argument("radius", DoubleArgumentType.doubleArg(1.0, 64.0)).executes { ctx ->
                            run(DoubleArgumentType.getDouble(ctx, "radius"))
                            1
                        },
                    )
                    .executes {
                        run(DEFAULT_RADIUS)
                        1
                    },
            )
        }
    }

    private fun noteDialogue(message: Component) {
        val caught = WynnDialogueTracker.isDialogue(message)
        val prompt = DIALOGUE_PROMPT.containsMatchIn(message.string)
        if (!caught && !prompt) return
        val line = "caught=$caught ${describe(message)}"
        if (caught) {
            lastCaughtDialogue = line
            val compact = describeDialogue(message)
            synchronized(dialogueLog) {
                if (dialogueLog.lastOrNull() != compact) {
                    dialogueLog.addLast(compact)
                    while (dialogueLog.size > DIALOGUE_LOG_SIZE) dialogueLog.removeFirst()
                }
            }
        } else {
            lastMissedDialogue = line
            val now = System.currentTimeMillis()
            if (now - lastMissLogMillis > MISS_LOG_GAP_MS) {
                lastMissLogMillis = now
                WynnOverhaul.LOGGER.info("[DialogueMissed] {}", line)
            }
        }
    }

    private fun run(radius: Double) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val text = build(client, radius)
        client.keyboardHandler.setClipboard(text)
        player.sendSystemMessage(
            Component.literal("Copied game state (${text.length} chars) to the clipboard.").withStyle(ChatFormatting.GRAY),
        )
    }

    private fun runDialogue() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val text = buildString {
            appendLine("== Tracker state")
            appendLine(WynnDialogueTracker.debugState())
            appendLine("== Dialogue bars, oldest first")
            synchronized(dialogueLog) { dialogueLog.forEach { appendLine(it) } }
        }
        client.keyboardHandler.setClipboard(text)
        player.sendSystemMessage(
            Component.literal("Copied ${dialogueLog.size} dialogue bars (${text.length} chars) to the clipboard.").withStyle(ChatFormatting.GRAY),
        )
    }

    private enum class DumpMode { LEGACY, FULL, COMPACT }

    private fun dumpOpenContainer() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val menu = openMenu
        val title = openTitle
        if (menu == null || title == null || player.containerMenu !== menu) {
            player.sendSystemMessage(Component.literal("No container is open.").withStyle(ChatFormatting.GRAY))
            return
        }
        val full = describeContainer(title, openScreenName, menu, DumpMode.FULL)
        val compact = describeContainer(title, openScreenName, menu, DumpMode.COMPACT)
        val directory = client.gameDirectory.toPath().resolve(DUMP_DIRECTORY)
        val path = runCatching {
            Files.createDirectories(directory)
            directory.resolve("container-${LocalDateTime.now().format(DUMP_TIME)}.txt").also { Files.writeString(it, full) }
        }.getOrNull()
        client.keyboardHandler.setClipboard(compact)
        val where = if (path != null) " Full data saved to $path" else ""
        player.sendSystemMessage(Component.literal("Copied container data (${compact.length} chars) to the clipboard.$where").withStyle(ChatFormatting.GRAY))
    }

    private fun describeContainer(title: Component, screenName: String, menu: AbstractContainerMenu, mode: DumpMode): String = buildString {
        val slots = menu.slots
        val containerSize = (slots.size - PLAYER_SLOTS).coerceAtLeast(0)
        appendLine("title=${describe(title)} screen=$screenName menuSlots=${slots.size} containerSlots=$containerSize")
        for (i in 0 until containerSize) {
            val stack = slots[i].item
            if (stack.isEmpty) continue
            val models = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.floats().orEmpty()
            val key = BuiltInRegistries.ITEM.getKey(stack.item)
            val lore = stack.get(DataComponents.LORE)?.lines().orEmpty()
            if (mode == DumpMode.COMPACT) {
                val name = TextClean.clean(stack.hoverName.string)
                val text = lore.map { TextClean.clean(it.string) }.filter { it.isNotBlank() }.joinToString(" | ")
                appendLine("slot=$i $key x${stack.count} cmd=$models name=$name" + if (text.isNotEmpty()) " lore=$text" else "")
                continue
            }
            val name = stack.get(DataComponents.CUSTOM_NAME)?.let(::describe) ?: stack.hoverName.string
            appendLine("slot=$i $key x${stack.count} cmd=$models name=$name")
            val withLore = mode == DumpMode.FULL || (models.isNotEmpty() && stack.count == 1 && key.path == "potion")
            if (withLore) lore.forEach { appendLine("    lore: ${describe(it)}") }
        }
    }

    private fun build(client: Minecraft, radius: Double): String = buildString {
        val player = client.player
        appendLine("== Last container screen")
        appendLine(lastContainer ?: "(none)")
        appendLine("== Last caught dialogue")
        appendLine(lastCaughtDialogue ?: "(none)")
        appendLine("== Last missed dialogue")
        appendLine(lastMissedDialogue ?: "(none)")
        appendLine("== Tab footer")
        appendLine(WynnStatusEffectTracker.lastFooter?.let(::describe) ?: "(none)")
        appendLine("== Action bar")
        appendLine(lastActionBar?.let(::describe) ?: "(none)")
        appendLine("== Boss bars")
        val overlay = client.gui.hud.bossOverlay as BossHealthOverlayAccessor
        for (event in overlay.`wynnoverhaul$getEvents`().values) {
            appendLine("${describe(event.name)} progress=${event.progress}")
        }
        appendLine("== Potion effects")
        if (player != null) {
            for (effect in player.activeEffects) {
                appendLine("${BuiltInRegistries.MOB_EFFECT.getKey(effect.effect.value())} amp=${effect.amplifier} ticks=${effect.duration}")
            }
        }
        appendLine("== Entity labels within ${radius.toInt()} blocks")
        val level = client.level
        if (player != null && level != null) {
            val box = AABB(
                player.x - radius, player.y - radius, player.z - radius,
                player.x + radius, player.y + radius, player.z + radius,
            )
            for (entity in level.getEntities(player, box) { true }) {
                val label = when (entity) {
                    is Display.TextDisplay -> entity.text
                    else -> entity.customName
                } ?: continue
                val distance = "%.1f".format(entity.distanceTo(player))
                appendLine("${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)} id=${entity.id} dist=$distance ${describe(label)}")
            }
            appendLine("== Item displays within 12 blocks")
            val itemBox = player.boundingBox.inflate(12.0)
            for (entity in level.getEntities(player, itemBox) { it is Display.ItemDisplay || it.passengers.any { p -> p is Display.ItemDisplay } }) {
                val stacks = (listOf(entity) + entity.passengers).filterIsInstance<Display.ItemDisplay>().map {
                    val stack = (it as ItemDisplayAccessor).`wynnoverhaul$getItemStack`()
                    "${BuiltInRegistries.ITEM.getKey(stack.item)} cmd=${stack.get(DataComponents.CUSTOM_MODEL_DATA)?.floats()}"
                }
                val distance = "%.1f".format(entity.distanceTo(player))
                appendLine("${BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)} id=${entity.id} dist=$distance passengers=${entity.passengers.size} age=${entity.tickCount} items=$stacks")
            }
        }
        appendLine("== Class buffs")
        appendLine("classKey=${WynnClassTracker.classKey} lastCast=${WynnSpellTracker.lastCast?.let { "${it.name} ${System.currentTimeMillis() - it.atMillis}ms ago" }}")
        for (buff in WynnClassBuffTracker.buffs) appendLine(buff.toString())
    }

    private fun describe(component: Component): String = buildString {
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                if (text.isNotEmpty()) {
                    append('[').append(styleOf(style)).append(']')
                    for (ch in text) {
                        if (ch.code in 0x20..0x7E) append(ch) else if (ch == '\n') append("\\n") else append("\\u%04X".format(ch.code))
                    }
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
    }

    private fun describeDialogue(component: Component): String = buildString {
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                val path = (style.font as? FontDescription.Resource)?.id?.path
                if (path != null && path.startsWith("hud/dialogue/") && text.isNotEmpty() && !path.startsWith("hud/dialogue/effect/") && !path.contains("/box") && !path.contains("/footer") && !path.contains("nameplate")) {
                    if (isNotEmpty()) append(" | ")
                    append(path.removePrefix("hud/dialogue/"))
                    style.color?.let { append(" #%06X".format(it.value and 0xFFFFFF)) }
                    append(": ")
                    for (ch in text) {
                        if (ch.code in 0x20..0x7E) append(ch) else append("\\u%04X".format(ch.code))
                    }
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
    }

    private fun styleOf(style: Style): String {
        val parts = ArrayList<String>()
        style.color?.let { parts.add("#%06X".format(it.value and 0xFFFFFF)) }
        if (style.isBold) parts.add("b")
        if (style.isItalic) parts.add("i")
        if (style.isUnderlined) parts.add("u")
        if (style.isStrikethrough) parts.add("s")
        style.font?.let { parts.add("font=$it") }
        return parts.joinToString(",")
    }

    private const val DEFAULT_RADIUS = 16.0
    private const val MISS_LOG_GAP_MS = 1000L
    private const val DIALOGUE_LOG_SIZE = 40
    private val DIALOGUE_PROMPT = Regex("(?i)\\bto (choose option|confirm|continue)\\b")
    private const val PLAYER_SLOTS = 36
    private const val DEFAULT_DUMP_KEY = 297
    private const val DUMP_DIRECTORY = "wynnoverhaul-dumps"
    private val DUMP_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
}
