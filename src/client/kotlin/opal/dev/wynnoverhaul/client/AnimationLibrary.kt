package opal.dev.wynnoverhaul.client

import com.mojang.brigadier.Command
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.client.WeaponAnimations.ComboStep
import opal.dev.wynnoverhaul.client.WeaponAnimations.Cue
import opal.dev.wynnoverhaul.client.WeaponAnimations.Curve
import opal.dev.wynnoverhaul.client.WeaponAnimations.Gait
import opal.dev.wynnoverhaul.client.WeaponAnimations.Pose
import opal.dev.wynnoverhaul.client.WeaponAnimations.Stance
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal object AnimationLibrary {
    private val BUNDLED = listOf("shared", "spear", "dagger", "wand", "relik", "bow", "spell", "spell_moves", "tool", "locomotion")
    private const val RESOURCE_ROOT = "/assets/wynnoverhaul/animations/"
    private const val README_NAME = "README.txt"
    private const val CHECK_TICKS = 20
    private const val MAX_WARNINGS_SHOWN = 5

    @Volatile
    private var loaded: AnimationTables? = null

    @Volatile
    var revision = 0
        private set

    private var signature = 0L
    private var tickCounter = 0

    val directory: Path
        get() = runCatching { FabricLoader.getInstance().configDir }.getOrDefault(Path.of("config"))
            .resolve("wynnoverhaul").resolve("animations")

    fun tables(): AnimationTables = loaded ?: synchronized(this) {
        loaded ?: load().tables.also {
            loaded = it
            signature = currentSignature()
        }
    }

    fun register() {
        writeReadme()
        ClientTickEvents.END_CLIENT_TICK.register { client -> watch(client) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("owanim")
                    .then(
                        ClientCommands.literal("reload").executes {
                            reloadAndReport(Minecraft.getInstance())
                            Command.SINGLE_SUCCESS
                        },
                    )
                    .then(
                        ClientCommands.literal("folder").executes {
                            runCatching { Files.createDirectories(directory) }
                            Minecraft.getInstance().player?.sendSystemMessage(Component.literal(directory.toString()).withStyle(ChatFormatting.GRAY))
                            Command.SINGLE_SUCCESS
                        },
                    ),
            )
        }
    }

    private fun writeReadme() {
        runCatching {
            val target = directory.resolve(README_NAME)
            if (Files.exists(target)) return
            val stream = AnimationLibrary::class.java.getResourceAsStream("$RESOURCE_ROOT$README_NAME") ?: return
            Files.createDirectories(directory)
            stream.use { Files.copy(it, target) }
        }
    }

    private fun watch(client: Minecraft) {
        if (++tickCounter < CHECK_TICKS) return
        tickCounter = 0
        if (loaded == null) return
        val now = currentSignature()
        if (now == signature) return
        reloadAndReport(client)
    }

    class Outcome(val tables: AnimationTables, val warnings: List<String>, val failed: Boolean)

    fun reload(): Outcome {
        val result = load()
        signature = currentSignature()
        if (!result.failed) {
            loaded = result.tables
            revision++
        }
        return Outcome(result.tables, result.warnings, result.failed)
    }

    private fun reloadAndReport(client: Minecraft) {
        val result = try {
            reload()
        } catch (e: Exception) {
            client.player?.sendSystemMessage(Component.literal("Animation reload failed: ${e.message}").withStyle(ChatFormatting.RED))
            WynnOverhaul.LOGGER.warn("[Animations] reload failed", e)
            return
        }
        val player = client.player
        result.warnings.forEach { WynnOverhaul.LOGGER.warn("[Animations] {}", it) }
        if (result.failed) {
            player?.sendSystemMessage(Component.literal("Animation reload failed, keeping the previous animations.").withStyle(ChatFormatting.RED))
            result.warnings.take(MAX_WARNINGS_SHOWN).forEach { player?.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.GRAY)) }
            return
        }
        val t = result.tables
        player?.sendOverlayMessage(Component.literal("Animations reloaded: ${t.poses.size} poses, ${t.stances.size} stances, ${t.combos.size} movesets"))
        if (result.warnings.isNotEmpty()) {
            player?.sendSystemMessage(Component.literal("${result.warnings.size} animation warnings:").withStyle(ChatFormatting.YELLOW))
            result.warnings.take(MAX_WARNINGS_SHOWN).forEach { player?.sendSystemMessage(Component.literal(it).withStyle(ChatFormatting.GRAY)) }
        }
    }

    private fun currentSignature(): Long {
        var h = 17L
        val dir = directory
        if (!Files.isDirectory(dir)) return h
        runCatching {
            Files.list(dir).use { stream ->
                stream.filter { it.toString().endsWith(".yml") }.sorted().forEach { path ->
                    h = h * 31 + path.fileName.toString().hashCode()
                    h = h * 31 + Files.getLastModifiedTime(path).toMillis()
                    h = h * 31 + Files.size(path)
                }
            }
        }
        return h
    }

    private class Result(val tables: AnimationTables, val warnings: List<String>, val failed: Boolean)

    private class Document(val source: String, val data: Map<String, Any?>)

    private fun load(): Result {
        val warnings = ArrayList<String>()
        val documents = ArrayList<Document>()
        for (name in BUNDLED) {
            val stream = AnimationLibrary::class.java.getResourceAsStream("$RESOURCE_ROOT$name.yml")
                ?: throw IllegalStateException("Missing bundled animation file $name.yml")
            documents.add(Document("$name.yml", stream.use { parse(it) }))
        }
        val dir = directory
        if (Files.isDirectory(dir)) {
            val files = Files.list(dir).use { stream -> stream.filter { it.toString().endsWith(".yml") }.sorted().toList() }
            for (path in files) {
                val name = path.fileName.toString()
                try {
                    Files.newInputStream(path).use { documents.add(Document(name, parse(it))) }
                } catch (e: Exception) {
                    val line = (e as? MarkedYamlEngineException)?.problemMark?.orElse(null)?.let { " (line ${it.line + 1})" }.orEmpty()
                    warnings.add("$name$line: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}")
                }
            }
        }
        val builder = Builder(warnings)
        documents.forEach { builder.apply(it) }
        val tables = builder.build() ?: return Result(emptyTables(), warnings, true)
        return Result(tables, warnings, false)
    }

    private fun emptyTables(): AnimationTables = loaded ?: throw IllegalStateException("Animation library has no tables to fall back on")

    private fun parse(stream: InputStream): Map<String, Any?> {
        val root = Load(LoadSettings.builder().build()).loadFromInputStream(stream)
        val map = root as? Map<*, *> ?: return emptyMap()
        return map.entries.associate { it.key.toString() to it.value }
    }

    private class Builder(private val warnings: MutableList<String>) {
        val poses = LinkedHashMap<String, Pose>()
        val stances = LinkedHashMap<String, Stance>()
        val combos = LinkedHashMap<String, List<ComboStep>>()
        val movesetStances = HashMap<String, String>()
        val openers = HashMap<String, ComboStep>()
        val sfx = HashMap<String, List<Cue>>()
        val impact = HashMap<String, List<Cue>>()
        val handShift = HashMap<String, Float>()
        val handSprintExtra = HashMap<String, Float>()
        val trailSpans = HashMap<String, Pair<Float, Float>>()
        val labels = HashMap<String, String>()
        val spellKeys = HashMap<String, String>()
        val castStyles = HashMap<String, CastStyle>()
        val movesetSpells = HashMap<String, Map<String, String>>()
        val styleDefaults = LinkedHashMap<String, Float>()
        val styleDefs = LinkedHashMap<String, Pair<String, Map<String, Any?>>>()
        val stateDefs = LinkedHashMap<String, Pair<String, Map<String, Any?>>>()
        var firstPersonHidden: Set<String>? = null
        val firstPersonShift = HashMap<String, Float>()
        var lungeFamilies: Set<String>? = null
        var finisher: Cue? = null
        var thump: Cue? = null
        var tail: Cue? = null
        var slam: Cue? = null
        private var source = ""

        fun apply(doc: Document) {
            source = doc.source
            val d = doc.data
            map(d["poses"])?.forEach { (key, value) -> guarded("pose $key") { pose(key, map(value) ?: bad("not a mapping")) } }
            map(d["stances"])?.forEach { (key, value) -> guarded("stance $key") { stances[key] = stance(map(value) ?: bad("not a mapping")) } }
            map(d["movesets"])?.forEach { (key, value) ->
                guarded("moveset $key") {
                    val m = map(value) ?: bad("not a mapping")
                    combos[key] = moveset(m)
                    movesetStances.remove(key)
                    openers.remove(key)
                    map(m["opener"])?.let { o -> openers[key] = ComboStep(o["pose"]?.toString() ?: bad("opener is missing pose"), o["mirror"] == true, false) }
                    m["stance"]?.toString()?.let { movesetStances[key] = it }
                    (m["label"] as? String)?.let { labels[key] = it }
                    castStyles.remove(key)
                    movesetSpells.remove(key)
                    map(m["cast"])?.let { castStyles[key] = castStyle(it) }
                    map(m["spells"])?.let { spells ->
                        movesetSpells[key] = spells.entries.associate { (name, pose) -> name.lowercase().filter { it.isLetter() } to pose.toString() }
                    }
                }
            }
            map(d["movesetSpells"])?.forEach { (moveset, value) ->
                guarded("spells for $moveset") {
                    val spells = map(value) ?: bad("not a mapping")
                    movesetSpells[moveset] = movesetSpells[moveset].orEmpty() +
                        spells.entries.associate { (name, pose) -> name.lowercase().filter { it.isLetter() } to pose.toString() }
                }
            }
            map(d["trails"])?.forEach { (key, value) -> guarded("trail $key") { trailSpans[key] = trail(value) } }
            map(d["impact"])?.forEach { (key, value) -> guarded("impact $key") { impact[key] = cues(value, false) } }
            map(d["spellNames"])?.forEach { (key, value) -> spellKeys[key] = value.toString() }
            map(d["firstPerson"])?.let { fp ->
                guarded("firstPerson") {
                    list(fp["hidden"])?.let { names ->
                        val unknown = names.map { it.toString() }.filter { it !in FirstPersonTables.POSES }
                        if (unknown.isNotEmpty()) bad("unknown pose ${unknown.joinToString()}")
                        firstPersonHidden = names.map { it.toString() }.toSet()
                    }
                    map(fp["shift"])?.forEach { (pose, value) ->
                        if (pose !in FirstPersonTables.POSES) bad("unknown pose $pose")
                        firstPersonShift[pose] = float(value, "shift.$pose")
                    }
                }
            }
            map(d["styleDefaults"])?.forEach { (key, value) -> guarded("style default $key") { styleDefaults[key] = float(value, key) } }
            map(d["styles"])?.forEach { (key, value) -> guarded("style $key") { styleDefs[key] = source to (map(value) ?: bad("not a mapping")) } }
            list(d["states"])?.forEach { raw ->
                guarded("state") {
                    val m = map(raw) ?: bad("not a mapping")
                    val name = m["name"]?.toString() ?: bad("missing name")
                    stateDefs[name] = source to m
                }
            }
            list(d["lungeFamilies"])?.let { families -> lungeFamilies = families.map { it.toString() }.toSet() }
            map(d["cues"])?.let { c ->
                guarded("cues") {
                    c["finisher"]?.let { finisher = cue(it, false) }
                    c["thump"]?.let { thump = cue(it, false) }
                    c["tail"]?.let { tail = cue(it, false) }
                    c["slam"]?.let { slam = cue(it, false) }
                }
            }
        }

        fun build(): AnimationTables? {
            val missing = listOf("lungeFamilies" to lungeFamilies, "cues.finisher" to finisher, "cues.thump" to thump, "cues.tail" to tail, "cues.slam" to slam)
                .filter { it.second == null }.map { it.first }
            if (missing.isNotEmpty()) {
                warnings.add("missing required entries: ${missing.joinToString()}")
                return null
            }
            val validCombos = combos.filterValues { steps -> steps.all { it.key in poses } }
            (combos.keys - validCombos.keys).forEach { warnings.add("moveset $it references an unknown pose and was skipped") }
            val validOpeners = openers.filter { it.key in validCombos && it.value.key in poses }
            (openers.keys - validOpeners.keys).filter { it in validCombos }.forEach { warnings.add("moveset $it names an unknown opener pose and plays its first stroke instead") }
            val validStances = movesetStances.filter { it.key in validCombos && it.value in stances }
            (movesetStances.keys - validStances.keys).filter { it in validCombos }.forEach { warnings.add("moveset $it names an unknown stance and uses its family stance") }
            val validCasts = castStyles.filter { it.key in validCombos }
            val validSpells = HashMap<String, Map<String, String>>()
            for ((moveset, spells) in movesetSpells) {
                if (moveset !in validCombos) continue
                val known = spells.filterValues { it in poses }
                (spells.keys - known.keys).forEach { warnings.add("moveset $moveset names an unknown pose for spell $it and uses the shared spell animation") }
                validSpells[moveset] = known
            }
            val locomotion = buildLocomotion() ?: return null
            return AnimationTables(
                poses, stances, validCombos, validStances, validOpeners, sfx, impact, finisher!!, thump!!, tail!!, slam!!,
                lungeFamilies!!, handShift, handSprintExtra, trailSpans, labels, spellKeys, validCasts, validSpells, locomotion,
                FirstPersonTables(firstPersonHidden ?: emptySet(), firstPersonShift),
            )
        }

        private fun buildLocomotion(): LocomotionTables? {
            if (styleDefaults.isEmpty() || styleDefs.isEmpty()) {
                warnings.add("locomotion styles are missing")
                return null
            }
            val params = styleDefaults.keys.toList()
            val styles = LinkedHashMap<String, StyleDef>()
            for ((key, entry) in styleDefs) {
                source = entry.first
                guarded("style $key") {
                    val values = FloatArray(params.size) { styleDefaults.getValue(params[it]) }
                    var label = key
                    entry.second.forEach { (name, value) ->
                        if (name == "label") {
                            label = value.toString()
                        } else {
                            val index = params.indexOf(name)
                            if (index < 0) bad("unknown style parameter $name")
                            values[index] = float(value, name)
                        }
                    }
                    styles[key] = StyleDef(label, values)
                }
            }
            if (styles.isEmpty()) {
                warnings.add("no locomotion style could be loaded")
                return null
            }
            val base = LocoSignals.COUNT + params.size
            var envSize = base
            val states = ArrayList<LocoState>()
            for ((name, entry) in stateDefs) {
                source = entry.first
                guarded("state $name") {
                    val state = compileState(name, entry.second, params, base)
                    envSize = maxOf(envSize, base + state.locals.size)
                    states.add(state)
                }
            }
            return LocomotionTables(params, styles, states, envSize)
        }

        private fun compileState(name: String, d: Map<String, Any?>, params: List<String>, base: Int): LocoState {
            val toggle = d["toggle"]?.toString()
            if (toggle != null && toggle !in LocoToggles.NAMES) bad("unknown toggle $toggle")
            val localIndex = LinkedHashMap<String, Int>()
            fun resolve(variable: String): Int {
                localIndex[variable]?.let { return it }
                if (variable.startsWith("style.")) {
                    val i = params.indexOf(variable.substring(6))
                    return if (i < 0) -1 else LocoSignals.COUNT + i
                }
                return LocoSignals.NAMES.indexOf(variable)
            }
            val gate = d["gate"]?.toString()?.let { g -> resolve(g).also { if (it < 0) bad("unknown gate variable $g") } } ?: -1
            val localSlots = ArrayList<Int>()
            val locals = ArrayList<AnimExpr>()
            map(d["vars"])?.forEach { (variable, text) ->
                val expr = AnimExpr.compile(text.toString(), ::resolve)
                localIndex[variable] = base + locals.size
                localSlots.add(base + locals.size)
                locals.add(expr)
            }
            val effects = (list(d["effects"]) ?: bad("effects must be a list")).map { raw ->
                val e = map(raw) ?: bad("each effect must be a mapping")
                val target = LocoTargets.code(e["to"]?.toString() ?: bad("effect is missing to"))
                if (target < 0) bad("unknown target ${e["to"]}")
                val free = e["freeArms"] == true
                when {
                    e["add"] != null -> LocoEffect(target, AnimExpr.compile(e["add"].toString(), ::resolve), null, false, free)
                    e["sub"] != null -> LocoEffect(target, AnimExpr.compile("-(" + e["sub"] + ")", ::resolve), null, false, free)
                    e["approach"] != null -> {
                        val rate = e["rate"] ?: bad("approach needs a rate")
                        LocoEffect(target, AnimExpr.compile(e["approach"].toString(), ::resolve), AnimExpr.compile(rate.toString(), ::resolve), true, free)
                    }
                    else -> bad("effect needs add, sub or approach")
                }
            }
            return LocoState(name, toggle, gate, localSlots.toIntArray(), locals.toTypedArray(), effects)
        }

        private fun guarded(what: String, block: () -> Unit) {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                warnings.add("$source: $what: ${e.message}")
            }
        }

        private fun bad(message: String): Nothing = throw IllegalArgumentException(message)

        private fun map(value: Any?): Map<String, Any?>? =
            (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }

        private fun list(value: Any?): List<Any?>? = value as? List<*>

        private fun float(value: Any?, what: String): Float =
            (value as? Number)?.toFloat() ?: bad("$what must be a number")

        private fun floatOr(value: Any?, default: Float, what: String): Float =
            if (value == null) default else float(value, what)

        private fun curve(value: Any?, channel: String): Curve {
            val points = list(value) ?: bad("channel $channel must be a list of [time, value] pairs")
            if (points.size < 2) bad("channel $channel needs at least two points")
            val times = FloatArray(points.size)
            val values = FloatArray(points.size)
            for (i in points.indices) {
                val pair = list(points[i]) ?: bad("channel $channel point ${i + 1} must be [time, value]")
                if (pair.size != 2) bad("channel $channel point ${i + 1} must be [time, value]")
                times[i] = float(pair[0], "channel $channel time")
                values[i] = if (pair[1] == null) Float.NaN else float(pair[1], "channel $channel value")
                if (i > 0 && times[i] <= times[i - 1]) bad("channel $channel times must increase")
            }
            if (times.first() != 0f || times.last() != 1f) bad("channel $channel must run from time 0 to 1")
            return Curve(times, values)
        }

        private fun pose(key: String, d: Map<String, Any?>) {
            val pose = Pose()
            pose.key = key
            pose.seconds = floatOr(d["seconds"], pose.seconds, "seconds")
            pose.hit = floatOr(d["hit"], pose.hit, "hit")
            if (pose.seconds <= 0f) bad("seconds must be positive")
            if (pose.hit <= 0f || pose.hit >= 1f) bad("hit must be between 0 and 1")
            d["recover"]?.let {
                pose.recover = float(it, "recover")
                if (pose.recover <= pose.hit || pose.recover >= 1f) bad("recover must be between hit and 1")
            }
            pose.gripOffset = floatOr(d["gripOffset"], pose.gripOffset, "gripOffset")
            pose.blend = floatOr(d["blend"], pose.blend, "blend")
            if (pose.blend <= 0f) bad("blend must be positive")
            pose.holdEnd = d["holdEnd"] == true
            pose.spin = d["spin"] == true
            map(d["channels"])?.forEach { (name, value) ->
                val index = AnimationChannels.indexOf(name)
                if (index < 0) bad("unknown channel $name")
                pose.curves[index] = curve(value, name)
            }
            val sounds = list(d["sounds"])?.let { cues(it, true) }
            poses[key] = pose
            labels.remove(key)
            (d["label"] as? String)?.let { labels[key] = it }
            handShift.remove(key)
            d["handShift"]?.let { handShift[key] = float(it, "handShift") }
            handSprintExtra.remove(key)
            d["handSprintExtra"]?.let { handSprintExtra[key] = float(it, "handSprintExtra") }
            if (sounds != null) sfx[key] = sounds else sfx.remove(key)
        }

        private fun state(value: Any?, name: String): FloatArray {
            val out = WeaponAnimations.REST_BASE.copyOf()
            (map(value) ?: bad("$name must be a mapping of channel values")).forEach { (channel, v) ->
                val index = AnimationChannels.indexOf(channel)
                if (index < 0) bad("unknown channel $channel in $name")
                out[index] = float(v, "$name.$channel")
            }
            return out
        }

        private fun stance(d: Map<String, Any?>): Stance {
            val gaitMap = map(d["gait"])
            val base = WeaponAnimations.DEFAULT_GAIT
            fun g(name: String, default: Float) = floatOr(gaitMap?.get(name), default, "gait.$name")
            val gait = Gait(
                g("mainWalk", base.mainWalk), g("offWalk", base.offWalk), g("mainSprint", base.mainSprint), g("offSprint", base.offSprint),
                g("across", base.across), g("lean", base.lean), g("leanBounce", base.leanBounce), g("tipWalk", base.tipWalk),
                g("tipSprint", base.tipSprint), g("yaw", base.yaw), g("armBob", base.armBob), g("twist", base.twist),
            )
            return Stance(
                state(d["carry"], "carry"), state(d["ready"], "ready"), state(d["idle"], "idle"), state(d["sprint"], "sprint"),
                floatOr(d["gripOffset"], 7f, "gripOffset"), d["walk"]?.let { state(it, "walk") }, gait,
            )
        }

        private fun moveset(d: Map<String, Any?>): List<ComboStep> {
            val light = list(d["light"]) ?: bad("light must be a list of steps")
            if (light.isEmpty()) bad("light needs at least one step")
            return light.map { raw ->
                val step = map(raw) ?: bad("each light step must be a mapping with a pose")
                val pose = step["pose"]?.toString() ?: bad("light step is missing pose")
                ComboStep(pose, step["mirror"] == true, step["finisher"] == true)
            }
        }

        private fun trail(value: Any?): Pair<Float, Float> {
            val pair = list(value) ?: bad("trail must be [from, to]")
            if (pair.size != 2) bad("trail must be [from, to]")
            return float(pair[0], "trail from") to float(pair[1], "trail to")
        }

        private fun castStyle(d: Map<String, Any?>): CastStyle {
            fun f(name: String, default: Float): Float = floatOr(d[name], default, "cast $name")
            val twirl = map(d["twirl"])
            val twirlName = twirl?.get("channel")?.toString() ?: "twist"
            val twirlChannel = AnimationChannels.indexOf(twirlName)
            if (twirl != null && twirlChannel < 0) bad("unknown twirl channel $twirlName")
            val style = CastStyle(
                f("seconds", 1f), f("arc", 1f), f("body", 1f), f("reach", 1f), f("raise", 0f), f("turn", 0f), d["mirror"] == true,
                f("grip", -1f), f("hit", 0f), f("blend", 1f),
                if (twirl != null) twirlChannel else -1,
                floatOr(twirl?.get("amplitude"), 0f, "cast twirl amplitude"),
                floatOr(twirl?.get("cycles"), 1f, "cast twirl cycles"),
                list(d["sounds"])?.let { cues(it, true) }.orEmpty(),
                list(d["addSounds"])?.let { cues(it, true) }.orEmpty(),
            )
            if (style.seconds <= 0f || style.blend <= 0f) bad("cast seconds and blend must be positive")
            return style
        }

        private fun cues(value: Any?, timed: Boolean): List<Cue> =
            (list(value) ?: bad("cues must be a list")).mapNotNull { cue(it, timed) }

        private fun cue(value: Any?, timed: Boolean): Cue? {
            val d = map(value) ?: bad("a cue must be a mapping")
            val id = d["sound"]?.toString() ?: bad("cue is missing sound")
            val identifier = Identifier.tryParse(id) ?: bad("invalid sound id $id")
            val sound: SoundEvent? = BuiltInRegistries.SOUND_EVENT.getOptional(identifier).orElse(null)
            if (sound == null) {
                warnings.add("$source: unknown sound $id was skipped")
                return null
            }
            val at = if (timed) floatOr(d["at"], 0f, "cue at") else 0f
            return Cue(at, sound, floatOr(d["pitch"], 1f, "cue pitch"), floatOr(d["volume"], 1f, "cue volume"))
        }
    }
}
