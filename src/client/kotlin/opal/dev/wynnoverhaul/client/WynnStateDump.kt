package opal.dev.wynnoverhaul.client

import com.mojang.brigadier.arguments.DoubleArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.ChatFormatting
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
import java.util.Optional

object WynnStateDump {
    @Volatile
    private var lastActionBar: Component? = null

    @Volatile
    private var lastContainer: String? = null

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (overlay) lastActionBar = message }
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (screen is AbstractContainerScreen<*> && screen !is InventoryScreen) {
                ScreenEvents.remove(screen).register { lastContainer = describeContainer(it as AbstractContainerScreen<*>) }
            }
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("owdump")
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

    private fun run(radius: Double) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val text = build(client, radius)
        client.keyboardHandler.setClipboard(text)
        player.sendSystemMessage(
            Component.literal("Copied game state (${text.length} chars) to the clipboard.").withStyle(ChatFormatting.GRAY),
        )
    }

    private fun describeContainer(screen: AbstractContainerScreen<*>): String = buildString {
        val slots = screen.menu.slots
        val containerSize = (slots.size - PLAYER_SLOTS).coerceAtLeast(0)
        appendLine("title=${describe(screen.title)} screen=${screen.javaClass.simpleName} menuSlots=${slots.size} containerSlots=$containerSize")
        for (i in 0 until containerSize) {
            val stack = slots[i].item
            if (stack.isEmpty) continue
            val models = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.floats().orEmpty()
            val name = stack.get(DataComponents.CUSTOM_NAME)?.let(::describe) ?: stack.hoverName.string
            appendLine("slot=$i ${BuiltInRegistries.ITEM.getKey(stack.item)} x${stack.count} cmd=$models name=$name")
            if (models.isNotEmpty() && stack.count == 1 && BuiltInRegistries.ITEM.getKey(stack.item).path == "potion") {
                stack.get(DataComponents.LORE)?.lines()?.forEach { appendLine("    lore: ${describe(it)}") }
            }
        }
    }

    private fun build(client: Minecraft, radius: Double): String = buildString {
        val player = client.player
        appendLine("== Last container screen")
        appendLine(lastContainer ?: "(none)")
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
        }
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
    private const val PLAYER_SLOTS = 36
}
