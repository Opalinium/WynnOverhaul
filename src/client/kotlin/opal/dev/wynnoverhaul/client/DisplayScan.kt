package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.AABB
import kotlin.math.abs

object DisplayScan {
    class Label(val entity: Display.TextDisplay, val text: Component, val string: String)

    private class Cached(val text: Component, val string: String)

    private const val RADIUS = 64.0

    private val strings = HashMap<Int, Cached>()
    private val seen = HashSet<Int>()
    private var labels = ArrayList<Label>()
    private var items = ArrayList<Display.ItemDisplay>()
    private var playerX = 0.0
    private var playerY = 0.0
    private var playerZ = 0.0

    fun wanted(): Boolean {
        val config = WynnOverhaulConfig.current
        return config.totemHudEnabled || config.dpsHudEnabled || config.hitFeedbackEnabled || config.hitFeedbackNumbers || config.classBuffHudEnabled
    }

    fun refresh(client: Minecraft) {
        val player = client.player
        val level = client.level
        if (player == null || level == null || !wanted()) {
            clear()
            return
        }
        playerX = player.x
        playerY = player.y
        playerZ = player.z
        val box = AABB(playerX - RADIUS, playerY - RADIUS, playerZ - RADIUS, playerX + RADIUS, playerY + RADIUS, playerZ + RADIUS)
        val nextLabels = ArrayList<Label>()
        val nextItems = ArrayList<Display.ItemDisplay>()
        seen.clear()
        for (entity in level.getEntities(player, box) { it is Display }) {
            when (entity) {
                is Display.TextDisplay -> {
                    val text = entity.text
                    var cached = strings[entity.id]
                    if (cached == null || cached.text !== text) {
                        cached = Cached(text, text.string)
                        strings[entity.id] = cached
                    }
                    seen.add(entity.id)
                    nextLabels.add(Label(entity, text, cached.string))
                }
                is Display.ItemDisplay -> nextItems.add(entity)
                else -> {}
            }
        }
        strings.keys.retainAll(seen)
        labels = nextLabels
        items = nextItems
    }

    fun clear() {
        strings.clear()
        seen.clear()
        if (labels.isNotEmpty()) labels = ArrayList()
        if (items.isNotEmpty()) items = ArrayList()
    }

    fun labelsWithin(radius: Double): List<Label> =
        if (radius >= RADIUS) labels else labels.filter { within(it.entity.x, it.entity.y, it.entity.z, radius) }

    fun itemsWithin(radius: Double): List<Display.ItemDisplay> =
        if (radius >= RADIUS) items else items.filter { within(it.x, it.y, it.z, radius) }

    private fun within(x: Double, y: Double, z: Double, radius: Double): Boolean =
        abs(x - playerX) <= radius && abs(y - playerY) <= radius && abs(z - playerZ) <= radius
}
