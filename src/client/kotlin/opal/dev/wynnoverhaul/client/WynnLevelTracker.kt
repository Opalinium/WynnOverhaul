package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object WynnLevelTracker {
    @Volatile
    var level: Int? = null
        private set

    @Volatile
    var iconText: String? = null
        private set

    @Volatile
    var professionLevel: Int? = null
        private set

    @Volatile
    var professionName: String? = null
        private set

    @Volatile
    private var professionAtMillis = 0L

    fun professionFresh(): Boolean =
        professionLevel != null && System.currentTimeMillis() - professionAtMillis < PROFESSION_STALE_MILLIS

    fun gatheringNow(): Boolean =
        heldProfession(Minecraft.getInstance().player?.mainHandItem) != null || GatherSession.active()

    fun professionDisplay(): Pair<String, Int>? {
        if (!professionFresh() || !gatheringNow()) return null
        val name = professionName
        val profLevel = professionLevel
        if (name == null || profLevel == null) return null
        return name to profLevel
    }

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        level = null
        iconText = null
        professionLevel = null
        professionName = null
        professionAtMillis = 0L
    }

    private fun onActionBar(message: Component) {
        val raw = message.string
        val parsed = findLevel(raw) ?: return
        val tool = heldProfession(Minecraft.getInstance().player?.mainHandItem)
        val gathering = tool != null || GatherSession.active()
        val now = System.currentTimeMillis()
        when {
            parsed == level -> Unit
            gathering -> {
                professionLevel = parsed
                if (tool != null) professionName = tool
                professionAtMillis = now
            }
            parsed == professionLevel && professionFresh() -> Unit
            else -> level = parsed
        }
        WynnOverhaulGate.noteActionBar()
    }

    private fun heldProfession(stack: ItemStack?): String? = GatheringTool.kind(stack)?.let { TOOL_PROFESSIONS[it] }

    fun findLevel(raw: String): Int? {
        val match = LEVEL_PATTERN.findAll(raw).lastOrNull { m ->
            val v = m.value
            v.length >= 2 && v[0] == SPACER && v[v.length - 2] == SPACER
        } ?: return null
        val levelGroup = match.groups["level"]?.value ?: return null
        val digits = levelGroup.replace(SEPARATOR, "")
        val sb = StringBuilder()
        for (ch in digits) {
            if (ch in LEVEL_CHAR_START..LEVEL_CHAR_END) sb.append(ch - LEVEL_CHAR_START)
        }
        val parsed = sb.toString().toIntOrNull() ?: return null
        if (parsed !in 1..200) return null
        iconText = levelGroup
        return parsed
    }

    private const val SPACER = '\uDAFF'
    private const val SEPARATOR = "󏿾"
    private const val LEVEL_CHAR_START = ''
    private const val LEVEL_CHAR_END = ''
    private const val PROFESSION_STALE_MILLIS = 5000L
    private val TOOL_PROFESSIONS = mapOf(
        "PICKAXE" to "Mining",
        "AXE" to "Woodcutting",
        "SCYTHE" to "Farming",
        "ROD" to "Fishing",
    )
    private val LEVEL_PATTERN = Regex(""".(?<level>([-]󏿾?){1,6}).""")
}
