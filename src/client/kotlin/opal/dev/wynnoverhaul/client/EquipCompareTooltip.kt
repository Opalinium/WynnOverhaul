package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import org.joml.Vector2i

object EquipCompareTooltip {
    private class Target(val label: String, val stack: ItemStack)

    private class Laid(val components: List<ClientTooltipComponent>, val width: Int, val height: Int)

    fun draw(graphics: GuiGraphicsExtractor, font: Font, stack: ItemStack, mouseX: Int, mouseY: Int) {
        if (!WynnOverhaulConfig.current.equipComparisonEnabled || !WynnOverhaulGate.inGame) return
        val player = Minecraft.getInstance().player ?: return
        val targets = resolveTargets(player, stack)
        if (targets.isEmpty() || targets.any { it.stack === stack }) return

        val mc = Minecraft.getInstance()
        val screenW = graphics.guiWidth()
        val screenH = graphics.guiHeight()

        val primary = lay(font, Screen.getTooltipFromItem(mc, stack))
        val origin = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenW, screenH, mouseX, mouseY, primary.width, primary.height)
        val primaryRight = origin.x() > mouseX
        var nearLeft = if (primaryRight) mouseX - CURSOR_GAP else origin.x() - GAP
        var nearRight = origin.x() + primary.width + GAP

        for (target in targets) {
            val lines = ArrayList<Component>()
            lines.add(Component.literal("Equipped: ${target.label}").withColor(HEADER_COLOR))
            lines.addAll(Screen.getTooltipFromItem(mc, target.stack))
            val laid = lay(font, lines)

            val fitsRight = nearRight + laid.width + MARGIN <= screenW
            val fitsLeft = nearLeft - laid.width >= MARGIN
            val useRight = if (primaryRight) fitsRight else !fitsLeft && fitsRight
            val x = when {
                useRight -> nearRight.also { nearRight += laid.width + GAP }
                fitsLeft -> (nearLeft - laid.width).also { nearLeft = it - GAP }
                else -> continue
            }
            val y = origin.y().coerceAtMost(screenH - laid.height - MARGIN).coerceAtLeast(MARGIN)
            graphics.tooltip(font, laid.components, x, y, Fixed(x, y), null)
        }
    }

    private class Fixed(private val x: Int, private val y: Int) : ClientTooltipPositioner {
        override fun positionTooltip(screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int, width: Int, height: Int) =
            Vector2i(x, y)
    }

    private fun lay(font: Font, lines: List<Component>): Laid {
        val components = lines.map { ClientTooltipComponent.create(it.visualOrderText) }
        var width = 0
        var height = if (components.size == 1) -2 else 0
        for (c in components) {
            width = maxOf(width, c.getWidth(font))
            height += c.getHeight(font)
        }
        return Laid(components, width, height)
    }

    private fun resolveTargets(player: Player, stack: ItemStack): List<Target> {
        val code = WynnGearKind.frameCode(stack)
        if (code != null) {
            val matched = equippedCandidates(player).filter { WynnGearKind.frameCode(it.stack) == code }
            if (matched.isNotEmpty()) return matched
        }
        val kind = WynnGearKind.of(stack) ?: return emptyList()
        return targetsFor(player, kind)
    }

    private fun equippedCandidates(player: Player): List<Target> {
        val out = ArrayList<Target>(8)
        out.add(Target("Helmet", player.getItemBySlot(EquipmentSlot.HEAD)))
        out.add(Target("Chestplate", player.getItemBySlot(EquipmentSlot.CHEST)))
        out.add(Target("Leggings", player.getItemBySlot(EquipmentSlot.LEGS)))
        out.add(Target("Boots", player.getItemBySlot(EquipmentSlot.FEET)))
        out.add(Target("Ring 1", player.inventory.getItem(RING_1_SLOT)))
        out.add(Target("Ring 2", player.inventory.getItem(RING_2_SLOT)))
        out.add(Target(accessoryLabel(player.inventory.getItem(BRACELET_SLOT)), player.inventory.getItem(BRACELET_SLOT)))
        out.add(Target(accessoryLabel(player.inventory.getItem(NECKLACE_SLOT)), player.inventory.getItem(NECKLACE_SLOT)))
        weaponTarget(player)?.let { out.add(it) }
        return out.filter { !it.stack.isEmpty }
    }

    private fun accessoryLabel(stack: ItemStack): String = when (WynnGearKind.of(stack)) {
        GearSlotKind.BRACELET -> "Bracelet"
        GearSlotKind.NECKLACE -> "Necklace"
        else -> "Accessory"
    }

    private fun targetsFor(player: Player, kind: GearSlotKind): List<Target> {
        val candidates = when (kind) {
            GearSlotKind.HELMET -> listOf(Target("Helmet", player.getItemBySlot(EquipmentSlot.HEAD)))
            GearSlotKind.CHESTPLATE -> listOf(Target("Chestplate", player.getItemBySlot(EquipmentSlot.CHEST)))
            GearSlotKind.LEGGINGS -> listOf(Target("Leggings", player.getItemBySlot(EquipmentSlot.LEGS)))
            GearSlotKind.BOOTS -> listOf(Target("Boots", player.getItemBySlot(EquipmentSlot.FEET)))
            GearSlotKind.RING -> listOf(
                Target("Ring 1", player.inventory.getItem(RING_1_SLOT)),
                Target("Ring 2", player.inventory.getItem(RING_2_SLOT)),
            )
            GearSlotKind.BRACELET -> listOf(Target("Bracelet", player.inventory.getItem(BRACELET_SLOT)))
            GearSlotKind.NECKLACE -> listOf(Target("Necklace", player.inventory.getItem(NECKLACE_SLOT)))
            else -> weaponTarget(player)?.let { listOf(it) } ?: emptyList()
        }
        return candidates.filter { !it.stack.isEmpty }
    }

    private fun weaponTarget(player: Player): Target? =
        ActiveWeapon.stack(player)?.let { Target("Active weapon", it) }

    private const val RING_1_SLOT = 9
    private const val RING_2_SLOT = 10
    private const val BRACELET_SLOT = 11
    private const val NECKLACE_SLOT = 12

    private const val GAP = 10
    private const val CURSOR_GAP = 4
    private const val MARGIN = 4
    private const val HEADER_COLOR = 0xAAAAAA
}
