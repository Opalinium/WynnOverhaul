package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft

object ObjectiveClaims {
    @Volatile
    var dailyClaimable: Boolean = false

    val weeklyClaimable: Boolean
        get() = WynnOverhaulConfig.current.weeklyObjectivePending

    fun register() {
        ClientSendMessageEvents.COMMAND.register { command ->
            val parts = command.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            if (parts.size >= 2 && parts[0] == "guild" && parts[1] == "rewards") setWeekly(false)
        }
    }

    private var sidebarSawCollect = false
    private val collectLine = Regex("""collect your rewards""", RegexOption.IGNORE_CASE)

    fun onSidebar(text: String) {
        val collect = collectLine.containsMatchIn(text)
        if (collect) {
            sidebarSawCollect = true
            setWeekly(true)
        } else if (sidebarSawCollect) {
            sidebarSawCollect = false
            setWeekly(false)
        }
    }

    fun setWeekly(pending: Boolean) {
        val config = WynnOverhaulConfig.current
        if (config.weeklyObjectivePending == pending) return
        config.weeklyObjectivePending = pending
        config.save()
    }

    fun claimWeekly() {
        Minecraft.getInstance().connection?.sendCommand("guild rewards")
        setWeekly(false)
    }

    fun clear() {
        dailyClaimable = false
    }
}
