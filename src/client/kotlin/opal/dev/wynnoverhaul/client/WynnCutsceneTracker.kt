package opal.dev.wynnoverhaul.client

import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.Display

object WynnCutsceneTracker : ClientboundBossEventPacket.Handler {
    private val SKIP_PROMPT = Regex("""Swap Hands.{0,6}to skip""", RegexOption.IGNORE_CASE)

    @Volatile
    private var skipBarId: UUID? = null

    @Volatile
    private var onCamera = false

    val active: Boolean get() = skipBarId != null || onCamera

    val hidesHud: Boolean
        get() = active && WynnOverhaulGate.inGame && WynnOverhaulConfig.current.hideHudInCutscenes

    fun clear() {
        skipBarId = null
        onCamera = false
    }

    fun tick(client: Minecraft) {
        onCamera = client.cameraEntity is Display || client.player?.vehicle is Display
    }

    override fun add(
        id: UUID,
        name: Component,
        progress: Float,
        color: BossEvent.BossBarColor,
        overlay: BossEvent.BossBarOverlay,
        darkenScreen: Boolean,
        playBossMusic: Boolean,
        createWorldFog: Boolean,
    ) {
        if (isSkipPrompt(name)) skipBarId = id
    }

    override fun remove(id: UUID) {
        if (id == skipBarId) skipBarId = null
    }

    override fun updateProgress(id: UUID, progress: Float) {
    }

    override fun updateName(id: UUID, name: Component) {
        if (isSkipPrompt(name)) {
            skipBarId = id
        } else if (id == skipBarId) {
            skipBarId = null
        }
    }

    private fun isSkipPrompt(name: Component): Boolean = SKIP_PROMPT.containsMatchIn(TextClean.clean(name.string))
}
