package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnBuffTracker {
    data class Buff(val name: String, val amount: Int, val expiresAtMillis: Long) {
        val remainingSeconds: Int
            get() = ((expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
    }

    @Volatile
    var active: List<Buff> = emptyList()
        private set

    private val buffs = LinkedHashMap<String, Buff>()

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onMessage(message) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onMessage(message) }
    }

    fun tick(client: Minecraft) {
        if (buffs.isEmpty()) return
        val now = System.currentTimeMillis()
        if (buffs.values.removeIf { it.expiresAtMillis <= now }) {
            active = buffs.values.toList()
        }
    }

    private fun onMessage(message: Component) {
        val text = TextClean.clean(message.string)
        val match = BUFF_PATTERN.find(text) ?: return
        val amount = match.groupValues[1].toIntOrNull() ?: return
        val name = match.groupValues[2].trim()
        val seconds = match.groupValues[3].toIntOrNull() ?: return
        if (name.isEmpty() || seconds <= 0) return
        buffs[name] = Buff(name, amount, System.currentTimeMillis() + seconds * 1000L)
        active = buffs.values.toList()
    }

    private val BUFF_PATTERN = Regex("""\[([+-]\d+)\s+(.+?)\s+for\s+(\d+)\s+seconds]""")
}
