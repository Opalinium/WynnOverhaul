package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

class OverwatchSettingsPanels(private val host: Host) {

    interface Host {
        val screen: Screen
        val panelFont: Font
        fun rebuildPanels()
        fun installPanelRows(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, w: Int, h: Int)
        fun addPanelWidget(widget: AbstractWidget)
        fun panelContentBottom(): Int
    }

    enum class Group(val label: String) {
        TRACKER("Tracker"),
        HUD("HUD"),
        QOL("QoL"),
        LOOTRUN("Lootrun"),
        DISCORD("Discord"),
        OVERRIDES("Overrides"),
    }

    enum class TrackerTab(val label: String) {
        GENERAL("General"),
        RULES("Rules"),
        DISPLAY("Display"),
        DISCOVERED("Discovered"),
    }

    private val config = OverwatchConfig.current
    private var activeGroup: Group = Group.QOL
    private var activeTrackerTab: TrackerTab = TrackerTab.GENERAL
    private var trackerRulesPage = 0

    private var trackerStatusLabel: OwLabel? = null
    private var trackerRulesStatusLabel: OwLabel? = null

    fun buildTabs(left: Int, w: Int, top: Int): Int {
        trackerStatusLabel = null
        trackerRulesStatusLabel = null

        val sidebarW = Group.entries.maxOf { host.panelFont.width(it.label) } + SIDEBAR_PAD
        drawSidebar(Group.entries, left, top, sidebarW, { it.label }, { it == activeGroup }) {
            activeGroup = it
            host.rebuildPanels()
        }

        val contentLeft = left + sidebarW + SIDEBAR_GAP
        val contentW = w - sidebarW - SIDEBAR_GAP
        var cy = top

        if (activeGroup == Group.TRACKER) {
            cy = drawTabRow(TrackerTab.entries, contentLeft, contentW, cy, { it.label }, { it == activeTrackerTab }, sub = true) {
                activeTrackerTab = it
                host.rebuildPanels()
            }
            cy += 6
        }

        when (activeGroup) {
            Group.TRACKER -> when (activeTrackerTab) {
                TrackerTab.GENERAL -> buildTrackerTab(contentLeft, contentW, cy)
                TrackerTab.RULES -> buildRulesTab(contentLeft, contentW, cy)
                TrackerTab.DISPLAY -> buildDisplayTab(contentLeft, contentW, cy)
                TrackerTab.DISCOVERED -> buildDiscoveredTab(contentLeft, contentW, cy)
            }
            Group.QOL -> buildQolTab(contentLeft, contentW, cy)
            Group.HUD -> buildHudTab(contentLeft, contentW, cy)
            Group.LOOTRUN -> buildLootrunTab(contentLeft, contentW, cy)
            Group.DISCORD -> buildDiscordTab(contentLeft, contentW, cy)
            Group.OVERRIDES -> buildOverridesTab(contentLeft, contentW, cy)
        }
        return cy
    }

    private fun <T> drawSidebar(
        entries: Collection<T>,
        left: Int,
        top: Int,
        w: Int,
        label: (T) -> String,
        selected: (T) -> Boolean,
        onSelect: (T) -> Unit,
    ): Int {
        var ty = top
        for (entry in entries) {
            host.addPanelWidget(
                OwButton(left, ty, w, SIDEBAR_ROW_H, Component.literal(label(entry)), accent = selected(entry)) {
                    onSelect(entry)
                },
            )
            ty += SIDEBAR_ROW_H + 2
        }
        return ty
    }

    fun tick() {
        if (activeGroup == Group.TRACKER && activeTrackerTab == TrackerTab.GENERAL && trackerStatusLabel != null) refreshTrackerStatus()
        if (activeGroup == Group.TRACKER && activeTrackerTab == TrackerTab.RULES && trackerRulesStatusLabel != null) refreshTrackerRulesStatus()
    }

    fun save() {
        config.save()
    }

    private fun <T> drawTabRow(
        entries: Collection<T>,
        left: Int,
        w: Int,
        top: Int,
        label: (T) -> String,
        selected: (T) -> Boolean,
        sub: Boolean = false,
        onSelect: (T) -> Unit,
    ): Int {
        var tx = left + if (sub) 10 else 0
        var ty = top
        val height = if (sub) TAB_H - 2 else TAB_H
        for (entry in entries) {
            val tabWidth = (host.panelFont.width(label(entry)) + 12).coerceAtLeast(28)
            if (tx != left && tx + tabWidth > left + w) {
                tx = left + if (sub) 10 else 0
                ty += height + 2
            }
            host.addPanelWidget(
                OwButton(tx, ty, tabWidth, height, Component.literal(label(entry)), accent = selected(entry)) {
                    onSelect(entry)
                },
            )
            tx += tabWidth + 2
        }
        return ty + height + 2
    }


    private fun buildTrackerTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        val trackerCheckbox = OwCheckbox(left, 0, w, Component.literal("Entity Tracker enabled"), config.trackerEnabled) {
            config.trackerEnabled = it
        }
        trackerCheckbox.setTooltip(Tooltip.create(Component.literal("Master switch. The 'Toggle Entity Tracker' keybind flips this too.")))
        rows += trackerCheckbox to OwTheme.ROW_H

        val scanRangeTooltip = Tooltip.create(Component.literal("How far to actively scan for entities/chests matching your rules, every tick."))
        val scanRangeRows = owNumberField(left, w, host.panelFont, "Scan range", 8.0, 128.0, 0, config.trackerRange) { config.trackerRange = it }
        scanRangeRows.forEach { it.first.setTooltip(scanRangeTooltip) }
        rows += scanRangeRows

        trackerStatusLabel = OwLabel(left, 0, w, OwTheme.ROW_H, "")
        rows += trackerStatusLabel!! to OwTheme.ROW_H
        refreshTrackerStatus()

        rows += OwLabel(left, 0, w, 10, "Use the Rules / Display / Discovered tabs above for more.", OwTheme.TEXT_DIM) to OwTheme.ROW_H

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }

    private fun refreshTrackerStatus() {
        val count = EntityTracker.previewCount(Minecraft.getInstance())
        val state = if (config.trackerEnabled) "on" else "off"
        trackerStatusLabel?.message =
            Component.literal("Tracker $state  ·  $count entit${if (count == 1) "y" else "ies"} match your rules right now")
    }


    private fun buildRulesTab(left: Int, w: Int, top: Int) {
        trackerRulesStatusLabel = OwLabel(left, top, w, 10, "")
        host.addPanelWidget(trackerRulesStatusLabel!!)
        refreshTrackerRulesStatus()
        var y = top + 13
        host.addPanelWidget(OwLabel(left, y, w, 10, "Top row:  on · name(s), comma-separated (blank = any) · entity-or-block · delete", OwTheme.TEXT_DIM))
        y += 11
        host.addPanelWidget(OwLabel(left, y, w, 10, "2nd row:  match mode · type · colour · see-through · label", OwTheme.TEXT_DIM))
        y += 11
        host.addPanelWidget(OwLabel(left, y, w, 10, "3rd row (chest / node rules only):  always show + timer · min tier / profession", OwTheme.TEXT_DIM))
        y += 16

        val rules = config.trackerRules
        val pageCount = maxOf(1, (rules.size + RULES_PER_PAGE - 1) / RULES_PER_PAGE)
        trackerRulesPage = trackerRulesPage.coerceIn(0, pageCount - 1)
        val start = trackerRulesPage * RULES_PER_PAGE
        val end = minOf(rules.size, start + RULES_PER_PAGE)

        for (i in start until end) {
            addRuleRows(rules[i], i, y, left, w)
            y += RULE_BLOCK
        }
        y += 4

        host.addPanelWidget(OwButton(left, y, 96, 18, Component.literal("Add rule")) { addRule(OverwatchConfig.TrackerRule()) })
        val chestButton = OwButton(left + 100, y, 148, 18, Component.literal("Add loot chest rule")) {
            addRule(OverwatchConfig.TrackerRule(nameMode = "CONTAINS", namePattern = "Loot Chest", colorArgb = 0xFFFFD700L, trackBlockBelow = true))
        }
        chestButton.setTooltip(Tooltip.create(Component.literal("Highlights Wynncraft loot chests (the block under a 'Loot Chest [★...]' hologram). Skips ones already opened.")))
        host.addPanelWidget(chestButton)
        val nodeButton = OwButton(left + 252, y, 166, 18, Component.literal("Add gathering node rule")) {
            addRule(OverwatchConfig.TrackerRule(nameMode = "CONTAINS", namePattern = "Mining", colorArgb = 0xFF55FF55L, trackGatheringNode = true, nodeProfession = "MINING"))
        }
        nodeButton.setTooltip(
            Tooltip.create(
                Component.literal(
                    "Highlights a gathering node (mining/woodcutting/farming) by its hologram text. " +
                        "The name pattern is just a starting guess -- edit it to match your server's actual " +
                        "wording, and set the profession tag on the row below. Skips harvested nodes unless " +
                        "'always show' is on.",
                ),
            ),
        )
        host.addPanelWidget(nodeButton)
        y += 22

        if (rules.size > RULES_PER_PAGE) {
            host.addPanelWidget(OwButton(left, y, 46, 18, Component.literal("< Prev")) { trackerRulesPage = (trackerRulesPage - 1).mod(pageCount); host.rebuildPanels() })
            host.addPanelWidget(OwButton(left + 50, y, 46, 18, Component.literal("${trackerRulesPage + 1}/$pageCount")) { })
            host.addPanelWidget(OwButton(left + 100, y, 46, 18, Component.literal("Next >")) { trackerRulesPage = (trackerRulesPage + 1).mod(pageCount); host.rebuildPanels() })
        }
    }

    private fun addRuleRows(rule: OverwatchConfig.TrackerRule, index: Int, top: Int, left: Int, rowWidth: Int) {
        val enabledBox = OwCheckbox(left, top, 20, Component.literal(""), rule.enabled, height = 18) { rule.enabled = it }
        enabledBox.setTooltip(Tooltip.create(Component.literal("Enable / disable this rule")))
        host.addPanelWidget(enabledBox)

        val nameBox = OwTextField(host.panelFont, left + 24, top, rowWidth - 106, 18)
        nameBox.setMaxLength(96)
        nameBox.setValue(rule.namePattern)
        nameBox.setHint(Component.literal("name(s), comma-separated (blank = any name)"))
        nameBox.setResponder { rule.namePattern = it }
        host.addPanelWidget(nameBox)

        val targetButton = OwButton(left + rowWidth - 82, top, 58, 18, Component.literal(targetLabel(rule))) {
            cycleTarget(rule)
            host.rebuildPanels()
        }
        targetButton.setTooltip(
            Tooltip.create(
                Component.literal(
                    "What this rule matches:\n" +
                        "entity = the matched entity itself\n" +
                        "block ↓ = the block under a matching hologram (Wynn loot chests), " +
                        "plus any chest you've been near once (works past hologram range)\n" +
                        "all chests = every chest / barrel in range\n" +
                        "node ↓ = a gathering node (mining/woodcutting/farming) under a " +
                        "matching hologram, remembered the same way as chests\n" +
                        "Looted chests / harvested nodes are skipped unless 'always show' is on.",
                ),
            ),
        )
        host.addPanelWidget(targetButton)

        val deleteButton = OwButton(left + rowWidth - 20, top, 20, 18, Component.literal("X")) {
            config.trackerRules.removeAt(index); host.rebuildPanels()
        }
        deleteButton.setTooltip(Tooltip.create(Component.literal("Delete this rule")))
        host.addPanelWidget(deleteButton)

        val y2 = top + 20
        var x = left + 24
        val modeButton = OwButton(x, y2, 64, 18, Component.literal(MODE_LABELS[rule.nameMode] ?: "contains")) {
            rule.nameMode = NAME_MODES[(NAME_MODES.indexOf(rule.nameMode).coerceAtLeast(0) + 1).mod(NAME_MODES.size)]
            host.rebuildPanels()
        }
        modeButton.setTooltip(
            Tooltip.create(
                Component.literal(
                    "How the name box is matched:\n" +
                        "contains = the name includes this text\n" +
                        "equals = the whole name is exactly this\n" +
                        "regex = a regular expression\n" +
                        "contains/equals accept a comma-separated list -- any entry " +
                        "matching is a match (e.g. \"Zombie, Skeleton, Spider\").\n" +
                        "Leave the name box blank to match any name.",
                ),
            ),
        )
        host.addPanelWidget(modeButton)
        x += 68

        val typeButton = OwButton(x, y2, 84, 18, Component.literal(shortType(rule.entityTypeId))) {
            val opts = (listOf(rule.entityTypeId) + PRESET_TYPES).distinct()
            rule.entityTypeId = opts[(opts.indexOf(rule.entityTypeId).coerceAtLeast(0) + 1).mod(opts.size)]
            host.rebuildPanels()
        }
        typeButton.setTooltip(Tooltip.create(Component.literal("Only match this entity type. Click to cycle common types; 'any type' matches everything.")))
        host.addPanelWidget(typeButton)
        x += 88

        val colorButton = OwButton(x, y2, 40, 18, Component.literal("████").withColor(rule.colorArgb.toInt())) {
            rule.colorArgb = PALETTE[(PALETTE.indexOf(rule.colorArgb).coerceAtLeast(0) + 1).mod(PALETTE.size)]
            host.rebuildPanels()
        }
        colorButton.setTooltip(Tooltip.create(Component.literal("Highlight box + label colour")))
        host.addPanelWidget(colorButton)
        x += 44

        val wallsBox = OwCheckbox(x, y2, 52, Component.literal("walls"), rule.throughWalls, height = 18) { rule.throughWalls = it }
        wallsBox.setTooltip(Tooltip.create(Component.literal("Draw the highlight box through walls")))
        host.addPanelWidget(wallsBox)
        x += 56

        val labelBox = OwTextField(host.panelFont, x, y2, left + rowWidth - x, 18)
        labelBox.setMaxLength(48)
        labelBox.setValue(rule.label)
        labelBox.setHint(Component.literal("label (blank = auto-clean name)"))
        labelBox.setResponder { rule.label = it }
        host.addPanelWidget(labelBox)

        if (rule.trackBlockBelow || rule.trackGatheringNode) {
            val y3 = top + 40
            var x3 = left + 24
            val alwaysShowBox = OwCheckbox(x3, y3, 90, Component.literal("always show"), rule.alwaysDisplay, height = 18) { rule.alwaysDisplay = it }
            alwaysShowBox.setTooltip(
                Tooltip.create(
                    Component.literal(
                        "Keep showing this even while looted/harvested, with a countdown to when " +
                            "it's back. Off (default): hidden until available again.",
                    ),
                ),
            )
            host.addPanelWidget(alwaysShowBox)
            x3 += 90
            if (rule.trackBlockBelow) {
                val tierButton = OwButton(x3, y3, 80, 18, Component.literal(tierLabel(rule))) {
                    rule.minChestTier = (rule.minChestTier + 1).mod(5)
                    host.rebuildPanels()
                }
                tierButton.setTooltip(Tooltip.create(Component.literal("Minimum loot chest star tier to highlight. 'any tier' shows every tier.")))
                host.addPanelWidget(tierButton)
            } else {
                val professionButton = OwButton(x3, y3, 110, 18, Component.literal(professionLabel(rule))) {
                    rule.nodeProfession = NODE_PROFESSIONS[(NODE_PROFESSIONS.indexOf(rule.nodeProfession).coerceAtLeast(0) + 1).mod(NODE_PROFESSIONS.size)]
                    host.rebuildPanels()
                }
                professionButton.setTooltip(
                    Tooltip.create(
                        Component.literal(
                            "Profession tag for this node -- stored with the remembered position and used " +
                                "to group node rules on the Discovered list. Click to cycle.",
                        ),
                    ),
                )
                host.addPanelWidget(professionButton)
            }
        }
    }

    private fun addRule(rule: OverwatchConfig.TrackerRule) {
        config.trackerRules.add(rule)
        trackerRulesPage = (config.trackerRules.size - 1) / RULES_PER_PAGE
        host.rebuildPanels()
    }

    private fun refreshTrackerRulesStatus() {
        val count = EntityTracker.previewCount(Minecraft.getInstance())
        trackerRulesStatusLabel?.message = Component.literal("$count entit${if (count == 1) "y" else "ies"} match right now")
    }

    private fun shortType(id: String): String = when {
        id.isBlank() -> "any type"
        id.startsWith("minecraft:") -> id.removePrefix("minecraft:")
        else -> id
    }

    private fun tierLabel(rule: OverwatchConfig.TrackerRule): String = if (rule.minChestTier <= 0) "any tier" else "T${rule.minChestTier}+"

    private fun professionLabel(rule: OverwatchConfig.TrackerRule): String =
        if (rule.nodeProfession.isBlank()) "any profession" else rule.nodeProfession.lowercase().replaceFirstChar { it.uppercase() }

    private fun targetLabel(rule: OverwatchConfig.TrackerRule): String = when {
        rule.trackGatheringNode -> "node ↓"
        rule.trackAllChests -> "all chests"
        rule.trackBlockBelow -> "block ↓"
        else -> "entity"
    }

    private fun cycleTarget(rule: OverwatchConfig.TrackerRule) {
        when {
            rule.trackGatheringNode -> { rule.trackGatheringNode = false }
            rule.trackAllChests -> { rule.trackBlockBelow = false; rule.trackAllChests = false; rule.trackGatheringNode = true }
            rule.trackBlockBelow -> { rule.trackAllChests = true }
            else -> { rule.trackBlockBelow = true }
        }
    }


    private fun buildDisplayTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun checkbox(label: String, tooltip: String, selected: Boolean, onChange: (Boolean) -> Unit) {
            val box = OwCheckbox(left, 0, w, Component.literal(label), selected, onChange = onChange)
            if (tooltip.isNotEmpty()) box.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += box to OwTheme.ROW_H
        }

        fun header(label: String) {
            rows += OwSectionHeader(left, 0, w, label) to 18
        }

        fun slider(label: String, min: Double, max: Double, decimals: Int, initial: Double, tooltip: String, onChange: (Double) -> Unit) {
            val s = OwSlider(left, 0, w, OwTheme.ROW_H - 2, min, max, decimals, initial, label, onChange)
            if (tooltip.isNotEmpty()) s.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += s to OwTheme.ROW_H
        }

        fun cycleButton(labelFor: () -> String, tooltip: String, onPress: () -> Unit) {
            val button = OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal(labelFor())) {
                onPress()
                host.rebuildPanels()
            }
            if (tooltip.isNotEmpty()) button.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += button to OwTheme.ROW_H
        }

        checkbox(
            "Draw waypoints",
            "Xaero-style marker on each match: a coloured icon with the initial, plus name + distance " +
                "boxes when you look near it, or an arrow pointing the way when it's off screen or behind you. " +
                "Turn off for HUD list + ping only.",
            config.trackerWaypointsEnabled,
        ) { config.trackerWaypointsEnabled = it }
        slider("Waypoint scale", 0.5, 2.5, 2, config.trackerWaypointScale, "Size of the on-screen waypoint icons, labels and arrows.") {
            config.trackerWaypointScale = it
        }

        header("New-Match Alerts")
        checkbox("Chat message on new match", "", config.trackerPingChat) { config.trackerPingChat = it }
        checkbox("Sound on new match", "", config.trackerPingSound) { config.trackerPingSound = it }
        cycleButton({ "Ping sound: ${soundLabel()}" }, "Sound played when an entity starts matching. Click to cycle (plays a preview).") {
            val i = SOUND_PRESETS.indexOfFirst { it.second == config.trackerPingSoundId }
            config.trackerPingSoundId = SOUND_PRESETS[(i + 1).mod(SOUND_PRESETS.size)].second
            previewTrackerSound()
        }
        slider("Ping pitch", 0.5, 2.0, 2, config.trackerPingPitch, "Pitch of the new-match ping sound.") { config.trackerPingPitch = it }

        header("Tracked List HUD")
        checkbox("Show distance in HUD list", "", config.trackerHudShowDistance) { config.trackerHudShowDistance = it }

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }

    private fun previewTrackerSound() {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(EntityTracker.pingSound(config), config.trackerPingPitch.toFloat(), 0.6f),
        )
    }

    private fun soundLabel(): String = SOUND_PRESETS.firstOrNull { it.second == config.trackerPingSoundId }?.first ?: config.trackerPingSoundId


    private fun buildDiscoveredTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        rows += OwSectionHeader(left, 0, w, "Discovered Chests") to 18
        val chestCheckbox = OwCheckbox(left, 0, w, Component.literal("Show already-discovered chests"), config.trackerDiscoveredChestsEnabled) {
            config.trackerDiscoveredChestsEnabled = it
        }
        chestCheckbox.setTooltip(
            Tooltip.create(
                Component.literal(
                    "Show every chest you've already found (matching an enabled chest rule) far beyond " +
                        "the scan range -- their positions are already known, so this is just a HUD " +
                        "marker, not a re-scan.",
                ),
            ),
        )
        rows += chestCheckbox to OwTheme.ROW_H

        val chestRangeTooltip = Tooltip.create(Component.literal("How far away an already-discovered chest still counts."))
        val chestRangeRows = owNumberField(left, w, host.panelFont, "Discovered chest range", 64.0, 4000.0, 0, config.trackerDiscoveredChestRange) {
            config.trackerDiscoveredChestRange = it
        }
        chestRangeRows.forEach { it.first.setTooltip(chestRangeTooltip) }
        rows += chestRangeRows

        val chestGuidanceTooltip = Tooltip.create(
            Component.literal("How close a discovered chest needs to be before it gets an on-screen marker. Farther than this it's list-only -- capped to the range above."),
        )
        val chestGuidanceRows = owNumberField(left, w, host.panelFont, "Guidance range", 8.0, 256.0, 0, config.trackerDiscoveredChestGuidanceRange) {
            config.trackerDiscoveredChestGuidanceRange = it
        }
        chestGuidanceRows.forEach { it.first.setTooltip(chestGuidanceTooltip) }
        rows += chestGuidanceRows

        rows += OwSectionHeader(left, 0, w, "Discovered Gathering Nodes") to 18
        val nodeCheckbox = OwCheckbox(left, 0, w, Component.literal("Show already-discovered nodes"), config.trackerDiscoveredNodesEnabled) {
            config.trackerDiscoveredNodesEnabled = it
        }
        nodeCheckbox.setTooltip(
            Tooltip.create(
                Component.literal(
                    "Show every gathering node you've already found (matching an enabled node rule) far " +
                        "beyond the scan range -- same idea as discovered chests, just for nodes. Nodes " +
                        "have no live re-check the way chests do, so this trusts the last hologram reading.",
                ),
            ),
        )
        rows += nodeCheckbox to OwTheme.ROW_H

        val nodeRangeTooltip = Tooltip.create(Component.literal("How far away an already-discovered node still counts."))
        val nodeRangeRows = owNumberField(left, w, host.panelFont, "Discovered node range", 64.0, 4000.0, 0, config.trackerDiscoveredNodeRange) {
            config.trackerDiscoveredNodeRange = it
        }
        nodeRangeRows.forEach { it.first.setTooltip(nodeRangeTooltip) }
        rows += nodeRangeRows

        val nodeGuidanceTooltip = Tooltip.create(
            Component.literal("How close a discovered node needs to be before it gets an on-screen marker. Farther than this it's list-only -- capped to the range above."),
        )
        val nodeGuidanceRows = owNumberField(left, w, host.panelFont, "Guidance range", 8.0, 256.0, 0, config.trackerDiscoveredNodeGuidanceRange) {
            config.trackerDiscoveredNodeGuidanceRange = it
        }
        nodeGuidanceRows.forEach { it.first.setTooltip(nodeGuidanceTooltip) }
        rows += nodeGuidanceRows

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }


    private fun buildQolTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun checkbox(label: String, tooltip: String, selected: Boolean, onChange: (Boolean) -> Unit) {
            val box = OwCheckbox(left, 0, w, Component.literal(label), selected, onChange = onChange)
            if (tooltip.isNotEmpty()) box.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += box to OwTheme.ROW_H
        }

        fun header(label: String) {
            rows += OwSectionHeader(left, 0, w, label) to 18
        }

        fun cycleButton(labelFor: () -> String, tooltip: String, onPress: () -> Unit) {
            val button = OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal(labelFor())) {
                onPress()
                host.rebuildPanels()
            }
            button.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += button to OwTheme.ROW_H
        }

        header("Combat")
        checkbox(
            "Hold to attack",
            "Keeps swinging while attack is held, paced to your weapon's attack speed. The 'Toggle Hold-to-Attack' keybind flips this too.",
            config.enabled,
        ) { config.enabled = it }

        checkbox(
            "Prevent hotbar overscroll",
            "Scrolling the hotbar past slot 9 or slot 1 stops there instead of looping around to the other end.",
            config.qolPreventHotbarOverscroll,
        ) { config.qolPreventHotbarOverscroll = it }

        header("Nametags")
        checkbox(
            "Highlight party & friends",
            "Prefixes other players' nametags with [Party]/[Friend] when they're in your current party or friends list (read from \"party list\"/\"friend list\").",
            config.customPartyNametagsEnabled,
        ) { config.customPartyNametagsEnabled = it }

        header("Mounts")
        checkbox(
            "Feeding info on item tooltips",
            "Appends the optimal feeding shopping list to a hovered mount saddle/whistle's tooltip.",
            config.mountTooltipEnabled,
        ) { config.mountTooltipEnabled = it }
        checkbox(
            "Feeder stable HUD",
            "Shows a full feeding breakdown panel while the Mount Feeder menu is open and a mount is hovered.",
            config.mountFeederHudEnabled,
        ) { config.mountFeederHudEnabled = it }
        checkbox(
            "Mount pickup debug logging",
            "Logs mounted action-bar traffic with pickup-like content to latest.log (escaped, readable). Turn on, ride through pickups, then share the MountPickupDebug lines.",
            config.mountPickupDebug,
        ) { config.mountPickupDebug = it }

        header("Inventory")
        checkbox("Custom inventory screen", "New World-styled categorized inventory replacing the vanilla survival inventory.", config.customInventoryEnabled) {
            config.customInventoryEnabled = it
        }

        header("Debug")
        checkbox(
            "Middle-click copies item data",
            "Middle-click a hovered item in a custom Overwatch screen to copy its registry id, name and full data components to the clipboard -- for matching server menu items during development.",
            config.debugItemCopyEnabled,
        ) { config.debugItemCopyEnabled = it }

        header("Rare Item Alert")
        checkbox(
            "Alert on rare item obtained",
            "Sound/chat alert when an item at or above the rarity below shows up in your inventory. Reads the item's Wynncraft rarity straight off its name colour.",
            config.mythicAlertEnabled,
        ) { config.mythicAlertEnabled = it }
        cycleButton({ "Minimum rarity: ${rarityLabel()}" }, "Click to cycle: Normal / Unique / Rare / Legendary / Fabled / Mythic.") {
            val opts = WynnRarity.entries
            val i = opts.indexOfFirst { it.name == config.mythicAlertMinRarity }.coerceAtLeast(0)
            config.mythicAlertMinRarity = opts[(i + 1).mod(opts.size)].name
        }
        checkbox("Play sound", "", config.mythicAlertSound) { config.mythicAlertSound = it }
        checkbox("Show chat message", "", config.mythicAlertChat) { config.mythicAlertChat = it }

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }

    private fun rarityLabel(): String = WynnRarity.entries.firstOrNull { it.name == config.mythicAlertMinRarity }?.displayName ?: "Mythic"


    private fun buildHudTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun checkbox(label: String, tooltip: String, selected: Boolean, onChange: (Boolean) -> Unit) {
            val box = OwCheckbox(left, 0, w, Component.literal(label), selected, onChange = onChange)
            if (tooltip.isNotEmpty()) box.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += box to OwTheme.ROW_H
        }

        fun header(label: String) {
            rows += OwSectionHeader(left, 0, w, label) to 18
        }

        fun cycleButton(labelFor: () -> String, tooltip: String, onPress: () -> Unit) {
            val button = OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal(labelFor())) {
                onPress()
                host.rebuildPanels()
            }
            button.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += button to OwTheme.ROW_H
        }

        checkbox(
            "Custom HUD",
            "One switch for the whole New World-styled HUD: health/mana, sprint (mount energy while riding, plus mount pickup announcements), experience and class resource bars, spell cast/combo indicators, NPC dialogue, plus the draggable hotbar, parsed from the real action bar and boss bars. Also hides the vanilla overlay text, hotbar and hunger bar they replace.",
            config.customHudEnabled,
        ) { config.customHudEnabled = it }
        cycleButton({ "Customize HUD layout..." }, "Drag, resize and lock any HUD element -- opens the HUD designer.") {
            Minecraft.getInstance().setScreenAndShow(HudDesignerScreen(host.screen))
        }

        header("Potion Effects")
        checkbox("Hide vanilla effect icons", "Hides the top-right vanilla potion effect icon HUD.", config.hideVanillaPotionHud) {
            config.hideVanillaPotionHud = it
        }
        checkbox(
            "Show custom effect HUD",
            "Shows active effects as an icon + name + remaining-time list instead.",
            config.customPotionHudEnabled,
        ) { config.customPotionHudEnabled = it }

        header("Ability Cooldowns")
        checkbox(
            "Show ability cooldown HUD",
            "Shows active class/archetype ability cooldowns (read from the tab-list \"Status Effects\" listing) as a name + timer + bar per ability.",
            config.abilityCooldownHudEnabled,
        ) { config.abilityCooldownHudEnabled = it }

        header("Quest Log")
        checkbox(
            "Show custom quest log",
            "Replaces the vanilla scoreboard sidebar (tracked quest, daily/weekly objectives) with a draggable panel. Also hides the vanilla sidebar it replaces.",
            config.questLogHudEnabled,
        ) { config.questLogHudEnabled = it }

        header("Bars")
        cycleButton({ "Bar style: ${HudBars.styleLabel(config.hudBarStyle)}" }, "Look shared by the health, mana, sprint, experience, class resource and mount energy bars. Height comes from the HUD designer: drag a bar's corner handle taller or shorter.") {
            val i = HudBars.STYLES.indexOf(config.hudBarStyle).coerceAtLeast(0)
            config.hudBarStyle = HudBars.STYLES[(i + 1).mod(HudBars.STYLES.size)]
        }

        header("Panels")
        checkbox(
            "Panel backgrounds",
            "Draws dark panels behind HUD text. Off is the New World style: floating shadowed text with no panels (bars keep their tracks).",
            config.hudPanelsEnabled,
        ) { config.hudPanelsEnabled = it }

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }


    private fun buildLootrunTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun checkbox(label: String, tooltip: String, selected: Boolean, onChange: (Boolean) -> Unit) {
            val box = OwCheckbox(left, 0, w, Component.literal(label), selected, onChange = onChange)
            if (tooltip.isNotEmpty()) box.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += box to OwTheme.ROW_H
        }

        checkbox("Enabled", "Master switch for all lootrun features below.", config.lootrunEnabled) { config.lootrunEnabled = it }
        checkbox(
            "Chest beacon markers",
            "Colour-codes real beacon entities (chests/tasks) while a lootrun is active.",
            config.lootrunBeaconsEnabled,
        ) { config.lootrunBeaconsEnabled = it }
        checkbox(
            "Task location marker",
            "Decodes Wynncraft's real firework-particle circle burst into a marker at the current task's centre.",
            config.lootrunTaskMarkerEnabled,
        ) { config.lootrunTaskMarkerEnabled = it }
        checkbox("State HUD", "Timer / challenges / current task, read from the real scoreboard.", config.lootrunHudEnabled) {
            config.lootrunHudEnabled = it
        }
        checkbox(
            "Record my own runs",
            "While a lootrun is active, records your own walked route so it can be replayed as a guide line next time.",
            config.lootrunRecorderEnabled,
        ) { config.lootrunRecorderEnabled = it }

        rows += OwSectionHeader(left, 0, w, "Saved Paths") to 18

        val active = LootrunRecorder.activePath
        if (active != null) {
            val stopButton = OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("Following: ${active.name}  (click to stop)"), accent = true) {
                LootrunRecorder.activePath = null
                host.rebuildPanels()
            }
            rows += stopButton to OwTheme.ROW_H
        }

        val paths = LootrunPathStore.list()
        if (paths.isEmpty()) {
            rows += OwLabel(left, 0, w, OwTheme.ROW_H, "No saved runs yet.", OwTheme.TEXT_DIM) to OwTheme.ROW_H
        }
        for (path in paths) {
            val useWidth = w - 44
            val useButton = OwButton(left, 0, useWidth, OwTheme.ROW_H - 2, Component.literal("${path.name}  (${path.points.size} pts)")) {
                LootrunRecorder.activePath = path
                host.rebuildPanels()
            }
            rows += useButton to 0
            val deleteButton = OwButton(left + useWidth + 4, 0, 40, OwTheme.ROW_H - 2, Component.literal("Del")) {
                if (LootrunRecorder.activePath?.name == path.name) LootrunRecorder.activePath = null
                LootrunPathStore.delete(path.name)
                host.rebuildPanels()
            }
            rows += deleteButton to OwTheme.ROW_H
        }

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }


    private fun buildDiscordTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        val rpcCheckbox = OwCheckbox(left, 0, w, Component.literal("Enable Discord Rich Presence"), config.discordRpcEnabled) {
            config.discordRpcEnabled = it
        }
        rpcCheckbox.setTooltip(Tooltip.create(Component.literal("Connects to your local Discord client over IPC and shows a status card. Nothing is sent anywhere but Discord.")))
        rows += rpcCheckbox to OwTheme.ROW_H

        val activityCheckbox = OwCheckbox(left, 0, w, Component.literal("Show current activity"), config.discordShowActivity) {
            config.discordShowActivity = it
        }
        activityCheckbox.setTooltip(Tooltip.create(Component.literal("What you're doing right now -- on a lootrun, your tracked quest, or just adventuring.")))
        rows += activityCheckbox to OwTheme.ROW_H

        val levelCheckbox = OwCheckbox(left, 0, w, Component.literal("Show combat level"), config.discordShowLevel) {
            config.discordShowLevel = it
        }
        levelCheckbox.setTooltip(Tooltip.create(Component.literal("Your character's combat level, read live off the in-game HUD.")))
        rows += levelCheckbox to OwTheme.ROW_H

        val regionCheckbox = OwCheckbox(left, 0, w, Component.literal("Show current region"), config.discordShowRegion) {
            config.discordShowRegion = it
        }
        regionCheckbox.setTooltip(
            Tooltip.create(
                Component.literal(
                    "The Wynncraft territory closest to your position, from Wynncraft's own public territory API " +
                        "(api.wynncraft.com) -- an approximation, not an exact zone name.",
                ),
            ),
        )
        rows += regionCheckbox to OwTheme.ROW_H

        val info = listOf(
            "Requires the Discord desktop app to be running.",
            "Nothing is sent anywhere but your own local Discord client.",
        )
        for (line in info) rows += OwLabel(left, 0, w, 10, line, OwTheme.TEXT_DIM) to 11

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }


    private fun buildOverridesTab(left: Int, w: Int, top: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        val contentBookCheckbox = OwCheckbox(left, 0, w, Component.literal("Override Wynncraft's Content Book"), config.contentBookOverrideEnabled) {
            config.contentBookOverrideEnabled = it
        }
        contentBookCheckbox.setTooltip(Tooltip.create(Component.literal("Right-clicking the Content Book item opens Overwatch's own menu instead of Wynncraft's.")))
        rows += contentBookCheckbox to OwTheme.ROW_H

        val waypointCheckbox = OwCheckbox(left, 0, w, Component.literal("Override quest waypoints"), config.questWaypointOverrideEnabled) {
            config.questWaypointOverrideEnabled = it
        }
        waypointCheckbox.setTooltip(
            Tooltip.create(
                Component.literal(
                    "Renders Overwatch's own waypoint pointer at Wynncraft's real quest marker location, " +
                        "instead of relying on Wynncraft's own render-distance-limited beam.",
                ),
            ),
        )
        rows += waypointCheckbox to OwTheme.ROW_H

        val voxyCheckbox = OwCheckbox(left, 0, w, Component.literal("Voxy Vista (hide LODs outside Wynncraft)"), config.voxyVistaEnabled) {
            config.voxyVistaEnabled = it
        }
        voxyCheckbox.setTooltip(
            Tooltip.create(
                Component.literal(
                    "If Voxy is installed, disables its rendering (via Voxy's own enableRendering toggle) while " +
                        "you're outside Wynncraft's own map bounds -- no LOD data to see there, so this avoids the wasted cost.",
                ),
            ),
        )
        rows += voxyCheckbox to OwTheme.ROW_H

        val questToastCheckbox = OwCheckbox(left, 0, w, Component.literal("Quest completion as toast"), config.questCompletionToastEnabled) {
            config.questCompletionToastEnabled = it
        }
        questToastCheckbox.setTooltip(Tooltip.create(Component.literal("Blocks the \"[Quest Completed]\" chat message and shows a HUD toast instead.")))
        rows += questToastCheckbox to OwTheme.ROW_H

        val levelUpToastCheckbox = OwCheckbox(left, 0, w, Component.literal("Level up as toast"), config.levelUpToastEnabled) {
            config.levelUpToastEnabled = it
        }
        levelUpToastCheckbox.setTooltip(Tooltip.create(Component.literal("Blocks level-up chat messages and shows a HUD toast instead.")))
        rows += levelUpToastCheckbox to OwTheme.ROW_H

        rows += OwSectionHeader(left, 0, w, "Reference Tools") to 18
        rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("Quest Reference (wiki)")) {
            Minecraft.getInstance().setScreenAndShow(OverwatchQuestBookScreen(host.screen))
        } to OwTheme.ROW_H
        rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("Powder Guide")) {
            Minecraft.getInstance().setScreenAndShow(OverwatchPowderGuideScreen(host.screen))
        } to OwTheme.ROW_H

        host.installPanelRows(rows, left, top, w, host.panelContentBottom() - top)
    }

    private companion object {
        const val TAB_H = 16
        const val SIDEBAR_ROW_H = 20
        const val SIDEBAR_PAD = 16
        const val SIDEBAR_GAP = 8
        const val RULES_PER_PAGE = 4
        const val RULE_BLOCK = 64
        val NAME_MODES = listOf("CONTAINS", "EXACT", "REGEX")
        val MODE_LABELS = mapOf("CONTAINS" to "contains", "EXACT" to "equals", "REGEX" to "regex")
        val NODE_PROFESSIONS = listOf("", "MINING", "WOODCUTTING", "FARMING")
        val PRESET_TYPES = listOf(
            "",
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:enderman",
            "minecraft:player",
            "minecraft:villager",
            "minecraft:armor_stand",
            "minecraft:text_display",
            "minecraft:item",
        )
        val PALETTE = listOf(
            0xFFFF5555L, 0xFFFFAA00L, 0xFFFFFF55L, 0xFF55FF55L,
            0xFF55FFFFL, 0xFF5555FFL, 0xFFFF55FFL, 0xFFFFFFFFL,
        )
        val SOUND_PRESETS = listOf(
            "Pling" to "minecraft:block.note_block.pling",
            "Bell" to "minecraft:block.note_block.bell",
            "Chime" to "minecraft:block.amethyst_block.chime",
            "Harp" to "minecraft:block.note_block.harp",
            "Bit" to "minecraft:block.note_block.bit",
            "Didgeridoo" to "minecraft:block.note_block.didgeridoo",
            "XP orb" to "minecraft:entity.experience_orb.pickup",
            "Level up" to "minecraft:entity.player.levelup",
            "Arrow hit" to "minecraft:entity.arrow.hit_player",
            "Dispenser" to "minecraft:block.dispenser.dispense",
        )
    }
}
