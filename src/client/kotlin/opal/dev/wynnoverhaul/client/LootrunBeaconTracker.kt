package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Display
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import opal.dev.wynnoverhaul.mixin.client.ItemDisplayAccessor
import opal.dev.wynnoverhaul.mixin.client.TextDisplayAccessor
import java.util.Optional

object LootrunBeaconTracker {
    @Volatile
    var current: List<EntityTracker.Match> = emptyList()
        private set

    fun tick(client: Minecraft) {
        val config = WynnOverhaulConfig.current
        if (!config.lootrunEnabled || !config.lootrunBeaconsEnabled || LootrunModel.state == LootrunModel.State.NOT_RUNNING) {
            if (current.isNotEmpty()) current = emptyList()
            return
        }
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            if (current.isNotEmpty()) current = emptyList()
            return
        }

        val box = player.boundingBox.inflate(BEACON_RANGE)
        val beacons = ArrayList<Pair<Vec3, Int>>()
        val markers = ArrayList<Display.TextDisplay>()

        for (entity in level.getEntities(player, box) { it.isAlive }) {
            when (entity) {
                is Display.ItemDisplay -> beaconColor(entity)?.let { beacons.add(entity.position() to it) }
                is Display.TextDisplay -> markers.add(entity)
                else -> {}
            }
        }

        if (beacons.isEmpty()) {
            if (current.isNotEmpty()) current = emptyList()
            return
        }

        current = beacons
            .filter { (pos, _) -> markers.any { isMarkerFont(it) && nearXZ(it.position(), pos) } }
            .map { (pos, color) -> waypointMatch(pos, color) }
    }

    private fun nearXZ(a: Vec3, b: Vec3): Boolean {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz <= MARKER_PROXIMITY_SQR
    }

    private fun waypointMatch(pos: Vec3, colorArgb: Int): EntityTracker.Match {
        val box = AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.2, pos.z + 0.3)
        return EntityTracker.Match(anchor = null, blockBox = box, label = "Beacon", colorArgb = colorArgb, throughWalls = true, icon = WaypointIcons.beacon)
    }

    private fun beaconColor(entity: Display.ItemDisplay): Int? {
        val stack = (entity as ItemDisplayAccessor).`wynnoverhaul$getItemStack`()
        if (stack.isEmpty || stack.item != Items.POTION) return null
        val potionContents = stack.get(DataComponents.POTION_CONTENTS) ?: return null
        val color = potionContents.customColor().orElse(null) ?: return null
        return BEACON_COLORS[color and 0xFFFFFF]
    }

    private fun isMarkerFont(entity: Display.TextDisplay): Boolean {
        val text = (entity as TextDisplayAccessor).`wynnoverhaul$getText`()
        var result = false
        var seen = false
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                if (!seen && string.isNotEmpty()) {
                    seen = true
                    result = style.font == MARKER_FONT
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private val MARKER_FONT: FontDescription = FontDescription.Resource(Identifier.withDefaultNamespace("marker"))
    private const val BEACON_RANGE = 64.0
    private const val MARKER_PROXIMITY_SQR = 16.0

    private val BEACON_COLORS = mapOf(
        0x00FF80 to 0xFF00FF80.toInt(),
        0xFFFF33 to 0xFFFFFF33.toInt(),
        0x5C5CE6 to 0xFF5C5CE6.toInt(),
        0xFF00FF to 0xFFFF00FF.toInt(),
        0xBFBFBF to 0xFFBFBFBF.toInt(),
        0xFF9500 to 0xFFFF9500.toInt(),
        0xFF0000 to 0xFFFF0000.toInt(),
        0x808080 to 0xFF808080.toInt(),
        0xFFFFFF to 0xFFFFFFFF.toInt(),
        0x55FFFF to 0xFF55FFFF.toInt(),
        0xFD72B1 to 0xFFFD72B1.toInt(),
    )
}
