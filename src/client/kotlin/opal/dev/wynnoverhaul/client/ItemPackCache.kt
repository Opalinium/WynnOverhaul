package opal.dev.wynnoverhaul.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackSelectionConfig
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.repository.RepositorySource
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Optional
import java.util.function.Consumer
import java.util.zip.ZipFile
import kotlin.concurrent.thread

class StackSpec(
    val slot: String,
    val definition: String,
    val floats: List<Float>,
    val flags: List<Boolean>,
    val strings: List<String>,
    val colors: List<Int>,
    val equipment: String?,
    val damage: Int = 0,
    val maxDamage: Int = 0,
) {
    fun signature(): String = "$slot|$definition|$floats|$flags|$strings|$colors|$equipment|$damage|$maxDamage"
}

object ItemPackCache : RepositorySource {
    const val NS = "wowitems"
    private const val EXTRACTOR_VERSION = 2
    private const val PACK_ID = "wynnoverhaul_item_cache"
    private const val MARKER_ENTRY = "assets/minecraft/font/overlay.json"
    private const val SRC = "assets/minecraft/"

    class SlotInfo(val model: Boolean, val equipment: String?)

    val root: Path get() = FabricLoader.getInstance().configDir.resolve("wynnoverhaul").resolve("item_pack")
    private val stampFile: Path get() = root.resolve("stamp.txt")
    private val indexFile: Path get() = root.resolve("index.json")

    @Volatile
    private var building = false

    @Volatile
    var slots: Map<String, SlotInfo> = emptyMap()
        private set

    fun slotInfo(slot: String): SlotInfo? {
        if (slots.isEmpty()) loadIndex()
        return slots[slot]
    }

    override fun loadPacks(consumer: Consumer<Pack>) {
        if (!Files.exists(root.resolve("pack.mcmeta"))) return
        loadIndex()
        val info = PackLocationInfo(PACK_ID, Component.literal("WynnOverhaul item cache"), PackSource.BUILT_IN, Optional.empty())
        val pack = Pack.readMetaAndCreate(
            info,
            PathPackResources.PathResourcesSupplier(root),
            PackType.CLIENT_RESOURCES,
            PackSelectionConfig(true, Pack.Position.TOP, false),
        )
        if (pack != null) consumer.accept(pack)
    }

    private fun loadIndex() {
        runCatching {
            val obj = JsonParser.parseString(Files.readString(indexFile)).asJsonObject.getAsJsonObject("slots")
            slots = obj.entrySet().associate { (key, value) ->
                val o = value.asJsonObject
                key to SlotInfo(o.get("model").asBoolean, o.get("equipment")?.takeIf { !it.isJsonNull }?.asString)
            }
        }
    }

    fun currentStamp(): String? = runCatching { Files.readString(stampFile) }.getOrNull()

    fun buildAsync(specs: List<StackSpec>) {
        if (building || specs.isEmpty()) return
        building = true
        thread(name = "wynnoverhaul-item-pack", isDaemon = true) {
            try {
                build(specs)
            } catch (t: Throwable) {
                WynnOverhaul.LOGGER.warn("Item pack extraction failed", t)
            } finally {
                building = false
            }
        }
    }

    private fun findServerPack(): Path? {
        val downloads = FabricLoader.getInstance().gameDir.resolve("downloads")
        if (!Files.isDirectory(downloads)) return null
        val files = Files.walk(downloads, 2).use { stream -> stream.filter { Files.isRegularFile(it) }.toList() }
        for (file in files.sortedByDescending { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }) {
            val hit = runCatching { ZipFile(file.toFile()).use { it.getEntry(MARKER_ENTRY) != null } }.getOrDefault(false)
            if (hit) return file
        }
        return null
    }

    fun stampFor(specs: List<StackSpec>, pack: Path): String = "$EXTRACTOR_VERSION:${pack.fileName}:" + specs.joinToString(";") { it.signature() }.hashCode()

    private fun build(specs: List<StackSpec>) {
        val pack = findServerPack() ?: return
        val stamp = stampFor(specs, pack)
        if (currentStamp() == stamp && Files.exists(root.resolve("pack.mcmeta"))) {
            loadIndex()
            return
        }
        val staging = root.resolveSibling("item_pack_staging")
        deleteTree(staging)
        Files.createDirectories(staging)
        buildInto(pack, staging, specs)
        Files.writeString(staging.resolve("stamp.txt"), stamp)
        deleteTree(root)
        Files.createDirectories(root.parent)
        Files.move(staging, root)
        loadIndex()
    }

    fun buildInto(pack: Path, target: Path, specs: List<StackSpec>) {
        ZipFile(pack.toFile()).use { zip -> Extraction(zip, target).run(specs) }
    }

    private fun deleteTree(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private class Extraction(val zip: ZipFile, val out: Path) {
        val names: Set<String> = zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toSet()
        val neededTextures = HashSet<String>()
        val writtenModels = HashSet<String>()
        val index = JsonObject()

        fun run(specs: List<StackSpec>) {
            for (spec in specs) {
                val hasModel = writeDefinition(spec)
                val equipment = spec.equipment?.takeIf { writeEquipment(it) }
                index.add(spec.slot, JsonObject().apply {
                    addProperty("model", hasModel)
                    if (equipment != null) addProperty("equipment", equipment)
                })
            }
            for (texture in neededTextures) {
                val path = "textures/$texture.png"
                if (SRC + path in names) copy(SRC + path, "assets/$NS/$path")
                if (SRC + path + ".mcmeta" in names) copy(SRC + path + ".mcmeta", "assets/$NS/$path.mcmeta")
            }
            writeAtlas()
            writeMeta()
            Files.writeString(target("index.json"), JsonObject().apply { add("slots", index) }.toString())
        }

        private fun read(name: String): JsonElement = zip.getInputStream(zip.getEntry(name)).use { JsonParser.parseReader(it.reader(Charsets.UTF_8)) }

        private fun target(rel: String): Path = out.resolve(rel).also { Files.createDirectories(it.parent) }

        private fun copy(entry: String, rel: String) {
            zip.getInputStream(zip.getEntry(entry)).use { Files.copy(it, target(rel), StandardCopyOption.REPLACE_EXISTING) }
        }

        private fun bare(ref: String): String = ref.removePrefix("minecraft:")

        private fun writeDefinition(spec: StackSpec): Boolean {
            val entry = "${SRC}items/${spec.definition}.json"
            if (entry !in names) return false
            val original = read(entry) as? JsonObject ?: return false
            val root = original.get("model") as? JsonObject ?: return false
            val resolved = Resolver(spec).resolve(root)
            rewriteDefinition(resolved)
            val result = JsonObject()
            for ((key, value) in original.entrySet()) if (key != "model") result.add(key, value)
            result.add("model", resolved)
            Files.writeString(target("assets/$NS/items/s_${spec.slot}.json"), result.toString())
            return true
        }

        private fun rewriteDefinition(json: JsonElement) {
            when (json) {
                is JsonObject -> for (key in json.keySet().toList()) {
                    val value = json.get(key)
                    if ((key == "model" || key == "base") && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                        json.addProperty(key, emitModel(value.asString))
                    } else {
                        rewriteDefinition(value)
                    }
                }
                is JsonArray -> json.forEach { rewriteDefinition(it) }
                else -> {}
            }
        }

        private fun emitModel(ref: String): String {
            if (ref.contains(':') && !ref.startsWith("minecraft:")) return ref
            val id = bare(ref)
            if ("${SRC}models/$id.json" !in names) return ref
            if (writtenModels.add(id)) {
                val model = read("${SRC}models/$id.json") as? JsonObject
                if (model != null) {
                    model.get("parent")?.takeIf { it.isJsonPrimitive }?.let { model.addProperty("parent", emitModel(it.asString)) }
                    (model.get("textures") as? JsonObject)?.let { textures ->
                        for (key in textures.keySet().toList()) {
                            val value = textures.get(key)
                            if (value.isJsonPrimitive) textures.addProperty(key, textureRef(value.asString))
                        }
                    }
                    Files.writeString(target("assets/$NS/models/$id.json"), model.toString())
                }
            }
            return "$NS:$id"
        }

        private fun textureRef(ref: String): String {
            if (ref.startsWith("#") || (ref.contains(':') && !ref.startsWith("minecraft:"))) return ref
            val id = bare(ref)
            if ("${SRC}textures/$id.png" !in names) return ref
            neededTextures += id
            return "$NS:$id"
        }

        private fun writeEquipment(asset: String): Boolean {
            val entry = "${SRC}equipment/$asset.json"
            if (entry !in names) return false
            val json = read(entry)
            val textures = HashSet<String>()
            rewriteEquipment(json, textures)
            Files.writeString(target("assets/$NS/equipment/$asset.json"), json.toString())
            for (texture in textures) {
                for (type in listOf("humanoid", "humanoid_leggings", "wings", "horse_body", "llama_body", "wolf_body", "pig_saddle")) {
                    val path = "textures/entity/equipment/$type/$texture.png"
                    if (SRC + path in names) copy(SRC + path, "assets/$NS/$path")
                    if (SRC + path + ".mcmeta" in names) copy(SRC + path + ".mcmeta", "assets/$NS/$path.mcmeta")
                }
            }
            return true
        }

        private fun rewriteEquipment(json: JsonElement, textures: MutableSet<String>) {
            when (json) {
                is JsonObject -> for (key in json.keySet().toList()) {
                    val value = json.get(key)
                    if (key == "texture" && value.isJsonPrimitive) {
                        val name = bare(value.asString)
                        textures += name
                        json.addProperty(key, "$NS:$name")
                    } else {
                        rewriteEquipment(value, textures)
                    }
                }
                is JsonArray -> json.forEach { rewriteEquipment(it, textures) }
                else -> {}
            }
        }

        private fun writeAtlas() {
            val sources = JsonArray()
            for (dir in listOf("wynn", "skins")) {
                sources.add(JsonObject().apply {
                    addProperty("type", "directory")
                    addProperty("source", dir)
                    addProperty("prefix", "$dir/")
                })
            }
            Files.writeString(target("assets/minecraft/atlases/blocks.json"), JsonObject().apply { add("sources", sources) }.toString())
        }

        private fun writeMeta() {
            val original = read("pack.mcmeta").asJsonObject.getAsJsonObject("pack")
            val pack = JsonObject()
            for ((key, value) in original.entrySet()) if (key != "description") pack.add(key, value)
            pack.addProperty("description", "WynnOverhaul item cache")
            Files.writeString(target("pack.mcmeta"), JsonObject().apply { add("pack", pack) }.toString())
        }
    }

    private class Resolver(val spec: StackSpec) {
        private fun type(node: JsonObject): String = node.get("type")?.asString?.removePrefix("minecraft:") ?: ""
        private fun property(node: JsonObject): String = node.get("property")?.asString?.removePrefix("minecraft:") ?: ""
        private fun index(node: JsonObject): Int = node.get("index")?.asInt ?: 0
        private fun empty(): JsonObject = JsonObject().apply { addProperty("type", "minecraft:empty") }

        fun resolve(node: JsonElement?): JsonObject {
            val obj = node as? JsonObject ?: return empty()
            return when (type(obj)) {
                "condition" -> condition(obj)
                "select" -> select(obj)
                "range_dispatch" -> rangeDispatch(obj)
                "composite" -> composite(obj)
                "model" -> model(obj)
                else -> obj
            }
        }

        private fun condition(node: JsonObject): JsonObject {
            val on = if (property(node) == "custom_model_data") spec.flags.getOrNull(index(node)) ?: false else false
            return resolve(node.get(if (on) "on_true" else "on_false"))
        }

        private fun select(node: JsonObject): JsonObject {
            val cases = node.getAsJsonArray("cases") ?: JsonArray()
            if (property(node) == "custom_model_data") {
                val value = spec.strings.getOrNull(index(node))
                for (case in cases) {
                    val c = case.asJsonObject
                    val whenValue = c.get("when")
                    val matches = whenValue != null && (
                        (whenValue.isJsonArray && whenValue.asJsonArray.any { it.asString == value }) ||
                            (whenValue.isJsonPrimitive && whenValue.asString == value)
                        )
                    if (matches) return resolve(c.get("model"))
                }
                return resolve(node.get("fallback"))
            }
            for (case in cases) case.asJsonObject.let { it.add("model", resolve(it.get("model"))) }
            node.get("fallback")?.let { node.add("fallback", resolve(it)) }
            return node
        }

        private fun rangeDispatch(node: JsonObject): JsonObject {
            val entries = node.getAsJsonArray("entries") ?: JsonArray()
            if (property(node) == "custom_model_data") {
                val scale = node.get("scale")?.asFloat ?: 1f
                val value = (spec.floats.getOrNull(index(node)) ?: 0f) * scale
                return pickEntry(node, entries, value)
            }
            if (property(node) == "damage") {
                val normalize = node.get("normalize")?.asBoolean ?: true
                val scale = node.get("scale")?.asFloat ?: 1f
                val raw = spec.damage.toFloat()
                val value = (if (normalize) (if (spec.maxDamage > 0) (raw / spec.maxDamage).coerceIn(0f, 1f) else 0f) else raw) * scale
                return pickEntry(node, entries, value)
            }
            for (entry in entries) entry.asJsonObject.let { it.add("model", resolve(it.get("model"))) }
            node.get("fallback")?.let { node.add("fallback", resolve(it)) }
            return node
        }

        private fun pickEntry(node: JsonObject, entries: JsonArray, value: Float): JsonObject {
            var best: JsonObject? = null
            var bestThreshold = Float.NEGATIVE_INFINITY
            for (entry in entries) {
                val e = entry.asJsonObject
                val threshold = e.get("threshold").asFloat
                if (threshold <= value && threshold >= bestThreshold) {
                    best = e
                    bestThreshold = threshold
                }
            }
            return if (best != null) resolve(best.get("model")) else resolve(node.get("fallback"))
        }

        private fun composite(node: JsonObject): JsonObject {
            val models = node.getAsJsonArray("models") ?: return node
            val resolved = JsonArray()
            for (child in models) resolved.add(resolve(child))
            node.add("models", resolved)
            return node
        }

        private fun model(node: JsonObject): JsonObject {
            val tints = node.getAsJsonArray("tints") ?: return node
            val fixed = JsonArray()
            for (tint in tints) {
                val t = tint.asJsonObject
                if (type(t) == "custom_model_data") {
                    val color = spec.colors.getOrNull(index(t)) ?: t.get("default")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0xFFFFFF
                    fixed.add(JsonObject().apply {
                        addProperty("type", "minecraft:constant")
                        addProperty("value", color)
                    })
                } else {
                    fixed.add(t)
                }
            }
            node.add("tints", fixed)
            return node
        }
    }
}
