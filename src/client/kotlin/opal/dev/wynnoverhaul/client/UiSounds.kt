package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object UiSounds {
    private const val HOVER_GAP_NANOS = 45_000_000L
    private var lastHover = 0L

    private fun enabled(): Boolean = WynnOverhaulConfig.current.uiSoundsEnabled

    private fun play(event: SoundEvent, pitch: Float, volume: Float) {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(event, pitch, volume * WynnOverhaulConfig.current.uiSoundVolume.toFloat()))
    }

    fun click(): Boolean {
        if (!enabled()) return false
        play(SoundEvents.STONE_BUTTON_CLICK_ON, 1.4f, 0.55f)
        play(SoundEvents.CHAIN_PLACE, 1.7f, 0.2f)
        return true
    }

    fun hover() {
        if (!enabled()) return
        val now = System.nanoTime()
        if (now - lastHover < HOVER_GAP_NANOS) return
        lastHover = now
        play(SoundEvents.BOOK_PAGE_TURN, 2f, 0.1f)
    }

    fun open() {
        if (enabled()) play(SoundEvents.BOOK_PAGE_TURN, 0.95f, 0.4f)
    }

    fun close() {
        if (enabled()) play(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.3f)
    }

    fun reveal() {
        if (enabled()) play(SoundEvents.LODESTONE_COMPASS_LOCK, 1.1f, 0.5f)
    }
}
