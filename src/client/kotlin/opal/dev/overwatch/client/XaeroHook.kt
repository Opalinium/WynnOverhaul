package opal.dev.overwatch.client

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import opal.dev.overwatch.Overwatch
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object XaeroHook {
    private class Desired(val name: String, val x: Int, val y: Int, val z: Int, val color: String)

    private class Added(val waypoint: Any, val x: Int, val y: Int, val z: Int, val name: String)

    private val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded("xaerominimap") }
    private val added = HashMap<String, Added>()
    private var waypointSet: Any? = null
    private var tickCounter = 0
    private var failed = false

    private class Api(
        val moduleInstance: Any,
        val getSession: Method,
        val getWorldManager: Method,
        val getCurrentWorld: Method,
        val getCurrentSet: Method,
        val colors: Map<String, Any>,
        val purposeNormal: Any,
        val constructor: Constructor<*>,
    )

    private val api: Api? by lazy { buildApi() }

    fun tick(client: Minecraft) {
        if (!loaded || failed) return
        if (++tickCounter % SYNC_INTERVAL != 0) return
        val config = OverwatchConfig.current
        val desired = if (config.xaeroHookEnabled && client.level != null) collect() else emptyMap()
        if (desired.isEmpty() && added.isEmpty()) return
        try {
            sync(desired)
        } catch (t: Throwable) {
            failed = true
            Overwatch.LOGGER.warn("Overwatch: Xaero's Minimap hook disabled ({})", t.toString())
        }
    }

    fun clear() {
        if (!loaded || failed || added.isEmpty()) return
        try {
            sync(emptyMap())
        } catch (t: Throwable) {
            added.clear()
        }
    }

    private fun collect(): Map<String, Desired> {
        val out = LinkedHashMap<String, Desired>()
        DiscoveryTracker.target?.let { target ->
            val located = target.located
            val name = if (located.approximate) "${target.name} (approx.)" else target.name
            out["discovery:${target.name}"] = Desired(name, located.x, located.y, located.z, "PURPLE")
        }
        for (match in QuestBeaconTracker.stable) {
            val center = match.center()
            out["quest:${match.label}"] = Desired(match.label, center.x.toInt(), center.y.toInt(), center.z.toInt(), "GREEN")
        }
        return out
    }

    private fun sync(desired: Map<String, Desired>) {
        val api = api ?: run {
            failed = true
            return
        }
        val set = currentSet(api) ?: return
        if (set !== waypointSet) {
            added.clear()
            waypointSet = set
        }
        val removeMethod = set.javaClass.methods.first { it.name == "remove" && it.parameterCount == 1 && !it.parameterTypes[0].isPrimitive }
        val addMethod = set.javaClass.methods.first { it.name == "add" && it.parameterCount == 1 }

        val iterator = added.entries.iterator()
        while (iterator.hasNext()) {
            val (key, entry) = iterator.next()
            val want = desired[key]
            if (want == null || want.x != entry.x || want.y != entry.y || want.z != entry.z || want.name != entry.name) {
                removeMethod.invoke(set, entry.waypoint)
                iterator.remove()
            }
        }
        for ((key, want) in desired) {
            if (added.containsKey(key)) continue
            val color = api.colors[want.color] ?: api.colors.values.first()
            val initials = want.name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "?"
            val waypoint = api.constructor.newInstance(want.x, want.y, want.z, want.name, initials, color, api.purposeNormal, true)
            addMethod.invoke(set, waypoint)
            added[key] = Added(waypoint, want.x, want.y, want.z, want.name)
        }
    }

    private fun currentSet(api: Api): Any? {
        val session = api.getSession.invoke(api.moduleInstance) ?: return null
        val manager = api.getWorldManager.invoke(session) ?: return null
        val world = api.getCurrentWorld.invoke(manager) ?: return null
        return api.getCurrentSet.invoke(world)
    }

    private fun buildApi(): Api? = try {
        val modules = Class.forName("xaero.hud.minimap.BuiltInHudModules")
        val module = modules.getField("MINIMAP").get(null)
        val getSession = module.javaClass.getMethod("getCurrentSession")
        val sessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession")
        val getWorldManager = sessionClass.getMethod("getWorldManager")
        val managerClass = Class.forName("xaero.hud.minimap.world.MinimapWorldManager")
        val getCurrentWorld = managerClass.getMethod("getCurrentWorld")
        val worldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld")
        val getCurrentSet = worldClass.getMethod("getCurrentWaypointSet")
        val colorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor")
        val purposeClass = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose")
        val waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint")
        val colors = colorClass.enumConstants.associate { (it as Enum<*>).name to (it as Any) }
        val purpose = purposeClass.enumConstants.first { (it as Enum<*>).name == "NORMAL" } as Any
        val constructor = waypointClass.getConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            String::class.java, String::class.java, colorClass, purposeClass, Boolean::class.javaPrimitiveType,
        )
        Api(module, getSession, getWorldManager, getCurrentWorld, getCurrentSet, colors, purpose, constructor)
    } catch (t: Throwable) {
        Overwatch.LOGGER.warn("Overwatch: Xaero's Minimap API not found ({})", t.toString())
        null
    }

    private const val SYNC_INTERVAL = 20
}
