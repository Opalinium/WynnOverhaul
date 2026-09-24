package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object NotificationSounds {

    val PRESETS = listOf(
        "Pling" to "minecraft:block.note_block.pling",
        "Bell" to "minecraft:block.note_block.bell",
        "Chime" to "minecraft:block.amethyst_block.chime",
        "Harp" to "minecraft:block.note_block.harp",
        "Bit" to "minecraft:block.note_block.bit",
        "Didgeridoo" to "minecraft:block.note_block.didgeridoo",
        "XP orb" to "minecraft:entity.experience_orb.pickup",
        "Level up" to "minecraft:entity.player.levelup",
        "Arrow hit" to "minecraft:entity.arrow.hit_player",
        "Dispenser" to "minecraft:block.dispenser.dispense",
    )

    fun label(id: String): String =
        if (id.isEmpty()) "Off" else PRESETS.firstOrNull { it.second == id }?.first ?: id

    fun next(id: String, allowOff: Boolean): String {
        val options = if (allowOff) listOf("") + PRESETS.map { it.second } else PRESETS.map { it.second }
        val index = options.indexOf(id).coerceAtLeast(0)
        return options[(index + 1).mod(options.size)]
    }

    fun resolve(id: String): SoundEvent {
        val parsed = Identifier.tryParse(id)
        val fromRegistry = parsed?.let { BuiltInRegistries.SOUND_EVENT.getOptional(it).orElse(null) }
        return fromRegistry ?: SoundEvents.NOTE_BLOCK_PLING.value()
    }

    fun play(id: String, volume: Double, pitch: Float = 1f) {
        if (id.isEmpty()) return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(resolve(id), pitch, volume.toFloat().coerceIn(0f, 1f)))
    }
}
