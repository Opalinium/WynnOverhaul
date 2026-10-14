package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object Pings {
    private val SHARED = Regex("""My (compass|location) is at \[(-?\d+), (-?\d+), (-?\d+)]""")
    private val WYNNMOD = Regex("""wynnmod ping: (-?\d+(?:\.\d+)?), (-?\d+(?:\.\d+)?), (-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private const val RANGE = 256.0
    private const val COOLDOWN_MILLIS = 600L
    private const val RECEIVED_LIFETIME_MULTIPLIER = 1.5

    private lateinit var pingKey: KeyMapping
    private var lastPingMillis = 0L
    private var lastSentMillis = 0L
    private var lastSentKey = ""

    fun register() {
        pingKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.wynnoverhaul.ping", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, WynnOverhaulKeyCategory.CATEGORY),
        )
        ClientReceiveMessageEvents.GAME.register { message, _ -> onMessage(message) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onMessage(message) }
    }

    fun tick(client: Minecraft) {
        while (pingKey.consumeClick()) {
            if (client.gui.screen() == null) ping(client)
        }
    }

    private fun ping(client: Minecraft) {
        val config = WynnOverhaulConfig.current
        val player = client.player ?: return
        if (!config.pingEnabled || !config.pinsEnabled || !WynnOverhaulGate.inGame) return
        val now = System.currentTimeMillis()
        if (now - lastPingMillis < COOLDOWN_MILLIS) return
        val hit = player.pick(RANGE, 1f, false)
        val at = if (hit.type == HitResult.Type.BLOCK) {
            (hit as BlockHitResult).blockPos.above()
        } else {
            val end = player.eyePosition.add(player.getViewVector(1f).scale(RANGE))
            BlockPos.containing(end)
        }
        lastPingMillis = now
        val key = "${at.x}, ${at.y}, ${at.z}"
        PinBoard.add(
            PinBoard.Pin(
                "P:$key", "Ping", at.x, at.y, at.z, PinBoard.Kind.PING, PinBoard.PING_COLOR,
                expiresMillis = now + config.pingLifetimeSeconds * 1000L,
            ),
        )
        PinBoard.setPrimary("P:$key")
        sound(client)
        if (config.pingShareParty && WynnScoreboardTracker.party != null) {
            lastSentMillis = now
            lastSentKey = key
            client.connection?.sendCommand("p My compass is at [$key]")
        }
    }

    private fun onMessage(message: Component) {
        val config = WynnOverhaulConfig.current
        if (!config.pingEnabled || !config.pingReceive || !config.pinsEnabled || !WynnOverhaulGate.inGame) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val text = TextClean.clean(message.string)
        val shared = SHARED.find(text)
        val wynnmod = if (shared == null) WYNNMOD.find(text) else null
        val match = shared ?: wynnmod ?: return
        val offset = if (shared != null) 2 else 1
        val kind = if (shared != null && shared.groupValues[1] == "location") "location" else "ping"
        val x = match.groupValues[offset].toDoubleOrNull()?.let { Math.floor(it).toInt() } ?: return
        val y = match.groupValues[offset + 1].toDoubleOrNull()?.let { Math.floor(it).toInt() } ?: return
        val z = match.groupValues[offset + 2].toDoubleOrNull()?.let { Math.floor(it).toInt() } ?: return
        val key = "$x, $y, $z"
        val now = System.currentTimeMillis()
        val sender = senderOf(text, match.range.first)
        if (sender.equals(player.gameProfile.name, ignoreCase = true) || (key == lastSentKey && now - lastSentMillis < ECHO_WINDOW_MILLIS)) return
        val label = if (sender.isEmpty()) "Shared $kind" else "$sender's $kind"
        PinBoard.add(
            PinBoard.Pin(
                "R:$key", label, x, y, z, PinBoard.Kind.RECEIVED, PinBoard.RECEIVED_COLOR,
                expiresMillis = now + (config.pingLifetimeSeconds * 1000L * RECEIVED_LIFETIME_MULTIPLIER).toLong(),
            ),
        )
        sound(client)
    }

    private fun senderOf(text: String, shareStart: Int): String {
        val head = text.substring(0, shareStart).trim().trimEnd(':', ' ')
        val name = head.substringAfterLast(' ').trim('[', ']', ':', '<', '>')
        return if (name.length in 3..16 && name.all { it.isLetterOrDigit() || it == '_' }) name else ""
    }

    private fun sound(client: Minecraft) {
        if (!WynnOverhaulConfig.current.pingSound) return
        client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.6f))
    }

    private const val ECHO_WINDOW_MILLIS = 4000L
}
