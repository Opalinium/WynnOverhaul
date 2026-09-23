package opal.dev.overwatch.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Marker
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.LidBlockEntity
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.Overwatch
import opal.dev.overwatch.mixin.client.TextDisplayAccessor
import java.util.Optional
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

object EntityTracker {

    class Match(
        val anchor: Entity?,
        val blockBox: AABB?,
        val label: String,
        val colorArgb: Int,
        val throughWalls: Boolean,
    ) {
        fun box(): AABB = blockBox ?: anchor!!.boundingBox
        fun center(): Vec3 = box().center
    }

    @Volatile
    var current: List<Match> = emptyList()
        private set

    @Volatile
    var discovered: List<Match> = emptyList()
        private set

    private val seen = HashSet<UUID>()
    private val regexCache = HashMap<String, Regex?>()
    private val ws = Regex("\\s+")
    private val section = Regex("§.")
    private val hexish = Regex("^#?[0-9a-fA-F]{3,}$")
    private val cooldownPart = Regex("""(\d+)\s*(h|m|s)\b""", RegexOption.IGNORE_CASE)
    private val confirmedChests = HashMap<Long, ChestRecord>()
    private val confirmedNodes = HashMap<Long, NodeRecord>()

    private var nextScanNanos = 0L
    private var cachedPreview = 0
    private var nextPreviewNanos = 0L
    private var loggedError = false

    fun tick(client: Minecraft) {
        if (client.level != null) {
            TrackerChestStore.sync(client, confirmedChests)
            TrackerNodeStore.sync(client, confirmedNodes)
        } else {
            TrackerChestStore.detach(confirmedChests)
            TrackerNodeStore.detach(confirmedNodes)
        }

        val config = OverwatchConfig.current
        if (ScreenGate.blockedByScreen(client)) {
            clear()
            return
        }
        val player = client.player ?: run { clear(); return }
        val level = client.level ?: run { clear(); return }

        val now = System.nanoTime()
        if (now < nextScanNanos) return
        nextScanNanos = now + SCAN_INTERVAL_NANOS

        if (!config.trackerEnabled) {
            clear()
            val pointRules = config.trackerRules.filter { it.enabled && (it.trackBlockBelow || it.trackGatheringNode) }
            if (pointRules.isEmpty()) return
            try {
                rememberPointsOnly(player, level, config, pointRules)
            } catch (t: Throwable) {
                if (!loggedError) {
                    loggedError = true
                    Overwatch.LOGGER.error("Entity tracker background chest/node scan failed", t)
                }
            }
            return
        }

        val rules = config.trackerRules.filter { it.enabled }
        if (rules.isEmpty()) {
            clear()
            return
        }

        try {
            runScan(client, config, player, level, rules)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Entity tracker scan failed", t)
            }
        }
    }

    private fun rememberPointsOnly(
        player: Player,
        level: ClientLevel,
        config: OverwatchConfig,
        pointRules: List<OverwatchConfig.TrackerRule>,
    ) {
        scan(player, level, config, pointRules) { entity, name, rule ->
            val redirect = redirect(level, entity, rule) ?: return@scan
            val pos = redirect.blockPos ?: return@scan
            if (rule.trackGatheringNode) {
                observeNode(pos, componentOf(entity), name, rule.nodeProfession)
            } else {
                observeChest(pos, componentOf(entity), name)
            }
        }
    }

    private fun runScan(
        client: Minecraft,
        config: OverwatchConfig,
        player: Player,
        level: ClientLevel,
        rules: List<OverwatchConfig.TrackerRule>,
    ) {
        val matches = ArrayList<Match>()
        val newly = ArrayList<Match>()
        val liveIds = HashSet<UUID>()

        scan(player, level, config, rules) { entity, name, rule ->
            val redirect = redirect(level, entity, rule) ?: return@scan
            if (rule.trackBlockBelow) {
                val pos = redirect.blockPos
                if (pos != null) {
                    observeChest(pos, componentOf(entity), name)
                } else {
                    val key = entity.uuid
                    if (!liveIds.add(key)) return@scan
                    val match = Match(entity, redirect.box, rule.label.ifBlank { sanitizeLabel(name) }, rule.colorArgb.toInt(), rule.throughWalls)
                    matches.add(match)
                    if (seen.add(key)) newly.add(match)
                }
                return@scan
            }
            if (rule.trackGatheringNode) {
                val pos = redirect.blockPos
                if (pos != null) {
                    observeNode(pos, componentOf(entity), name, rule.nodeProfession)
                } else {
                    val key = entity.uuid
                    if (!liveIds.add(key)) return@scan
                    val match = Match(entity, redirect.box, rule.label.ifBlank { sanitizeLabel(name) }, rule.colorArgb.toInt(), rule.throughWalls)
                    matches.add(match)
                    if (seen.add(key)) newly.add(match)
                }
                return@scan
            }
            val anchor = redirect.anchor ?: return@scan
            val key = anchor.uuid
            if (!liveIds.add(key)) return@scan
            val match = Match(anchor, null, rule.label.ifBlank { sanitizeLabel(name) }, rule.colorArgb.toInt(), rule.throughWalls)
            matches.add(match)
            if (seen.add(key)) newly.add(match)
        }

        for (rule in rules) {
            if (rule.trackBlockBelow) chestScan(player, level, config, rule, matches, newly, liveIds)
        }

        seen.retainAll(liveIds)
        matches.sortBy { player.distanceToSqr(it.center()) }
        current = matches

        val far = discoveredChests(player, level, config, rules, matches) + discoveredNodes(player, level, config, rules, matches)
        discovered = far.sortedBy { player.distanceToSqr(it.center()) }

        val rows = ArrayList<EntityTrackerHudState.Row>(matches.size + far.size)
        for (m in matches) rows.add(EntityTrackerHudState.Row(m.label, sqrt(player.distanceToSqr(m.center())), m.colorArgb))
        for (m in far) rows.add(EntityTrackerHudState.Row(m.label, sqrt(player.distanceToSqr(m.center())), m.colorArgb))
        rows.sortBy { it.distance }
        EntityTrackerHudState.rows = rows

        if (newly.isNotEmpty()) ping(client, config, newly)
    }

    private fun discoveredChests(
        player: Player,
        level: ClientLevel,
        config: OverwatchConfig,
        rules: List<OverwatchConfig.TrackerRule>,
        liveMatches: List<Match>,
    ): List<Match> {
        if (!config.trackerDiscoveredChestsEnabled || confirmedChests.isEmpty()) return emptyList()
        val chestRules = rules.filter { it.trackBlockBelow }
        if (chestRules.isEmpty()) return emptyList()

        val shown = HashSet<Long>(liveMatches.size)
        for (m in liveMatches) {
            val box = m.blockBox ?: continue
            val c = box.center
            shown.add(BlockPos(floor(c.x).toInt(), floor(c.y).toInt(), floor(c.z).toInt()).asLong())
        }

        val rangeSqr = config.trackerDiscoveredChestRange * config.trackerDiscoveredChestRange
        val pos = player.position()
        val chunkSource = level.chunkSource
        val out = ArrayList<Match>()
        for ((key, record) in confirmedChests) {
            if (key in shown) continue
            val rule = chestRules.firstOrNull { record.tier == 0 || it.minChestTier <= 0 || record.tier >= it.minChestTier } ?: continue
            if (onCooldown(record.availableAtMillis) && !rule.alwaysDisplay) continue
            val bp = BlockPos.of(key)
            if (pos.distanceToSqr(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5) > rangeSqr) continue

            val be = chunkSource.getChunk(bp.x shr 4, bp.z shr 4, ChunkStatus.FULL, false)?.getBlockEntity(bp)
            var availableAt = record.availableAtMillis
            if (be != null && chestOpened(be)) {
                extendCooldownFromLiveOpen(bp)
                availableAt = confirmedChests[key]?.availableAtMillis ?: availableAt
                if (!rule.alwaysDisplay) continue
            }

            val box = AABB(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), bp.x + 1.0, bp.y + 1.0, bp.z + 1.0)
            val label = if (rule.alwaysDisplay) withCooldownSuffix(record.label, availableAt) else record.label
            out.add(Match(null, box, label, rule.colorArgb.toInt(), rule.throughWalls))
        }
        out.sortBy { player.distanceToSqr(it.center()) }
        return out
    }

    private fun discoveredNodes(
        player: Player,
        level: ClientLevel,
        config: OverwatchConfig,
        rules: List<OverwatchConfig.TrackerRule>,
        liveMatches: List<Match>,
    ): List<Match> {
        if (!config.trackerDiscoveredNodesEnabled || confirmedNodes.isEmpty()) return emptyList()
        val nodeRules = rules.filter { it.trackGatheringNode }
        if (nodeRules.isEmpty()) return emptyList()

        val shown = HashSet<Long>(liveMatches.size)
        for (m in liveMatches) {
            val box = m.blockBox ?: continue
            val c = box.center
            shown.add(BlockPos(floor(c.x).toInt(), floor(c.y).toInt(), floor(c.z).toInt()).asLong())
        }

        val rangeSqr = config.trackerDiscoveredNodeRange * config.trackerDiscoveredNodeRange
        val pos = player.position()
        val candidates = ArrayList<BlockPos>()
        for ((key, record) in confirmedNodes) {
            if (key in shown) continue
            val rule = nodeRules.firstOrNull {
                it.nodeProfession.isBlank() || record.profession.isBlank() || it.nodeProfession.equals(record.profession, ignoreCase = true)
            } ?: continue
            if (onCooldown(record.availableAtMillis) && !rule.alwaysDisplay) continue
            val bp = BlockPos.of(key)
            if (pos.distanceToSqr(bp.x + 0.5, bp.y + 0.5, bp.z + 0.5) > rangeSqr) continue
            candidates.add(bp)
        }
        candidates.sortBy { pos.distanceToSqr(it.x + 0.5, it.y + 0.5, it.z + 0.5) }

        val out = ArrayList<Match>()
        val kept = ArrayList<BlockPos>()
        for (bp in candidates) {
            if (kept.any { it.distSqr(bp) <= NODE_MERGE_DISTANCE_SQR }) continue
            kept.add(bp)
            val record = confirmedNodes[bp.asLong()] ?: continue
            val rule = nodeRules.firstOrNull {
                it.nodeProfession.isBlank() || record.profession.isBlank() || it.nodeProfession.equals(record.profession, ignoreCase = true)
            } ?: continue
            val box = AABB(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), bp.x + 1.0, bp.y + 1.0, bp.z + 1.0)
            val label = if (rule.alwaysDisplay) withCooldownSuffix(record.label, record.availableAtMillis) else record.label
            out.add(Match(null, box, label, rule.colorArgb.toInt(), rule.throughWalls))
        }
        return out
    }

    fun previewCount(client: Minecraft): Int {
        val now = System.nanoTime()
        if (now < nextPreviewNanos) return cachedPreview
        nextPreviewNanos = now + PREVIEW_INTERVAL_NANOS

        val config = OverwatchConfig.current
        val player = client.player ?: return 0
        val level = client.level ?: return 0
        val rules = config.trackerRules.filter { it.enabled }
        if (rules.isEmpty()) {
            cachedPreview = 0
            return 0
        }
        val hits = HashSet<UUID>()
        runCatching {
            scan(player, level, config, rules) { entity, _, rule ->
                val r = redirect(level, entity, rule) ?: return@scan
                val id = if ((rule.trackBlockBelow || rule.trackGatheringNode) && r.blockPos != null) {
                    chestKey(r.blockPos)
                } else {
                    r.anchor?.uuid ?: return@scan
                }
                hits.add(id)
            }
            for (rule in rules) {
                if (rule.trackBlockBelow) chestScan(player, level, config, rule, null, null, hits)
            }
        }
        cachedPreview = hits.size
        return cachedPreview
    }

    private inline fun scan(
        player: Player,
        level: ClientLevel,
        config: OverwatchConfig,
        rules: List<OverwatchConfig.TrackerRule>,
        onMatch: (Entity, String, OverwatchConfig.TrackerRule) -> Unit,
    ) {
        val range = config.trackerRange
        val rangeSqr = range * range
        val box = player.boundingBox.inflate(range)
        for (entity in level.getEntities(player, box) { it.isAlive }) {
            if (player.distanceToSqr(entity) > rangeSqr) continue
            val name = nameOf(entity)
            val rule = rules.firstOrNull { ruleMatches(it, entity, name) } ?: continue
            onMatch(entity, name, rule)
        }
    }

    private fun chestScan(
        player: Player,
        level: ClientLevel,
        config: OverwatchConfig,
        rule: OverwatchConfig.TrackerRule,
        matches: MutableList<Match>?,
        newly: MutableList<Match>?,
        liveIds: MutableSet<UUID>,
    ) {
        val range = minOf(config.trackerRange, CHEST_SCAN_MAX_RANGE)
        val rangeSqr = range * range
        val chunkRadius = ceil(range / 16.0).toInt()
        val pcx = player.blockX shr 4
        val pcz = player.blockZ shr 4
        val chunkSource = level.chunkSource
        val playerPos = player.position()

        for (cx in pcx - chunkRadius..pcx + chunkRadius) {
            for (cz in pcz - chunkRadius..pcz + chunkRadius) {
                val chunk = chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false) ?: continue
                for (pos in chunk.blockEntitiesPos) {
                    val be = chunk.getBlockEntity(pos) ?: continue
                    if (be !is ChestBlockEntity && be !is BarrelBlockEntity) continue
                    val container = be as RandomizableContainerBlockEntity
                    if (playerPos.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > rangeSqr) continue

                    val remembered = confirmedChests[pos.asLong()]
                    val known = remembered != null
                    val onCd = onCooldown(remembered?.availableAtMillis ?: 0L)
                    if (onCd && !rule.alwaysDisplay) continue
                    val named = !known && container.customName?.string?.let { nameMatches(rule, it) } == true
                    if (!rule.trackAllChests && !known && !named) continue

                    var availableAt = remembered?.availableAtMillis ?: 0L
                    if (chestOpened(be)) {
                        extendCooldownFromLiveOpen(pos)
                        availableAt = confirmedChests[pos.asLong()]?.availableAtMillis ?: availableAt
                        if (!rule.alwaysDisplay) continue
                    }

                    val tier = remembered?.tier ?: container.customName?.let { chestInfo(it, it.string).tier } ?: 0
                    if (rule.minChestTier > 0 && tier in 1 until rule.minChestTier) continue

                    val key = chestKey(pos)
                    if (!liveIds.add(key)) continue
                    if (matches == null) continue

                    val box = level.getBlockState(pos).getCollisionShape(level, pos).bounds()
                        .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).inflate(0.006)
                    val baseLabel = rule.label.ifBlank {
                        remembered?.label
                            ?: container.customName?.let { chestInfo(it, it.string).display() }
                            ?: FALLBACK_CHEST_LABEL
                    }
                    val label = if (rule.alwaysDisplay) withCooldownSuffix(baseLabel, availableAt) else baseLabel
                    val match = Match(null, box, label, rule.colorArgb.toInt(), rule.throughWalls)
                    matches.add(match)
                    if (seen.add(key)) newly?.add(match)
                }
            }
        }
    }

    private fun observeChest(pos: BlockPos, component: Component?, fallback: String) {
        val raw = component?.string ?: fallback
        val cooldownMs = parseCooldownMs(raw)
        if (cooldownMs != null) {
            markChestCooldown(pos, System.currentTimeMillis() + cooldownMs)
            return
        }
        val info = chestInfo(component, fallback)
        rememberChest(pos, info.display(), info.tier)
    }

    private fun parseCooldownMs(raw: String): Long? {
        if (raw.contains("loot chest", ignoreCase = true)) return null
        var totalMs = 0L
        for (m in cooldownPart.findAll(raw)) {
            val value = m.groupValues[1].toLongOrNull() ?: continue
            totalMs += when (m.groupValues[2].lowercase()) {
                "h" -> value * 3_600_000L
                "m" -> value * 60_000L
                "s" -> value * 1_000L
                else -> 0L
            }
        }
        return totalMs.takeIf { it > 0L }
    }

    private fun markChestCooldown(pos: BlockPos, availableAtMillis: Long) {
        val key = pos.asLong()
        val prev = confirmedChests[key] ?: ChestRecord(FALLBACK_CHEST_LABEL, 0)
        if (prev.availableAtMillis != availableAtMillis) {
            confirmedChests[key] = prev.copy(availableAtMillis = availableAtMillis)
            TrackerChestStore.markDirty()
        }
    }

    private fun onCooldown(availableAtMillis: Long): Boolean = availableAtMillis > System.currentTimeMillis()

    private fun withCooldownSuffix(label: String, availableAtMillis: Long): String {
        val remaining = availableAtMillis - System.currentTimeMillis()
        return if (remaining > 0L) "$label (back in ${formatDuration(remaining)})" else label
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    private fun extendCooldownFromLiveOpen(pos: BlockPos) {
        val key = pos.asLong()
        val prev = confirmedChests[key] ?: return
        val candidate = System.currentTimeMillis() + LIVE_OPEN_KEEPALIVE_MS
        if (candidate > prev.availableAtMillis) {
            confirmedChests[key] = prev.copy(availableAtMillis = candidate)
            TrackerChestStore.markDirty()
        }
    }

    private fun rememberChest(pos: BlockPos, label: String, tier: Int) {
        val key = pos.asLong()
        val prev = confirmedChests[key]
        val better = prev == null ||
            (prev.label == FALLBACK_CHEST_LABEL && label != FALLBACK_CHEST_LABEL) ||
            (prev.tier == 0 && tier > 0) ||
            (isLockedChest(prev.label) && !isLockedChest(label)) ||
            prev.availableAtMillis > 0L
        if (better) {
            confirmedChests[key] = ChestRecord(label, tier)
            TrackerChestStore.markDirty()
        }
    }

    private fun isLockedChest(raw: String): Boolean = raw.contains("locked", ignoreCase = true)

    private fun observeNode(pos: BlockPos, component: Component?, fallback: String, profession: String) {
        val raw = component?.string ?: fallback
        val cooldownMs = parseCooldownMs(raw)
        if (cooldownMs != null) {
            markNodeCooldown(pos, System.currentTimeMillis() + cooldownMs, profession)
            return
        }
        rememberNode(pos, cleanName(raw), profession)
    }

    private fun markNodeCooldown(pos: BlockPos, availableAtMillis: Long, profession: String) {
        val key = pos.asLong()
        val prev = confirmedNodes[key] ?: NodeRecord(FALLBACK_NODE_LABEL, profession)
        val betterProfession = prev.profession.ifBlank { profession }
        if (prev.availableAtMillis != availableAtMillis || prev.profession != betterProfession) {
            confirmedNodes[key] = prev.copy(availableAtMillis = availableAtMillis, profession = betterProfession)
            TrackerNodeStore.markDirty()
        }
    }

    private fun rememberNode(pos: BlockPos, label: String, profession: String) {
        val key = pos.asLong()
        val prev = confirmedNodes[key]
        val betterProfession = prev?.profession?.ifBlank { profession } ?: profession
        val better = prev == null ||
            (prev.label == FALLBACK_NODE_LABEL && label != FALLBACK_NODE_LABEL) ||
            prev.profession != betterProfession ||
            prev.availableAtMillis > 0L
        if (better) {
            confirmedNodes[key] = NodeRecord(label, betterProfession)
            TrackerNodeStore.markDirty()
        }
    }

    private fun chestOpened(be: BlockEntity): Boolean {
        if (be is LidBlockEntity && be.getOpenNess(1.0f) > OPEN_NESS_THRESHOLD) return true
        val state = be.blockState
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
    }

    private fun ruleMatches(rule: OverwatchConfig.TrackerRule, entity: Entity, name: String): Boolean {
        if (rule.entityTypeId.isNotBlank()) {
            val key = EntityType.getKey(entity.type)
            if (!rule.entityTypeId.equals(key.toString(), ignoreCase = true) &&
                !rule.entityTypeId.equals(key.path, ignoreCase = true)
            ) {
                return false
            }
        }
        return rule.namePattern.isBlank() || nameMatches(rule, name)
    }

    private fun nameMatches(rule: OverwatchConfig.TrackerRule, name: String): Boolean = when (rule.nameMode) {
        "EXACT" -> namePatternEntries(rule.namePattern).any { name.equals(it, ignoreCase = true) }
        "REGEX" -> compiledRegex(rule.namePattern)?.containsMatchIn(name) ?: false
        else -> namePatternEntries(rule.namePattern).any { name.contains(it, ignoreCase = true) }
    }

    private fun namePatternEntries(pattern: String): List<String> =
        pattern.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun componentOf(entity: Entity): Component? {
        if (entity is Display.TextDisplay) {
            runCatching { (entity as TextDisplayAccessor).`overwatch$getText`() }.getOrNull()?.let { return it }
        }
        return entity.customName
    }

    private fun nameOf(entity: Entity): String {
        entity.customName?.let { return it.string }
        if (entity is Display.TextDisplay) {
            runCatching { (entity as TextDisplayAccessor).`overwatch$getText`() }.getOrNull()?.let {
                val text = it.string
                if (text.isNotBlank()) return text
            }
        }
        return entity.name.string
    }

    private fun sanitizeLabel(raw: String): String {
        val name = cleanName(raw)
        val stars = raw.count { it in FILLED_STARS }
        return if (stars in 1..10) "$name [★$stars]" else name
    }

    private class ChestInfo(val name: String, val tier: Int) {
        fun display(): String = if (tier in 1..10) "$name [★$tier]" else name
    }

    private fun chestInfo(component: Component?, fallback: String): ChestInfo {
        val raw = component?.string ?: fallback
        val name = cleanName(raw)
        val tier = exactChestTier(raw)
            ?: legacyStarTier(raw)
            ?: component?.let { litStarTier(it).takeIf { t -> t > 0 } }
            ?: raw.count { it in FILLED_STARS }.takeIf { it in 1..10 }
            ?: 0
        return ChestInfo(name, tier)
    }

    private fun exactChestTier(raw: String): Int? =
        CHEST_TIER_PATTERNS.firstOrNull { (_, pattern) -> pattern.containsMatchIn(raw) }?.first

    private fun legacyStarTier(raw: String): Int? {
        var total = 0
        var lit = 0
        var codesSeen = 0
        for (i in raw.indices) {
            val ch = raw[i]
            if (ch !in FILLED_STARS && ch !in EMPTY_STARS) continue
            total++
            if (i >= 2 && raw[i - 2] == '§' && raw[i - 1].lowercaseChar() in "0123456789abcdef") {
                codesSeen++
                if (raw[i - 1].lowercaseChar() !in "78") lit++
            }
        }
        if (total == 0 || codesSeen == 0) return null
        return lit.coerceIn(1, total)
    }

    private fun litStarTier(text: Component): Int {
        var total = 0
        var lit = 0
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                for (ch in string) {
                    if (ch !in FILLED_STARS && ch !in EMPTY_STARS) continue
                    total++
                    val color = style.color?.value
                    if (color != null && color != GRAY_RGB && color != DARK_GRAY_RGB) lit++
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return when {
            total == 0 -> 0
            lit in 1..total -> lit
            else -> total
        }
    }

    private fun cleanName(raw: String): String {
        val cleaned = buildString {
            for (ch in section.replace(raw, " ")) {
                append(if (ch.isLetterOrDigit() || ch == ' ' || ch == '\'' || ch == '-' || ch == '.') ch else ' ')
            }
        }.replace(ws, " ").trim()

        val kept = ArrayList<String>()
        for (token in cleaned.split(' ')) {
            val tok = token.trim('.', '-', '\'')
            if (tok.isEmpty()) continue
            val digits = tok.count { it.isDigit() }
            val junk = tok.none { it.isLetter() } ||
                (hexish.matches(tok) && digits >= 1) ||
                (digits > 0 && digits * 2 >= tok.length)
            if (junk) {
                if (kept.isNotEmpty()) break else continue
            }
            val trimmed = tok.dropWhile { it.isDigit() }
            kept.add(if (trimmed.length >= 2) trimmed else tok)
        }
        while (kept.size > 1) {
            val last = kept.last().lowercase().trimEnd('.')
            if (last == "lv" || last == "lvl" || last == "level" || last.length <= 1) {
                kept.removeAt(kept.lastIndex)
            } else {
                break
            }
        }

        var name = kept.joinToString(" ")
        if (name.length < 2) name = cleaned.filter { it.isLetter() || it == ' ' }.replace(ws, " ").trim()
        if (name.isBlank()) name = FALLBACK_CHEST_LABEL
        return name
    }

    private fun isNameplate(entity: Entity): Boolean =
        entity is Display || entity is Marker || (entity is ArmorStand && (entity.isMarker || entity.isInvisible))

    private class Redirect(val anchor: Entity?, val box: AABB, val blockPos: BlockPos?)

    private fun redirect(level: ClientLevel, entity: Entity, rule: OverwatchConfig.TrackerRule): Redirect? {
        if (rule.trackBlockBelow || rule.trackGatheringNode) {
            val hit = blockBelow(level, entity)
            if (hit.opened) return null
            return Redirect(entity, hit.box, hit.pos)
        }
        val mob = redirectToMob(level, entity) ?: entity
        return Redirect(mob, mob.boundingBox, null)
    }

    private class BlockHit(val box: AABB, val opened: Boolean, val pos: BlockPos?)

    private fun blockBelow(level: ClientLevel, e: Entity): BlockHit {
        val x = floor(e.x).toInt()
        val z = floor(e.z).toInt()
        val startY = floor(e.y + 0.3).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (dy in 0..BLOCK_SCAN_DOWN) {
            cursor.set(x, startY - dy, z)
            val state = level.getBlockState(cursor)
            if (state.isAir) continue
            val shape = state.getCollisionShape(level, cursor)
            if (shape.isEmpty) continue
            val pos = cursor.immutable()
            val box = shape.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).inflate(0.006)
            val lidOpen = (level.getBlockEntity(pos) as? LidBlockEntity)?.let { it.getOpenNess(1.0f) > OPEN_NESS_THRESHOLD } ?: false
            val stateOpen = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)
            return BlockHit(box, lidOpen || stateOpen, pos)
        }
        val y = e.y - 1.6
        return BlockHit(AABB(x.toDouble(), y, z.toDouble(), x + 1.0, y + 1.0, z + 1.0), opened = false, pos = null)
    }

    private fun redirectToMob(level: ClientLevel, nameplate: Entity): Entity? {
        if (!isNameplate(nameplate)) return null
        val box = AABB(
            nameplate.x - MOB_REDIRECT_RADIUS, nameplate.y - MOB_REDIRECT_DOWN, nameplate.z - MOB_REDIRECT_RADIUS,
            nameplate.x + MOB_REDIRECT_RADIUS, nameplate.y + MOB_REDIRECT_UP, nameplate.z + MOB_REDIRECT_RADIUS,
        )
        return level.getEntities(nameplate, box) {
            it is LivingEntity && it !is ArmorStand && it !is Player && it.isAlive
        }.minByOrNull { it.distanceToSqr(nameplate.x, nameplate.y, nameplate.z) }
    }

    private fun chestKey(pos: BlockPos): UUID = UUID(pos.asLong(), CHEST_KEY_MARKER)

    private fun compiledRegex(pattern: String): Regex? = regexCache.getOrPut(pattern) {
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
    }

    fun pingSound(config: OverwatchConfig): SoundEvent {
        val id = Identifier.tryParse(config.trackerPingSoundId)
        val fromRegistry = id?.let { BuiltInRegistries.SOUND_EVENT.getOptional(it).orElse(null) }
        return fromRegistry ?: SoundEvents.NOTE_BLOCK_PLING.value()
    }

    private fun ping(client: Minecraft, config: OverwatchConfig, newly: List<Match>) {
        if (config.trackerPingSound) {
            client.soundManager.play(
                SimpleSoundInstance.forUI(pingSound(config), config.trackerPingPitch.toFloat(), 0.6f),
            )
        }
        if (config.trackerPingChat) {
            val player = client.player ?: return
            for (match in newly) {
                player.sendSystemMessage(
                    Component.literal("[Overwatch] Tracking: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(match.label).withStyle(ChatFormatting.AQUA)),
                )
            }
        }
    }

    private fun clear() {
        if (current.isNotEmpty()) current = emptyList()
        if (discovered.isNotEmpty()) discovered = emptyList()
        if (EntityTrackerHudState.rows.isNotEmpty()) EntityTrackerHudState.rows = emptyList()
        if (seen.isNotEmpty()) seen.clear()
    }

    private const val FALLBACK_CHEST_LABEL = "Chest"
    private const val FALLBACK_NODE_LABEL = "Gathering node"
    private const val NODE_MERGE_DISTANCE_SQR = 9.0
    private const val MOB_REDIRECT_RADIUS = 2.5
    private const val MOB_REDIRECT_DOWN = 4.0
    private const val MOB_REDIRECT_UP = 1.0
    private const val BLOCK_SCAN_DOWN = 4
    private const val CHEST_SCAN_MAX_RANGE = 96.0
    private const val CHEST_KEY_MARKER = 0x6f77_6368_6573_74L
    private const val OPEN_NESS_THRESHOLD = 0.05f
    private const val SCAN_INTERVAL_NANOS = 200_000_000L
    private const val PREVIEW_INTERVAL_NANOS = 250_000_000L
    private const val LIVE_OPEN_KEEPALIVE_MS = 300_000L
    private const val FILLED_STARS = "★✦✪✫✬✭✮✯⭐✵✶✷"
    private const val EMPTY_STARS = "☆✩✰⭑⭒"
    private const val GRAY_RGB = 0xAAAAAA
    private const val DARK_GRAY_RGB = 0x555555

    private val CHEST_TIER_PATTERNS = listOf(
        1 to Regex("""§7\[§f✫§8✫✫✫§7]"""),
        2 to Regex("""§e\[§6✫✫§8✫✫§e]"""),
        3 to Regex("""§5\[§d✫✫✫§8✫§5]"""),
        4 to Regex("""§3\[§b✫✫✫✫§3]"""),
    )
}
