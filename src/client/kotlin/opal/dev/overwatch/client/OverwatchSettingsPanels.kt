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
        HUD("HUD"),
        NOTIFICATIONS("Notifications"),
        TRACKER("Tracker"),
        ANIMATIONS("Animations"),
        CAMERA("Camera"),
        QOL("QoL"),
        LOOTRUN("Lootrun"),
        DISCORD("Discord"),
        OVERRIDES("Advanced"),
    }

    enum class NotificationTab(val label: String) {
        TOASTS("Toasts"),
        ALERTS("Alerts"),
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
    private var activeNotificationTab: NotificationTab = NotificationTab.TOASTS
    private var activeToastKind: OverwatchToastQueue.Kind = OverwatchToastQueue.Kind.QUEST

    val animationsActive: Boolean get() = activeGroup == Group.ANIMATIONS

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

        if (activeGroup == Group.NOTIFICATIONS) {
            cy = drawTabRow(NotificationTab.entries, contentLeft, contentW, cy, { it.label }, { it == activeNotificationTab }) {
                activeNotificationTab = it
                host.rebuildPanels()
            }
            if (activeNotificationTab == NotificationTab.TOASTS) {
                cy = drawTabRow(OverwatchToastQueue.Kind.entries, contentLeft, contentW, cy, { it.label }, { it == activeToastKind }, sub = true) {
                    activeToastKind = it
                    host.rebuildPanels()
                }
            }
            cy += 6
        }

        when (activeGroup) {
            Group.NOTIFICATIONS -> when (activeNotificationTab) {
                NotificationTab.TOASTS -> buildToastsTab(contentLeft, contentW, cy)
                NotificationTab.ALERTS -> buildAlertsTab(contentLeft, contentW, cy)
            }
            Group.TRACKER -> when (activeTrackerTab) {
                TrackerTab.GENERAL -> buildTrackerTab(contentLeft, contentW, cy)
                TrackerTab.RULES -> buildRulesTab(contentLeft, contentW, cy)
                TrackerTab.DISPLAY -> buildDisplayTab(contentLeft, contentW, cy)
                TrackerTab.DISCOVERED -> buildDiscoveredTab(contentLeft, contentW, cy)
            }
            Group.QOL -> buildQolTab(contentLeft, contentW, cy)
            Group.ANIMATIONS -> buildAnimationsTab(contentLeft, contentW, cy)
            Group.CAMERA -> buildCameraTab(contentLeft, contentW, cy)
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

    private inner class PanelScope(val left: Int, val w: Int) {
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        private fun <T : AbstractWidget> add(widget: T, tooltip: String, height: Int = OwTheme.ROW_H): T {
            if (tooltip.isNotEmpty()) widget.setTooltip(Tooltip.create(Component.literal(tooltip)))
            rows += widget to height
            return widget
        }

        fun checkbox(label: String, tooltip: String, selected: Boolean, onChange: (Boolean) -> Unit) {
            add(OwCheckbox(left, 0, w, Component.literal(label), selected, onChange = onChange), tooltip)
        }

        fun header(label: String) {
            rows += OwSectionHeader(left, 0, w, label) to 18
        }

        fun slider(label: String, min: Double, max: Double, decimals: Int, initial: Double, tooltip: String, onChange: (Double) -> Unit) {
            add(OwSlider(left, 0, w, OwTheme.ROW_H - 2, min, max, decimals, initial, label, onChange), tooltip)
        }

        fun slider(label: String, min: Double, max: Double, initial: Double, tooltip: String, onChange: (Double) -> Unit) {
            slider(label, min, max, 2, initial, tooltip, onChange)
        }

        fun button(label: String, tooltip: String, onPress: () -> Unit) {
            add(
                OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal(label)) {
                    onPress()
                    host.rebuildPanels()
                },
                tooltip,
            )
        }

        fun cycleButton(labelFor: () -> String, tooltip: String, onPress: () -> Unit) = button(labelFor(), tooltip, onPress)
    }

    private fun panel(left: Int, w: Int, top: Int, build: PanelScope.() -> Unit) {
        val scope = PanelScope(left, w)
        scope.build()
        host.installPanelRows(scope.rows, left, top, w, host.panelContentBottom() - top)
    }

    private fun buildTrackerTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
        }
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

        val colorButton = OwButton(x, y2, 40, 18, Component.literal("Colour"), swatch = rule.colorArgb.toInt()) {
            val current = PALETTE.indexOf(rule.colorArgb)
            rule.colorArgb = PALETTE[if (current < 0) 0 else (current + 1).mod(PALETTE.size)]
            OverwatchConfig.current.save()
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
        panel(left, w, top) {
            checkbox(
                "Draw waypoints",
                "Marker on each match: a ringed badge with the real item icon (chest, tool, spawn egg, book...), plus " +
                    "a name + distance card when you look near it, or an arrow pointing the way when it's off screen or behind you. " +
                    "Turn off for HUD list + ping only.",
                config.trackerWaypointsEnabled,
            ) { config.trackerWaypointsEnabled = it }
            slider("Waypoint scale", 0.5, 2.5, 2, config.trackerWaypointScale, "Size of the on-screen waypoint icons, labels and arrows.") {
                config.trackerWaypointScale = it
            }

            rows += OwLabel(left, 0, w, 10, "Ping sound and chat alerts are under Notifications > Alerts.", OwTheme.TEXT_DIM) to OwTheme.ROW_H

            header("Tracked List HUD")
            checkbox("Show distance in HUD list", "", config.trackerHudShowDistance) { config.trackerHudShowDistance = it }
        }
    }

    private fun previewTrackerSound() {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(EntityTracker.pingSound(config), config.trackerPingPitch.toFloat(), 0.6f),
        )
    }

    private fun soundLabel(): String = NotificationSounds.label(config.trackerPingSoundId)

    private fun buildDiscoveredTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
        }
    }

    private fun buildCameraTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
            rows += OwSectionHeader(left, 0, w, "Souls-style Camera") to 18
            checkbox(
                "Enable free orbit camera",
                "Third-person camera that orbits your character independently of where you face. WASD moves relative to the camera and your character turns to run in that direction. Attacking and casting turn you toward the crosshair. Active in the third-person back view (F5); bind a key in Controls to toggle it.",
                config.soulsCameraEnabled,
            ) { config.soulsCameraEnabled = it }
            checkbox(
                "Show reticle",
                "Draws the crosshair in third person so you can see what your attacks will target.",
                config.soulsCameraReticle,
            ) { config.soulsCameraReticle = it }
            slider("Camera distance", 1.5, 12.0, config.soulsCameraDistance, "How far behind your character the camera sits.") { config.soulsCameraDistance = it }
            slider("Camera height", -1.0, 2.0, config.soulsCameraHeight, "Raises or lowers the point the camera orbits, relative to your eyes.") { config.soulsCameraHeight = it }
            slider("Shoulder offset", -1.5, 1.5, config.soulsCameraShoulder, "Shifts the camera sideways. Positive is over the right shoulder.") { config.soulsCameraShoulder = it }
            slider("Look sensitivity", 0.2, 3.0, config.soulsCameraSensitivity, "Multiplier on top of your normal mouse sensitivity, camera only.") { config.soulsCameraSensitivity = it }
            slider("Camera follow smoothing", 0.0, 1.0, config.soulsCameraSmoothing, "Lets the camera trail slightly behind your movement. 0 is rigid.") { config.soulsCameraSmoothing = it }
            slider("Character turn speed", 0.15, 1.0, config.soulsCameraTurnSpeed, "How quickly your character swings to face the way you run. 1 is instant.") { config.soulsCameraTurnSpeed = it }
            slider("Aim hold time (ms)", 0.0, 2000.0, config.soulsCameraFaceHoldMs, "How long your character keeps facing the crosshair after an attack or spell click before turning back to run. Raise it if spell combos get interrupted.") { config.soulsCameraFaceHoldMs = it }
        }
    }

    private fun buildAnimationsTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
            header("Movement Animations")
            checkbox(
                "Movement animations",
                "Restyles how player bodies move: walking, sprinting, jumping, landing, crouching, swimming, climbing, riding, gliding, eating, blocking, getting hurt and dying. Layers on top of vanilla and never overrides an active weapon animation on your arms.",
                config.locomotionEnabled,
            ) { config.locomotionEnabled = it }
            cycleButton({ "Style: " + LocomotionAnimations.labelOf(config.locomotionStyle) }, "Heroic: broad, powerful strides. Stealth: low and quiet. Lightfoot: springy and bouncy. Heavy: weighty stomps and slow sway. Weary: slumped and dragging.") {
                config.locomotionStyle = LocomotionAnimations.nextStyle(config.locomotionStyle)
            }
            checkbox("Apply to other players", "Also restyles every other player you can see, not just you.", config.locomotionOtherPlayers) { config.locomotionOtherPlayers = it }
            checkbox("Randomize other players' styles", "Each other player gets one of the styles based on their identity, so a crowd does not move in lockstep. Turn off to give everyone your selected style.", config.locomotionRandomizeOthers) { config.locomotionRandomizeOthers = it }
            checkbox("Joint bending", "Bends arms at the elbow and legs at the knee instead of swinging them as stiff blocks. Skin, sleeves, pants and armor bend together.", config.locomotionBend) { config.locomotionBend = it }
            checkbox("Walk and sprint", "Stride length, body bob, sway and forward lean while moving.", config.locomotionWalk) { config.locomotionWalk = it }
            checkbox("Jump, fall and landing", "Arms and legs react to leaping and falling, with a knee-bending impact when you land from a height.", config.locomotionJump) { config.locomotionJump = it }
            checkbox("Crouch", "Adds a deeper hunch, tucked arms and bent knees to sneaking.", config.locomotionCrouch) { config.locomotionCrouch = it }
            checkbox("Swimming and treading", "Flutter kick while swimming and a sculling motion while treading water.", config.locomotionSwim) { config.locomotionSwim = it }
            checkbox("Climbing", "Hand-over-hand reach and stepping on ladders and vines.", config.locomotionClimb) { config.locomotionClimb = it }
            checkbox("Riding", "Legs spread astride mounts and boats, hands forward on the reins.", config.locomotionRide) { config.locomotionRide = it }
            checkbox("Elytra gliding", "Arms swept back and legs trailing while gliding.", config.locomotionElytra) { config.locomotionElytra = it }
            checkbox("Eating, drinking and blocking", "Head chews while eating or drinking, and you brace back while blocking.", config.locomotionUseItem) { config.locomotionUseItem = it }
            checkbox("Hurt reaction", "Recoil and arm flail when taking damage.", config.locomotionHurt) { config.locomotionHurt = it }
            checkbox("Death", "Limbs go slack and spread as a player falls.", config.locomotionDeath) { config.locomotionDeath = it }

            header("Weapon Animations")
            checkbox(
                "Weapon attack animations",
                "Replaces your swing with a per-weapon animation (spear thrust, dagger slash, wand cast, relik sweep, bow draw) in first and third person. Cosmetic only, and only affects your own character.",
                config.weaponAnimationsEnabled,
            ) { config.weaponAnimationsEnabled = it }
            checkbox(
                "Weapon idle stance",
                "Replaces the vanilla arm pose with a per-weapon hold (two-handed grip for spears, staves and firearms, guard raised after you attack, slight breathing sway) so swings ease in and out of it. Needs weapon attack animations on.",
                config.weaponIdleEnabled,
            ) { config.weaponIdleEnabled = it }
            checkbox(
                "True idle pose",
                "After you stand still for a while, the weapon drops into a relaxed rest pose (spears and staves planted at your side, blades lowered) with slower, deeper breathing and a gentle weight shift. Needs weapon idle stance on.",
                config.weaponTrueIdleEnabled,
            ) { config.weaponTrueIdleEnabled = it }
            val idleDelaySlider = OwSlider(left, 0, w, OwTheme.ROW_H - 2, 0.5, 15.0, 1, config.weaponTrueIdleDelaySeconds, "True idle delay (s)") { config.weaponTrueIdleDelaySeconds = it }
            idleDelaySlider.setTooltip(Tooltip.create(Component.literal("How long you must stand still, after your last attack, before the rest pose starts.")))
            rows += idleDelaySlider to OwTheme.ROW_H
            checkbox(
                "Sprint stance",
                "While sprinting, you lean into the run and carry the weapon in a running pose: spears and staves held level in both hands, lighter weapons tucked with the arms still pumping. Needs weapon idle stance on.",
                config.weaponSprintEnabled,
            ) { config.weaponSprintEnabled = it }
            checkbox(
                "Walk stance",
                "While walking, the weapon is carried in a relaxed travelling pose and the arms swing in step with your stride. Needs weapon idle stance on.",
                config.weaponWalkEnabled,
            ) { config.weaponWalkEnabled = it }
            checkbox(
                "Combo attacks",
                "Chains different strokes as you attack (swipe left, right, thrust, twirl, then a finisher). Resets after a short pause.",
                config.weaponAnimationCombo,
            ) { config.weaponAnimationCombo = it }
            checkbox(
                "Spell animations",
                "Plays a unique animation for each class spell (Bash, Heal, Arrow Storm, Spin Attack, Totem and the rest, including archetype variants) the moment Wynncraft announces the cast. Spells without a dedicated animation use a generic cast. Needs weapon attack animations on.",
                config.weaponAnimationSpells,
            ) { config.weaponAnimationSpells = it }
            checkbox(
                "Swing sound effects",
                "Plays a local whoosh, and a heavier hit on finishers, timed to each stroke. Only you hear these.",
                config.weaponAnimationSfx,
            ) { config.weaponAnimationSfx = it }
            val sfxSlider = OwSlider(left, 0, w, OwTheme.ROW_H - 2, 0.0, 1.0, 2, config.weaponAnimationSfxVolume, "Swing sound volume") { config.weaponAnimationSfxVolume = it }
            rows += sfxSlider to OwTheme.ROW_H
            checkbox(
                "Weapon afterimages",
                "Leaves faint fading copies of your held weapon along the swing, drawn from its real item texture.",
                config.weaponAnimationTrail,
            ) { config.weaponAnimationTrail = it }
            val trailSlider = OwSlider(left, 0, w, OwTheme.ROW_H - 2, 0.2, 1.5, 2, config.weaponAnimationTrailIntensity, "Afterimage opacity") { config.weaponAnimationTrailIntensity = it }
            rows += trailSlider to OwTheme.ROW_H
            cycleButton({ "Weapon animation models..." }, "Register weapon models and choose which animation each one uses.") {
                Minecraft.getInstance().setScreenAndShow(OverwatchWeaponAnimationScreen(host.screen))
            }
            checkbox(
                "Freeze animation (preview)",
                "Debug: holds your held weapon's animation at the progress below so you can inspect a single pose. Progress 0 and 1 are the standby pose.",
                config.weaponAnimationPreview,
            ) { config.weaponAnimationPreview = it }
            val previewSlider = OwSlider(left, 0, w, OwTheme.ROW_H - 2, 0.0, 1.0, 2, config.weaponAnimationPreviewT, "Preview progress") { config.weaponAnimationPreviewT = it }
            rows += previewSlider to OwTheme.ROW_H
        }
    }

    private fun buildQolTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
            checkbox(
                "Remember camera mode",
                "Restores your last camera mode (first person, third person back or front) when you join a world or server.",
                config.rememberCameraMode,
            ) { config.rememberCameraMode = it }

            header("Town")
            checkbox(
                "Town NPC markers",
                "Small faded icons over blacksmiths, merchants, item identifiers, upgraders and similar NPCs nearby. The name shows when you look at one.",
                config.townNpcMarkersEnabled,
            ) { config.townNpcMarkersEnabled = it }

            header("Nametags")
            checkbox(
                "Highlight party & friends",
                "Prefixes other players' nametags with [Party]/[Friend] when they're in your current party or friends list (read from \"party list\"/\"friend list\").",
                config.customPartyNametagsEnabled,
            ) { config.customPartyNametagsEnabled = it }

            header("Gear")
            checkbox(
                "Equipped item comparison",
                "Shows the tooltip of your currently equipped item beside the hovered armor piece, accessory or weapon in the Overwatch inventory.",
                config.equipComparisonEnabled,
            ) { config.equipComparisonEnabled = it }
            checkbox(
                "Powder special effects",
                "Adds the weapon and armour special effect of a powder to its tooltip (unlocked with two Tier 4+ powders of the same element).",
                config.powderSpecialsTooltipEnabled,
            ) { config.powderSpecialsTooltipEnabled = it }

            header("Prices")
            checkbox(
                "Price check on tooltips",
                "Shows Trade Market prices (lowest / median / average, from Wynnventory) on hovered gear and tiered materials. Needs a Wynnventory API key.",
                config.priceCheckEnabled,
            ) { config.priceCheckEnabled = it }
            checkbox(
                "NPC & listing prices",
                "Formats merchant prices in stx / LE / EB and compares merchant and Trade Market listing prices to the market.",
                config.priceCheckNpcEnabled,
            ) { config.priceCheckNpcEnabled = it }
            rows += OwLabel(left, 0, w, OwTheme.ROW_H, "Wynnventory API key") to OwTheme.ROW_H
            val keyBox = OwTextField(host.panelFont, left, 0, w, OwTheme.ROW_H)
            keyBox.setMaxLength(128)
            keyBox.setValue(config.wynnventoryApiKey)
            keyBox.setHint(Component.literal("paste your read-only key"))
            keyBox.setResponder { config.wynnventoryApiKey = it.trim() }
            keyBox.setTooltip(Tooltip.create(Component.literal("Free read-only key from wynnventory.com. Stored only in your local config file and sent only to wynnventory.com.")))
            rows += keyBox to OwTheme.ROW_H

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

            header("Inventory")
            checkbox("Custom inventory screen", "New World-styled categorized inventory replacing the vanilla survival inventory.", config.customInventoryEnabled) {
                config.customInventoryEnabled = it
            }
            checkbox("Shift-drag quick move", "Hold Shift and drag across slots to quick-move each one, in every container screen (like Mouse Tweaks).", config.shiftDragQuickMove) {
                config.shiftDragQuickMove = it
            }

            header("Tools")
            rows += OwButton(left, 0, w, OwTheme.ROW_H - 2, Component.literal("Quest Reference (wiki)")) {
                Minecraft.getInstance().setScreenAndShow(OverwatchQuestBookScreen(host.screen))
            } to OwTheme.ROW_H
        }
    }

    private fun buildToastsTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
            val kind = activeToastKind
            val settings = config.toast(kind)

            val (enabledLabel, enabledTooltip) = when (kind) {
                OverwatchToastQueue.Kind.QUEST -> "Quest completion toast" to "Blocks the \"[Quest Completed]\" style chat messages (quests, mini-quests, caves, dungeons, raids, world events, boss altars) and shows a HUD toast with the rewards instead."
                OverwatchToastQueue.Kind.LEVEL_UP -> "Level up toast" to "Blocks level-up chat messages and shows a HUD toast instead."
                OverwatchToastQueue.Kind.DISCOVERY -> "Area discovery toast" to "Blocks the \"Area Discovered\" chat message and its description, and shows a HUD toast instead."
                OverwatchToastQueue.Kind.LOCATION -> "Location change toast" to "Shows a HUD toast with the region name when you enter a new area."
            }
            val enabledBox = OwCheckbox(left, 0, w, Component.literal(enabledLabel), toastEnabled(kind)) {
                setToastEnabled(kind, it)
                config.save()
            }
            enabledBox.setTooltip(Tooltip.create(Component.literal(enabledTooltip)))

            header("${kind.label} Toast")
            rows += enabledBox to OwTheme.ROW_H
            button(
                "Style: ${OverwatchToastQueue.styleFor(kind).label}",
                "Classic: a boxed panel. Souls: large fading text with a soft dark band and ornamental rule.",
            ) {
                setToastStyle(kind, OverwatchToastQueue.styleFor(kind).next().name)
                config.save()
            }

            header("Appearance")
            slider("Scale", 0.5, 4.0, 2, settings.scale, "Size of this toast's text and panel.") { settings.scale = it }
            slider("Opacity", 0.2, 1.0, 2, settings.opacity, "How solid this toast is at its most visible.") { settings.opacity = it }
            slider("Duration (s)", 1.5, 20.0, 1, settings.durationSeconds, "How long this toast stays on screen, including its fade in and out.") { settings.durationSeconds = it }

            header("Sound")
            button("Sound: ${NotificationSounds.label(settings.sound)}", "Sound played when this toast appears. Click to cycle (plays a preview).") {
                settings.sound = NotificationSounds.next(settings.sound, allowOff = true)
                NotificationSounds.play(settings.sound, settings.soundVolume)
            }
            slider("Volume", 0.0, 1.0, 2, settings.soundVolume, "Volume of this toast's sound.") { settings.soundVolume = it }

            header("Preview & Position")
            button("Preview this toast", "Shows a sample of this toast using your current settings.") { OverwatchToastQueue.preview(kind) }
            button("Preview all toasts", "Queues one sample of every toast type.") { OverwatchToastQueue.preview() }
            button("Position toasts...", "Toasts share one position. Opens the HUD designer to move or resize it.") {
                Minecraft.getInstance().setScreenAndShow(HudDesignerScreen(host.screen))
            }
        }
    }

    private fun toastEnabled(kind: OverwatchToastQueue.Kind): Boolean = when (kind) {
        OverwatchToastQueue.Kind.QUEST -> config.questCompletionToastEnabled
        OverwatchToastQueue.Kind.LEVEL_UP -> config.levelUpToastEnabled
        OverwatchToastQueue.Kind.DISCOVERY -> config.discoveryToastEnabled
        OverwatchToastQueue.Kind.LOCATION -> config.locationToastEnabled
    }

    private fun setToastEnabled(kind: OverwatchToastQueue.Kind, value: Boolean) {
        when (kind) {
            OverwatchToastQueue.Kind.QUEST -> config.questCompletionToastEnabled = value
            OverwatchToastQueue.Kind.LEVEL_UP -> config.levelUpToastEnabled = value
            OverwatchToastQueue.Kind.DISCOVERY -> config.discoveryToastEnabled = value
            OverwatchToastQueue.Kind.LOCATION -> config.locationToastEnabled = value
        }
    }

    private fun setToastStyle(kind: OverwatchToastQueue.Kind, value: String) {
        when (kind) {
            OverwatchToastQueue.Kind.QUEST -> config.questToastStyle = value
            OverwatchToastQueue.Kind.LEVEL_UP -> config.levelUpToastStyle = value
            OverwatchToastQueue.Kind.DISCOVERY -> config.discoveryToastStyle = value
            OverwatchToastQueue.Kind.LOCATION -> config.locationToastStyle = value
        }
    }

    private fun buildAlertsTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
            cycleButton({ "Alert sound: ${NotificationSounds.label(config.mythicAlertSoundId)}" }, "Sound played when a rare item is obtained. Click to cycle (plays a preview).") {
                config.mythicAlertSoundId = NotificationSounds.next(config.mythicAlertSoundId, allowOff = false)
                NotificationSounds.play(config.mythicAlertSoundId, config.mythicAlertVolume)
            }
            slider("Alert volume", 0.0, 1.0, config.mythicAlertVolume, "Volume of the rare item alert sound.") { config.mythicAlertVolume = it }
            checkbox("Show chat message", "", config.mythicAlertChat) { config.mythicAlertChat = it }

            header("Entity Tracker Alerts")
            checkbox("Chat message on new match", "", config.trackerPingChat) { config.trackerPingChat = it }
            checkbox("Sound on new match", "", config.trackerPingSound) { config.trackerPingSound = it }
            cycleButton({ "Ping sound: ${soundLabel()}" }, "Sound played when an entity starts matching. Click to cycle (plays a preview).") {
                config.trackerPingSoundId = NotificationSounds.next(config.trackerPingSoundId, allowOff = false)
                previewTrackerSound()
            }
            slider("Ping pitch", 0.5, 2.0, config.trackerPingPitch, "Pitch of the new-match ping sound.") { config.trackerPingPitch = it }
            slider("Ping volume", 0.0, 1.0, config.trackerPingVolume, "Volume of the new-match ping sound.") { config.trackerPingVolume = it }
        }
    }

    private fun rarityLabel(): String = WynnRarity.entries.firstOrNull { it.name == config.mythicAlertMinRarity }?.displayName ?: "Mythic"

    private fun buildHudTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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

            header("Chat")
            checkbox("Custom chat layout", "Makes the chat a HUD element you can drag and resize in the HUD designer. Off keeps the vanilla chat position and size.", config.chatHudEnabled) {
                config.chatHudEnabled = it
            }
            cycleButton({ "Chat style: ${ChatHud.label(config.chatStyle)}" }, "Classic is the vanilla per-line backdrop. Glass, fade band and no backdrop restyle only the backgrounds behind chat lines.") {
                val i = ChatHud.STYLES.indexOf(config.chatStyle).coerceAtLeast(0)
                config.chatStyle = ChatHud.STYLES[(i + 1).mod(ChatHud.STYLES.size)]
            }

            checkbox("Smart reply", "When someone messages you (or you message them) in the last 3 minutes, opening chat switches to a direct conversation with them. Pick ALL to go back.", config.chatSmartReply) {
                config.chatSmartReply = it
            }
            checkbox("Conversation view", "While a direct conversation is selected, the open chat shows only that player's messages, like a separate channel per player.", config.chatConversationView) {
                config.chatConversationView = it
            }

            header("Hotbar")
            cycleButton({ "Hotbar style: ${HotbarStyles.label(config.hotbarStyle)}" }, "Classic is the vanilla bar. Glass strip, floating tiles, arc, radial wheel and Elden cross are redrawn layouts of the same nine slots; drag and scale them in the HUD designer.") {
                val i = HotbarStyles.STYLES.indexOf(config.hotbarStyle).coerceAtLeast(0)
                config.hotbarStyle = HotbarStyles.STYLES[(i + 1).mod(HotbarStyles.STYLES.size)]
            }

            header("Panels")
            checkbox(
                "Panel backgrounds",
                "Draws dark panels behind HUD text. Off is the New World style: floating shadowed text with no panels (bars keep their tracks).",
                config.hudPanelsEnabled,
            ) { config.hudPanelsEnabled = it }
        }
    }

    private fun buildLootrunTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
        }
    }

    private fun buildDiscordTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
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
        }
    }

    private fun buildOverridesTab(left: Int, w: Int, top: Int) {
        panel(left, w, top) {
            rows += OwSectionHeader(left, 0, w, "Wynncraft Overrides") to 18
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

            rows += OwSectionHeader(left, 0, w, "Rendering") to 18
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

            rows += OwSectionHeader(left, 0, w, "Debug") to 18
            val debugCheckbox = OwCheckbox(left, 0, w, Component.literal("Middle-click copies item data"), config.debugItemCopyEnabled) {
                config.debugItemCopyEnabled = it
            }
            debugCheckbox.setTooltip(
                Tooltip.create(
                    Component.literal(
                        "Middle-click a hovered item in a custom Overwatch screen to copy its registry id, name and full data components to the clipboard -- for matching server menu items during development.",
                    ),
                ),
            )
            rows += debugCheckbox to OwTheme.ROW_H
            val actionBarLogCheckbox = OwCheckbox(left, 0, w, Component.literal("Log action bar glyphs"), config.debugActionBarLog) {
                config.debugActionBarLog = it
            }
            actionBarLogCheckbox.setTooltip(
                Tooltip.create(Component.literal("Writes every changed action bar line to the game log as text with <U+XXXX> codepoints for the glyphs (tag: [ActionBar])."))
            )
            rows += actionBarLogCheckbox to OwTheme.ROW_H
        }
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
    }
}
