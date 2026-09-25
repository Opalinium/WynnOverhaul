package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

object PowderSpecialsTooltip {
    private class Special(val name: String, val text: String)

    private class Element(val color: ChatFormatting, val weapon: Special, val armour: Special)

    private val elements = mapOf(
        "Earth" to Element(
            ChatFormatting.GREEN,
            Special("Quake", "240-480% Main Attack damage and a brief stun in a 4.5-7.5 block radius"),
            Special("Rage", "+0.4-1% Earth damage per % missing health below 75% (max +300%)"),
        ),
        "Thunder" to Element(
            ChatFormatting.YELLOW,
            Special("Chain Lightning", "200-350% Main Attack damage, chaining up to 5-11 times within 14 blocks"),
            Special("Kill Streak", "+6-15% Thunder damage for 10s per kill (max +200%)"),
        ),
        "Water" to Element(
            ChatFormatting.AQUA,
            Special("Curse", "mobs within 8 blocks take 10-25% more damage for 4s"),
            Special("Concentration", "+0.05-0.2% Water damage per mana spent (max +120%)"),
        ),
        "Fire" to Element(
            ChatFormatting.RED,
            Special("Courage", "110-200% Main Attack damage within 5 blocks, +10-25% damage to you and allies for 4s"),
            Special("Endurance", "+2-8% Fire damage for 8s when hit (max +120%)"),
        ),
        "Air" to Element(
            ChatFormatting.WHITE,
            Special("Wind Prison", "holds nearby mobs up to 5s; next hit deals 100-250% more damage"),
            Special("Dodge", "+2-8% Air damage per second near a hostile mob within 5 blocks (max +120%)"),
        ),
    )

    private val powderName = Regex("""^(Earth|Thunder|Water|Fire|Air) Powder [IV]{1,3}$""")

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            if (!OverwatchConfig.current.powderSpecialsTooltipEnabled || !OverwatchGate.inGame || stack.isEmpty) return@register
            val name = TextClean.clean(stack.hoverName.string)
            val element = powderName.find(name)?.groupValues?.get(1)?.let { elements[it] } ?: return@register
            lines.add(Component.literal(""))
            lines.add(Component.literal("Special effects (2+ powders, Tier 4+):").withStyle(ChatFormatting.GOLD))
            lines.add(line("Weapon", element.weapon, element.color))
            lines.add(line("Armour", element.armour, element.color))
        }
    }

    private fun line(kind: String, special: Special, color: ChatFormatting): Component =
        Component.literal("$kind: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(special.name).withStyle(color))
            .append(Component.literal(" - ${special.text}").withStyle(ChatFormatting.GRAY))
}
