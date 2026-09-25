package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack
import opal.dev.overwatch.Overwatch
import opal.dev.overwatch.client.CharacterMenuModel

class OverwatchInventoryScreen(
    private val menu: InventoryMenu,
    initialTab: InvTab = InvTab.INVENTORY,
) : Screen(Component.literal("Overwatch - Inventory")),
    OverwatchSettingsPanels.Host {
    enum class InvTab(val label: String) {
        INVENTORY("Inventory"),
        CHARACTER("Character"),
        JOURNAL("Journal"),
        SETTINGS("Settings"),
    }

    private data class PlacedTile(val menuSlot: Int, val x: Int, val y: Int, val docked: Boolean = false)
    private data class PlacedRow(val menuSlot: Int, val x: Int, val y: Int, val w: Int)
    private data class PlacedLabel(val text: String, val x: Int, val y: Int, val color: Int = OwTheme.TEXT_DIM, val docked: Boolean = false, val maxW: Int = -1)
    private data class JournalSlot(val x: Int, val y: Int, val activity: ActivityInfo)
    private data class Layout(
        val tiles: List<PlacedTile>,
        val labels: List<PlacedLabel>,
        val rows: List<PlacedRow>,
        val contentH: Int,
        val panelH: Int,
        val gridX: Int,
        val gridW: Int,
        val scrollTop: Int,
        val scrollBottom: Int,
        val journalSlots: List<JournalSlot> = emptyList(),
        val charTiles: List<PlacedTile> = emptyList(),

        val pouchSlot: Int = -1,
        val pouchX: Int = 0,
        val pouchY: Int = 0,
    )

    private var searchField: OwTextField? = null
    private var inventorySortButton: OwButton? = null
    private var searchText: String = ""
    private val tabButtons = ArrayList<OwButton>()
    private var scrollY: Int = 0
    private var invTab: InvTab = initialTab
    private val panels = OverwatchSettingsPanels(this)
    private val panelList = OwPanelList({ addRenderableWidget(it) })

    private var lastLayout: Layout? = null
    private var settingsPanelBottom: Int = 0
    private var mouseX: Int = 0
    private var mouseY: Int = 0
    private var hoveredSlot: Int = -1
    private var previewBox: IntArray = intArrayOf(0, 0, 0, 0)

    private var journalMenu: AbstractContainerMenu? = null
    private var characterMenu: AbstractContainerMenu? = null
    private val journal = ContentBookViewModel(ContentBookCache.snapshot ?: emptyList())
    private var journalBusy: Boolean = false
    private var charScrollY: Int = 0
    private var journalScrollY: Int = 0
    private var journalSearchField: OwTextField? = null
    private var journalSearchRefocus = -1
    private var journalSortButton: OwButton? = null
    private val journalCategoryButtons = ArrayList<Pair<OwButton, Int>>()
    private var journalTrackButton: OwButton? = null
    private var journalWikiButton: OwButton? = null
    private var journalActionsTop: Int = 0
    private var journalRefreshButton: OwButton? = null
    private var selectedJournal: ActivityInfo? = null
    private var hoveredChar: Int = -1
    private var hoveredJournal: JournalSlot? = null

    private val charWidgets = ArrayList<OwButton>()
    private var charSnapshot: CharacterMenuModel.Snapshot? = null
    private val combatInfoPager = CombatInfoPager()
    private enum class CharStatsTab { COMBAT, PROFESSIONS }
    private var charStatsTab = CharStatsTab.COMBAT
    private data class CharZone(val x: Int, val y: Int, val w: Int, val h: Int, val slot: Int)
    private val charHoverZones = ArrayList<CharZone>()
    private val charWidgetRows = ArrayList<Pair<OwButton, Int>>()
    private val charPinnedWidgetRows = ArrayList<Pair<OwButton, Int>>()
    private val charButtonSlots = ArrayList<Pair<OwButton, Int>>()
    private var charTickCounter = 0
    private var charLoadTicks = 0
    private var charWasLoading = false
    private var lastStatClickNanos = 0L

    private var pendingTabFire: Pair<InvTab, () -> Boolean>? = null
    private var pendingTabFireTimeout: Int = 0

    fun pollPendingFire() {
        val pendingFire = pendingTabFire ?: return
        if (invTab != pendingFire.first) {
            pendingTabFire = null
            return
        }
        --pendingTabFireTimeout
        if (pendingTabFireTimeout <= 0) {
            pendingTabFire = null
            pendingFire.second()
            return
        }

        if (pendingFire.first == InvTab.JOURNAL && journalMenu != null) {
            pendingTabFire = null
            return
        }
        if (pendingFire.first == InvTab.CHARACTER && characterMenu != null) {
            pendingTabFire = null
            return
        }
        if (!resyncPoll()) return

        pendingFire.second()
    }

    private fun resyncPoll(): Boolean {
        for (slot in 9 until menu.slots.size) {
            if (!menu.slots[slot].item.isEmpty) return true
        }
        return false
    }

    private fun armCrossTabTrigger() {
        if (!menu.carried.isEmpty) return
        when (invTab) {
            InvTab.JOURNAL -> if (journalMenu == null && !ContentBookQuery.isActive) {
                journal.actionMessage = "Opening..."
                pendingTabFire = InvTab.JOURNAL to { fireJournalTrigger() }
                pendingTabFireTimeout = 200
            }
            InvTab.CHARACTER -> if (characterMenu == null) {
                pendingTabFire = InvTab.CHARACTER to { fireCharacterTrigger() }
                pendingTabFireTimeout = 200
            }
            else -> {}
}
    }

    private var painting = false
    private var shiftDragging = false
    private val shiftVisited = HashSet<Int>()
    private var shiftDragX = 0
    private var shiftDragY = 0
    private var paintRight = false
    private var paintOrigin = -1
    private val painted = HashSet<Int>()
    private var lastClickSlot = -1
    private var lastClickTime = 0L

    private fun panelHFor(contentH: Int): Int {
        val fullH = height - MARGIN * 2 + 8
        return minOf(fullH, CONTENT_TOP_REL + contentH + GAP + FOOTER_H + 4).coerceAtLeast(160)
    }

    private fun panelTopFor(panelH: Int): Int = ((height - panelH) / 2).coerceAtLeast(MARGIN - 4)

    override fun init() {
        val ox = panelLeft()
        val layout = when (invTab) {
            InvTab.SETTINGS -> computeSettingsLayout()
            else -> computeInventoryLayout()
        }
        val panelH = layout.panelH
        val pt = panelTopFor(panelH)
        var tx = ox + MARGIN
        tabButtons.clear()
        for (tab in InvTab.entries) {
            val button = OwButton(tx, pt + TAB_Y_REL, TAB_W, TAB_H, Component.literal(tab.label), accent = tab == invTab) {
                    selectTab(tab)
                }
            tabButtons.add(button)
            addRenderableWidget(button)
            tx += TAB_W + 2
        }
        addClaimButtons(ox, pt)
        if (invTab == InvTab.INVENTORY) {
            val fieldX = ox + MARGIN
            val fieldY = pt + SEARCH_Y_REL
            val sortW = font.width("Sort: Default") + 16
            searchField = OwTextField(font, fieldX, fieldY, panelWidth() - MARGIN * 2 - sortW - 4, SEARCH_H).also {
                it.value = searchText
                addRenderableWidget(it)
            }
            inventorySortButton = OwButton(
                fieldX + panelWidth() - MARGIN * 2 - sortW, fieldY, sortW, SEARCH_H,
                Component.literal("Sort: ${InventorySort.parse(OverwatchConfig.current.inventorySort).label}"),
            ) {
                val config = OverwatchConfig.current
                config.inventorySort = InventorySort.parse(config.inventorySort).next().name
                config.save()
                rebuildWidgets()
            }.also { addRenderableWidget(it) }
        } else {
            searchField = null
            inventorySortButton = null
        }
        if (invTab == InvTab.JOURNAL) {
            val gx = ox + MARGIN
            val gw = panelWidth() - MARGIN * 2
            val rowY = { dy: Int -> pt + CONTENT_TOP_REL + dy }
            val sortW = 72
            val refreshW = 56
            journalSearchField = OwTextField(font, gx, rowY(0), gw - sortW - refreshW - 8, SEARCH_H).also {
                it.setValue(journal.query)
                it.setResponder { v ->
                    journal.query = v
                    journalScrollY = 0
                    journalSearchRefocus = journalSearchField?.cursorPosition ?: v.length
                    rebuildWidgets()
                }
                addRenderableWidget(it)
                if (journalSearchRefocus >= 0) {
                    setFocused(it)
                    it.setCursorPosition(journalSearchRefocus.coerceIn(0, it.value.length))
                    it.setHighlightPos(it.cursorPosition)
                    journalSearchRefocus = -1
                }
            }
            journalSortButton = OwButton(gx + gw - sortW - refreshW - 4, rowY(0), sortW, SEARCH_H, Component.literal(journal.sort.label)) {
                journal.cycleSort()
                rebuildWidgets()
            }.also { addRenderableWidget(it) }
            journalRefreshButton = OwButton(gx + gw - refreshW, rowY(0), refreshW, SEARCH_H, Component.literal("Refresh")) {
                refreshJournal()
            }.also { addRenderableWidget(it) }

            journalCategoryButtons.clear()
            val header = computeJournalHeader(gw)
            for (chip in header.chips) {
                val button = OwButton(gx + chip.x, rowY(chip.y), chip.w, JOURNAL_TAB_H, Component.literal(chip.value ?: "All"), accent = journal.filter == chip.value) {
                    journal.selectFilter(chip.value)
                    journalScrollY = 0
                    rebuildWidgets()
                }.also { addRenderableWidget(it) }
                journalCategoryButtons.add(button to chip.y)
            }

            journalActionsTop = header.actionsTop
            val selected = selectedJournal
            if (selected != null) {
                val mapLocated = if (selected.trackingState == ActivityTrackingState.UNTRACKABLE) DiscoveryTracker.locate(selected) else null
                val trackText = when {
                    mapLocated == null -> trackLabel(selected)
                    DiscoveryTracker.isTracked(selected.name) -> "Untrack"
                    mapLocated.approximate -> "Track (approx.)"
                    else -> "Track"
                }
                journalTrackButton = OwButton(
                    gx, rowY(header.actionsTop), 120, JOURNAL_ACTION_BTN_H, Component.literal(trackText),
                    enabled = { selected.trackingState != ActivityTrackingState.UNTRACKABLE || mapLocated != null },
                ) {
                    toggleJournalTrack(selected)
                }.also { addRenderableWidget(it) }
                journalWikiButton = OwButton(gx + 124, rowY(header.actionsTop), 100, JOURNAL_ACTION_BTN_H, Component.literal("Wiki Info")) {
                    Minecraft.getInstance().setScreenAndShow(OverwatchQuestWikiScreen(selected.type, selected.name, this))
                }.also { addRenderableWidget(it) }
            } else {
                journalTrackButton = null
                journalWikiButton = null
            }
        } else {
            journalSearchField = null
            journalSortButton = null
            journalCategoryButtons.clear()
            journalTrackButton = null
            journalWikiButton = null
            journalRefreshButton = null
        }
        if (invTab == InvTab.CHARACTER) {
            charWidgets.clear()
            charWidgetRows.clear()
            charPinnedWidgetRows.clear()
            charButtonSlots.clear()
            val menu = characterMenu
            charSnapshot = menu?.let { CharacterMenuModel.snapshot(it) } ?: CharacterMenuModel.lastSnapshot
            val snap = charSnapshot
            if (menu != null && snap != null) {
                val gx = ox + MARGIN
                val gw = panelWidth() - MARGIN * 2
                val leftW = (gw * 0.44).toInt()
                val rightW = gw - leftW - CHAR_CARD_GAP
                val rightX = gx + leftW + CHAR_CARD_GAP

                val statsTabY = CHARACTER_GRID_Y + charSections(menu).identity.size * STATS_ROW_H
                val statsTabBy = charScreenY(statsTabY, pt)
                val statsTabBtnW = (leftW - MENU_BTN_GAP) / 2
                charWidgets.add(
                    OwButton(gx, statsTabBy, statsTabBtnW, STAT_TAB_H, Component.literal("Combat"), accent = charStatsTab == CharStatsTab.COMBAT) {
                        charStatsTab = CharStatsTab.COMBAT
                        rebuildWidgets()
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to statsTabY) },
                )
                charWidgets.add(
                    OwButton(gx + statsTabBtnW + MENU_BTN_GAP, statsTabBy, leftW - statsTabBtnW - MENU_BTN_GAP, STAT_TAB_H, Component.literal("Professions"), accent = charStatsTab == CharStatsTab.PROFESSIONS) {
                        charStatsTab = CharStatsTab.PROFESSIONS
                        rebuildWidgets()
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to statsTabY) },
                )

                if (snap.openers.isNotEmpty()) {
                    val cols = (snap.openers.size + OPENER_ROWS - 1) / OPENER_ROWS
                    val btnW = gw / cols
                    for ((j, pair) in snap.openers.withIndex()) {
                        val (label, slot) = pair
                        val row = j / cols
                        val col = j % cols
                        val rowY = CHAR_MENU_ROW_Y + row * (OPENER_BTN_H + OPENER_ROW_GAP)
                        val by = pt + CONTENT_TOP_REL + rowY
                        val bx = gx + col * btnW
                        val lastCol = col == cols - 1 || j == snap.openers.size - 1
                        val w = if (lastCol) gx + gw - bx else btnW - MENU_BTN_GAP
                        val iconScale = if (label == "Ability Tree") 0.7f else 1f
                        charWidgets.add(
                            OwButton(bx, by, w, OPENER_BTN_H, Component.literal(label), accent = true, icon = charSlotStack(slot), iconScale = iconScale) {
                                sendCharInput(slot, 0, ContainerInput.PICKUP)
                            }.also {
                                addRenderableWidget(it)
                                charPinnedWidgetRows.add(it to rowY)
                                charButtonSlots.add(it to slot)
                            },
                        )
                    }
                }

                for ((i, skill) in snap.skills.withIndex()) {
                    val cellY = CHARACTER_GRID_Y + HEADER_H + i * SKILL_ROW_H
                    val by = charScreenY(cellY, pt)
                    charWidgets.add(OwButton(rightX + rightW - 44, by, 20, 16, Component.literal("-")) {
                        sendCharInput(skill.slot, 1, if (shiftHeld()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to cellY) })
                    charWidgets.add(OwButton(rightX + rightW - 22, by, 20, 16, Component.literal("+")) {
                        sendCharInput(skill.slot, 0, if (shiftHeld()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to cellY) })
                }
                val wy = CHARACTER_GRID_Y + HEADER_H + maxOf(5, snap.skills.size) * SKILL_ROW_H + CHAR_SECTION_GAP
                if (snap.skillCrystalSlot >= 0) {
                    val crystal = snap.skillCrystalSlot
                    val by = charScreenY(wy, pt)
                    charWidgets.add(
                        OwButton(rightX, by, rightW, OPENER_BTN_H, Component.literal("Reset Skills"), icon = charSlotStack(crystal)) {
                            sendCharInput(crystal, 0, ContainerInput.QUICK_MOVE)
                        }.also { addRenderableWidget(it); charWidgetRows.add(it to wy); charButtonSlots.add(it to crystal) },
                    )
                }
            }
        } else {
            charWidgets.clear()
            charWidgetRows.clear()
            charPinnedWidgetRows.clear()
            charButtonSlots.clear()
        }
        if (invTab == InvTab.SETTINGS) {
            panels.buildTabs(ox + MARGIN, panelWidth() - MARGIN * 2, pt + CONTENT_TOP_REL)
        }

        armCrossTabTrigger()
    }

    override fun tick() {
        super.tick()
        searchText = searchField?.value ?: searchText
        if (invTab == InvTab.SETTINGS) panels.tick()

        if (invTab == InvTab.CHARACTER && characterMenu != null) {
            charTickCounter++
            val loadingBefore = charLoading()
            if (loadingBefore) {
                charLoadTicks++
                if (charLoadTicks >= CHAR_LOAD_TIMEOUT_TICKS) combatInfoPager.forceDone()
            }
            if (!loadingBefore && charTickCounter % 4 == 0) {
                val old = charSnapshot
                var snap = CharacterMenuModel.snapshot(characterMenu!!)
                if (snap != null && old != null) {
                    snap = snap.copy(skills = snap.skills.map { s ->
                        if (s.points < 0) old.skills.firstOrNull { it.slot == s.slot }?.copy(isConfirm = true) ?: s else s
                    })
                    if (System.nanoTime() - lastStatClickNanos < STAT_GRACE_NANOS) {
                        val missing = old.skills.filter { o -> snap.skills.none { it.slot == o.slot } }
                        if (missing.isNotEmpty()) snap = snap.copy(skills = (snap.skills + missing).sortedBy { it.slot })
                    }
                }
                if (snap != null && snap != old) {
                    charSnapshot = snap
                    CharacterMenuModel.lastSnapshot = snap
                    rebuildWidgets()
                }
            }
            combatInfoPager.tick(characterMenu!!) { slot -> sendCharInput(slot, 0, ContainerInput.PICKUP) }
            val loadingAfter = charLoading()
            if (charWasLoading && !loadingAfter) rebuildWidgets()
            charWasLoading = loadingAfter
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        val player = Minecraft.getInstance().player
        val layout = when (invTab) {
            InvTab.SETTINGS -> computeSettingsLayout().also {
                drawOuterPanel(graphics, panelTopFor(it.panelH), it.panelH)
            }
            else -> computeInventoryLayout().also {
                val pt = panelTopFor(it.panelH)

                for (button in tabButtons) button.y = pt + TAB_Y_REL
                searchField?.let { field -> field.y = pt + SEARCH_Y_REL }
                inventorySortButton?.let { button -> button.y = pt + SEARCH_Y_REL }
                journalSearchField?.let { field -> field.y = pt + CONTENT_TOP_REL }
                journalSortButton?.let { button -> button.y = pt + CONTENT_TOP_REL }
                journalRefreshButton?.let { button -> button.y = pt + CONTENT_TOP_REL }
                for ((button, relY) in journalCategoryButtons) button.y = pt + CONTENT_TOP_REL + relY
                journalTrackButton?.let { button -> button.y = pt + CONTENT_TOP_REL + journalActionsTop }
                journalWikiButton?.let { button -> button.y = pt + CONTENT_TOP_REL + journalActionsTop }
                drawOuterPanel(graphics, pt, it.panelH)
                when (invTab) {
                    InvTab.INVENTORY -> {
                        drawScrollContent(graphics, player, it)
                        drawFooter(graphics, pt, it.panelH)
                    }
                    InvTab.JOURNAL -> {
                        drawJournalContent(graphics, player, it)
                    }
                    InvTab.CHARACTER -> {
                        for ((button, contentY) in charWidgetRows) {
                            button.y = pt + CONTENT_TOP_REL + contentY - charScrollY
                            button.visible = button.y + button.height > it.scrollTop && button.y < it.scrollBottom
                        }
                        for ((button, contentY) in charPinnedWidgetRows) {
                            button.y = pt + CONTENT_TOP_REL + contentY
                            button.visible = true
                        }
                        drawCharacterContent(graphics, player, it)
                    }
                    else -> {}
                }
            }
        }
        lastLayout = layout
        hoveredSlot = if (invTab == InvTab.INVENTORY) findTile(layout, mouseX, mouseY) else -1
        hoveredChar = if (invTab == InvTab.CHARACTER) findCharTile(layout, mouseX, mouseY) else -1
        hoveredJournal = if (invTab == InvTab.JOURNAL) findJournalSlot(layout, mouseX, mouseY) else null

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (invTab == InvTab.INVENTORY) drawHoverAndTooltip(graphics, layout)
        if (invTab == InvTab.CHARACTER) {
            drawCharHoverAndTooltip(graphics, layout)
            drawCharButtonTooltip(graphics)
        }
        if (invTab == InvTab.JOURNAL) drawJournalHoverAndTooltip(graphics)

        if (invTab == InvTab.INVENTORY) drawCarried(graphics, mouseX, mouseY, menu.carried)
    }

    private fun emptyLayout(): Layout = Layout(emptyList(), emptyList(), emptyList(), -1, 0, 0, 0, 0, 0)

    private fun computeSettingsLayout(): Layout {
        val contentH = 380
        val panelH = panelHFor(contentH)
        val panelTop = panelTopFor(panelH)
        settingsPanelBottom = panelTop + panelH
        val ox = panelLeft() + MARGIN
        val w = panelWidth() - MARGIN * 2
        val scrollTop = panelTop + CONTENT_TOP_REL
        val scrollBottom = (panelTop + panelH - 4 - FOOTER_H - GAP).coerceAtLeast(scrollTop + 40)
        return Layout(emptyList(), emptyList(), emptyList(), contentH, panelH, ox, w, scrollTop, scrollBottom, emptyList(), emptyList())
    }

    private fun computeInventoryLayout(): Layout {
        val x = panelLeft() + MARGIN
        val w = (panelLeft() + panelWidth() - MARGIN - x).coerceAtLeast(120)

        val query = searchText.trim().lowercase()
        val tiles = ArrayList<PlacedTile>()
        val rows = ArrayList<PlacedRow>()
        val labels = ArrayList<PlacedLabel>()
        var y = 0
        var pouchSlotOut = -1
        var pouchXOut = 0
        var pouchYOut = 0

        charHoverZones.clear()

        if (invTab == InvTab.INVENTORY) {
            labels.add(PlacedLabel("Hotbar", x, y, color = OwTheme.ACCENT))
            y += CAPTION_H
            var hx = x
            for (slot in HOTBAR_SLOTS) {
                val stack = slotStack(slot)
                val show = !isExcluded(stack) && (query.isEmpty() || stack.isEmpty || matchesQuery(stack, query))
                tiles.add(PlacedTile(if (show) slot else -1, hx, y))
                hx += TILE_STEP
            }
            y += TILE_STEP + SECTION_GAP

            val pouchSlot = findIngredientPouch()

            val storage = if (WorldContext.isWynncraft(Minecraft.getInstance())) MAIN_SLOTS.drop(4) else MAIN_SLOTS
            val filtered = InventorySort.parse(OverwatchConfig.current.inventorySort)
                .apply(storage.filter { slotVisible(it, query) && !isExcluded(slotStack(it)) }, ::slotStack)
            labels.add(PlacedLabel("Inventory", x, y, color = OwTheme.ACCENT))
            y += CAPTION_H
            val gridTop = y
            y = layTilesFiltered(tiles, filtered, x, 3, y)

            var pouchX = 0
            var pouchY = 0
            if (pouchSlot >= 0) {
                val stack = slotStack(pouchSlot)
                val px = x + 3 * TILE_STEP + GAP
                val maxW = (x + w - px).coerceAtLeast(60)
                var py = gridTop - CAPTION_H
                labels.add(PlacedLabel("INGREDIENT POUCH", px, py, color = OwTheme.ACCENT))
                py += CAPTION_H
                pouchX = px
                pouchY = py
                py += TILE_STEP + 4
                val entries = WynnPouches.ingredientEntries(stack)
                if (entries.isEmpty()) {
                    labels.add(PlacedLabel("(pouch is empty)", px + 2, py + 1))
                    py += STATS_ROW_H
                } else {
                    for ((count, name) in entries.take(POUCH_LINES)) {
                        labels.add(PlacedLabel(trimToWidth("$count x $name", maxW - 2), px + 2, py + 1))
                        py += STATS_ROW_H
                    }
                    if (entries.size > POUCH_LINES) {
                        labels.add(PlacedLabel("+${entries.size - POUCH_LINES} more", px + 2, py + 1, color = OwTheme.TEXT_DIM))
                        py += STATS_ROW_H
                    }
                }
                if (WynnPouches.isSellConfirm(stack) || WynnPouches.isConfirmMorph(stack)) {
                    labels.add(PlacedLabel("Click to confirm sale!", px + 2, py + 1, color = OwTheme.BAD))
                } else {
                    labels.add(PlacedLabel("Left-click: view contents", px + 2, py + 1, color = OwTheme.TEXT_DIM))
                    labels.add(PlacedLabel("Shift+Right-click: sell all", px + 2, py + 1 + STATS_ROW_H, color = OwTheme.TEXT_DIM))
                }
            }
            pouchSlotOut = pouchSlot
            pouchXOut = pouchX
            pouchYOut = pouchY
        }
        val journalSlotsOut = ArrayList<JournalSlot>()
        if (invTab == InvTab.JOURNAL) {
            val header = computeJournalHeader(w)
            val results = journal.results()
            val cols = ContentBookViewModel.LIST_COLS
            val colW = w / cols
            for ((i, a) in results.withIndex()) {
                val col = i % cols
                val row = i / cols
                journalSlotsOut.add(JournalSlot(x + col * colW, header.listTop + row * ContentBookViewModel.ROW_H, a))
            }
            y = header.listTop + JOURNAL_LIST_ROWS * ContentBookViewModel.ROW_H + SECTION_GAP
        }

        val charTilesOut = ArrayList<PlacedTile>()
        if (invTab == InvTab.CHARACTER) {
            charHoverZones.clear()
            y = CHARACTER_GRID_Y
            val charMenu = characterMenu
            val snap = charSnapshot ?: characterMenu?.let { CharacterMenuModel.snapshot(it) }
            if (charMenu != null) {
                val leftW = (w * 0.44).toInt()
                val rightW = w - leftW - CHAR_CARD_GAP
                val leftX = x
                val rightX = x + leftW + CHAR_CARD_GAP
                val sections = charSections(charMenu)

                var leftY = y
                for ((text, slot) in sections.identity) {
                    labels.add(PlacedLabel(text, leftX + 4, leftY + 1))
                    charHoverZones.add(CharZone(leftX + 4, leftY + 1, leftW, STATS_ROW_H, slot))
                    leftY += STATS_ROW_H
                }
                leftY += STAT_TAB_H + CHAR_SECTION_GAP
                if (charStatsTab == CharStatsTab.COMBAT) {
                    if (sections.combat.isNotEmpty()) {
                        labels.add(PlacedLabel("COMBAT", leftX, leftY + 2, color = OwTheme.ACCENT))
                        leftY += HEADER_H
                        val flowItems = ArrayList<Pair<String, Int>>()
                        for ((raw, slot) in sections.combat) {
                            val text = raw.trim()
                            if (text.endsWith(":")) {
                                if (flowItems.isNotEmpty()) {
                                    leftY = layoutFlow(flowItems, labels, leftX, leftY, leftW)
                                    flowItems.clear()
                                }
                                labels.add(PlacedLabel(text.removeSuffix(":"), leftX, leftY + 2, color = OwTheme.ACCENT_DIM))
                                charHoverZones.add(CharZone(leftX, leftY, leftW, STATS_ROW_H, slot))
                                leftY += STATS_ROW_H
                            } else {
                                flowItems.add(text.removePrefix("-").trim() to slot)
                            }
                        }
                        if (flowItems.isNotEmpty()) leftY = layoutFlow(flowItems, labels, leftX, leftY, leftW)
                    }
                } else if (sections.professions.isNotEmpty()) {
                    labels.add(PlacedLabel("PROFESSIONS", leftX, leftY + 2, color = OwTheme.ACCENT))
                    leftY += HEADER_H
                    leftY = layoutFlow(sections.professions, labels, leftX, leftY, leftW)
                }
                val leftHeight = leftY - y

                val skillRows = maxOf(5, snap?.skills?.size ?: 0)
                val resetTop = y + HEADER_H + skillRows * SKILL_ROW_H + CHAR_SECTION_GAP
                val rightHeight: Int
                if (snap != null) {
                    labels.add(PlacedLabel("SKILLS", rightX, y + 2, color = OwTheme.ACCENT))
                    for ((i, skill) in snap.skills.withIndex()) {
                        val sy = y + HEADER_H + i * SKILL_ROW_H
                        charHoverZones.add(CharZone(rightX, sy, rightW, SKILL_ROW_H, skill.slot))
                    }
                    rightHeight = (resetTop - y) + (if (snap.skillCrystalSlot >= 0) SKILL_ROW_H else 0)
                } else {
                    labels.add(PlacedLabel("SKILLS", rightX, y + 2, color = OwTheme.ACCENT))
                    rightHeight = HEADER_H + skillRows * SKILL_ROW_H + CHAR_SECTION_GAP
                }

                y += maxOf(leftHeight, rightHeight) + SECTION_GAP
            }
        }

        val contentH = y
        val panelH = panelHFor(contentH)
        val panelTop = panelTopFor(panelH)
        val scrollTop = panelTop + CONTENT_TOP_REL
        val scrollBottom = (panelTop + panelH - 4 - FOOTER_H - GAP).coerceAtLeast(scrollTop + 40)

        val maxScroll = (contentH - (scrollBottom - scrollTop)).coerceAtLeast(0)
        if (scrollY > maxScroll) scrollY = maxScroll
        if (scrollY < 0) scrollY = 0

        run {
            val dockX = panelLeft() - GAP - dockW()
            val dollX = dockX + TILE + GAP
            val dollTop = ((height - PREVIEW_H) / 2).coerceAtLeast(MARGIN)
            val toContent = { screenY: Int -> screenY - scrollTop }
            previewBox = intArrayOf(dollX, toContent(dollTop), PREVIEW_W, PREVIEW_H)

            val armorX = dollX - GAP - TILE
            val armorTop = dollTop + (PREVIEW_H - ARMOR_SLOTS.size * TILE_STEP) / 2
            for ((i, slot) in ARMOR_SLOTS.withIndex()) {
                tiles.add(PlacedTile(slot, armorX, toContent(armorTop + i * TILE_STEP), docked = true))
            }

            val rightX = dollX + PREVIEW_W + GAP
            var rightY = dollTop
            tiles.add(PlacedTile(OFFHAND_SLOT, rightX, toContent(rightY), docked = true))
            rightY += TILE_STEP + 4

            for (slot in extraSlots()) {
                tiles.add(PlacedTile(slot, rightX, toContent(rightY), docked = true))
                rightY += TILE_STEP
            }

            val jx = maxOf(dockX, armorX - TILE_STEP)
            val jy = armorTop + ARMOR_SLOTS.size * TILE_STEP + 12
            labels.add(PlacedLabel("Accessories", jx, toContent(jy + 2), docked = true))
            ACCESSORY_EQUIP_SLOTS.forEachIndexed { i, slot ->
                tiles.add(
                    PlacedTile(
                        slot, jx + (i % 2) * TILE_STEP,
                        toContent(jy + CAPTION_H + (i / 2) * TILE_STEP), docked = true,
                    ),
                )
            }

            val lvl = WynnLevelTracker.level
            if (lvl != null) {
                val lvlText = "Lv $lvl"
                labels.add(
                    PlacedLabel(
                        lvlText, dollX + (PREVIEW_W - font.width(lvlText)) / 2,
                        toContent(maxOf(2, dollTop - 14)), docked = true,
                    ),
                )
            }

            if (invTab == InvTab.INVENTORY) {
                val snap = charSnapshot
                    ?: characterMenu?.let { CharacterMenuModel.snapshot(it) }
                    ?: CharacterMenuModel.lastSnapshot
                if (snap != null && snap.skills.isNotEmpty()) {
                    var sy = jy + CAPTION_H + 2 * TILE_STEP + 6
                    labels.add(PlacedLabel("Skills", dockX, toContent(sy + 2), docked = true))
                    sy += CAPTION_H
                    for (skill in snap.skills) {
                        val pts = if (skill.points < 0) "…" else skill.points.toString()
                        labels.add(
                            PlacedLabel(
                                trimToWidth("${skill.name} $pts", dockW() - 4),
                                dockX, toContent(sy + 1), docked = true,
                            ),
                        )
                        sy += STATS_ROW_H
                    }
                }
            }
        }
        return Layout(tiles, labels, rows, contentH, panelH, x, w, scrollTop, scrollBottom, journalSlotsOut, charTilesOut, pouchSlotOut, pouchXOut, pouchYOut)
    }

    private fun layTiles(into: MutableList<PlacedTile>, slots: List<Int>, gridX: Int, cols: Int, startY: Int): Int {
        var y = startY
        var col = 0
        for (slot in slots) {
            into.add(PlacedTile(slot, gridX + col * TILE_STEP, y))
            col++
            if (col >= cols) {
                col = 0
                y += TILE_STEP
            }
        }

        if (col != 0) {
            val remaining = cols - col
            repeat(remaining) {
                into.add(PlacedTile(-1, gridX + col * TILE_STEP, y))
                col++
            }
            y += TILE_STEP
        }
        return y + SECTION_GAP
    }

    private fun layTilesFiltered(into: MutableList<PlacedTile>, slots: List<Int>, gridX: Int, cols: Int, startY: Int): Int {
        var y = startY
        var col = 0
        for (slot in slots) {
            into.add(PlacedTile(slot, gridX + col * TILE_STEP, y))
            col++
            if (col >= cols) {
                col = 0
                y += TILE_STEP
            }
        }
        if (col != 0) y += TILE_STEP
        return y + SECTION_GAP
    }

    private fun findIngredientPouch(): Int {
        var fallback = -1
        for (slot in MAIN_SLOTS + HOTBAR_SLOTS) {
            val stack = menu.slots.getOrNull(slot)?.item ?: continue
            if (!WynnPouches.isIngredientPouch(stack)) continue
            if (slot == INGREDIENT_POUCH_SLOT) return slot
            if (fallback < 0) fallback = slot
        }
        return fallback
    }

    private fun trimToWidth(text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var t = text
        while (t.length > 1 && font.width("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun slotVisible(slot: Int, query: String): Boolean {
        val stack = slotStack(slot)
        if (stack.isEmpty) return query.isEmpty()
        return matchesQuery(stack, query)
    }

    private fun slotStack(slot: Int): ItemStack {
        if (slot !in 0 until menu.slots.size) return ItemStack.EMPTY
        return menu.slots[slot].item
    }

    private fun plainName(stack: ItemStack): String = if (stack.isEmpty) "" else stack.hoverName.string.trim()

    private fun isExcluded(stack: ItemStack): Boolean = HotbarHudElement.isHidden(stack)

    private fun storedCount(): Int {
        var n = 0
        for (i in 0 until menu.slots.size) {
            if (i in ACCESSORY_EQUIP_SLOTS) continue
            val stack = menu.slots[i].item
            if (!stack.isEmpty && !isExcluded(stack)) n++
        }
        return n
    }

    private fun extraSlots(): List<Int> = (VANILLA_MENU_SIZE until menu.slots.size).toList()

    private fun matchesQuery(stack: ItemStack, query: String): Boolean {
        if (query.isEmpty()) return true
        if (plainName(stack).lowercase().contains(query)) return true
        return WynnItemRarity.loreLines(stack).any { it.lowercase().contains(query) }
    }

    private fun panelWidth(): Int =

        if (invTab == InvTab.CHARACTER || invTab == InvTab.JOURNAL) {
            (width * 0.55).toInt().coerceIn(560, 640).coerceAtMost((width - 20).coerceAtLeast(200))
        } else if (invTab == InvTab.SETTINGS) {
            (width * 0.46).toInt().coerceIn(460, 560).coerceAtMost((width - 20).coerceAtLeast(200))
        } else {
            (width * 0.26).toInt().coerceIn(282, 300).coerceAtMost((width - 20).coerceAtLeast(200))
        }
    private fun dockW(): Int = TILE + GAP + PREVIEW_W + GAP + TILE

    private fun panelLeft(): Int {
        if (invTab == InvTab.SETTINGS && panels.animationsActive) return (width - panelWidth() - MARGIN).coerceAtLeast(MARGIN)
        if (invTab == InvTab.SETTINGS) return ((width - panelWidth()) / 2).coerceAtLeast(MARGIN)
        val total = dockW() + GAP + panelWidth()
        if (total + MARGIN * 2 <= width) return (width - total) / 2 + dockW() + GAP
        return (width - panelWidth() - MARGIN).coerceAtLeast(MARGIN)
    }

    private fun findTile(layout: Layout, x: Int, y: Int): Int {
        for (t in layout.tiles) {
            if (!t.docked) continue
            if (x in t.x until t.x + TILE && y in layout.scrollTop + t.y until layout.scrollTop + t.y + TILE) {
                return t.menuSlot
            }
        }
        if (x < layout.gridX || x >= layout.gridX + layout.gridW) return -1
        if (y < layout.scrollTop || y >= layout.scrollBottom) return -1

        val contentY = y + scrollY - layout.scrollTop

        val row = layout.rows.firstOrNull { contentY in it.y until it.y + ROW_H && x in it.x until it.x + it.w }
        if (row != null) return row.menuSlot
        if (layout.pouchSlot >= 0 && x in layout.pouchX until layout.pouchX + TILE && contentY in layout.pouchY until layout.pouchY + TILE) {
            return layout.pouchSlot
        }
        return layout.tiles.firstOrNull { !it.docked && x in it.x until it.x + TILE && contentY in it.y until it.y + TILE }?.menuSlot ?: -1
    }

    private fun drawOuterPanel(graphics: GuiGraphicsExtractor, panelTop: Int, panelH: Int) {
        val ox = panelLeft()

        graphics.fill(ox - 4, panelTop, ox + panelWidth() + 4, panelTop + panelH, GLASS_BG)
        graphics.fill(ox - 4, panelTop, ox + panelWidth() + 4, panelTop + 1, OwTheme.HAIRLINE)
        graphics.fill(ox - 4, panelTop + panelH - 1, ox + panelWidth() + 4, panelTop + panelH, OwTheme.HAIRLINE)
        graphics.fill(ox - 4, panelTop, ox - 3, panelTop + panelH, OwTheme.HAIRLINE)
        graphics.fill(ox + panelWidth() + 3, panelTop, ox + panelWidth() + 4, panelTop + panelH, OwTheme.HAIRLINE)
    }

    private fun drawFloatingCharacter(graphics: GuiGraphicsExtractor, player: Player?, layout: Layout) {
        for (label in layout.labels) {
            if (!label.docked) continue
            graphics.text(font, label.text, label.x, layout.scrollTop + label.y + 2, OwTheme.TEXT_DIM)
        }
        val box = previewBox
        if (player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics, box[0], layout.scrollTop + box[1], box[0] + box[2],
                layout.scrollTop + box[1] + box[3], PREVIEW_ENTITY_SIZE, 0.0625f,
                mouseX.toFloat(), mouseY.toFloat(), player,
            )
        }
        for (tile in layout.tiles) {
            if (!tile.docked) continue
            drawTile(graphics, tile.copy(y = layout.scrollTop + tile.y), tile.menuSlot == hoveredSlot)
        }
    }

    private fun drawScrollContent(graphics: GuiGraphicsExtractor, player: Player?, layout: Layout) {
        drawFloatingCharacter(graphics, player, layout)
        graphics.enableScissor(layout.gridX, layout.scrollTop, layout.gridX + layout.gridW, layout.scrollBottom)
        for (label in layout.labels) {
            if (label.docked) continue
            val ly = label.y - scrollY + layout.scrollTop
            if (ly + CAPTION_H <= layout.scrollTop || ly >= layout.scrollBottom) continue
            graphics.text(font, label.text, label.x, ly + 2, OwTheme.TEXT_DIM)
        }
        for (row in layout.rows) {
            val ry = row.y - scrollY + layout.scrollTop
            if (ry + ROW_H <= layout.scrollTop || ry >= layout.scrollBottom) continue
            drawRow(graphics, PlacedRow(row.menuSlot, row.x, ry, row.w), row.menuSlot == hoveredSlot)
        }
        for (tile in layout.tiles) {
            if (tile.docked) continue
            val ty = tile.y - scrollY + layout.scrollTop
            if (ty + TILE <= layout.scrollTop || ty >= layout.scrollBottom) continue

            if (tile.menuSlot in HOTBAR_SLOTS) {
                drawTile(graphics, PlacedTile(tile.menuSlot, tile.x, ty), tile.menuSlot == hoveredSlot)
            } else {
                drawLooseItem(graphics, PlacedTile(tile.menuSlot, tile.x, ty), tile.menuSlot == hoveredSlot)
            }
        }
        if (layout.pouchSlot >= 0) {
            val py = layout.pouchY - scrollY + layout.scrollTop
            if (py + TILE > layout.scrollTop && py < layout.scrollBottom) {
                drawLooseItem(graphics, PlacedTile(layout.pouchSlot, layout.pouchX, py), layout.pouchSlot == hoveredSlot)
            }
        }
        graphics.disableScissor()
    }

    private fun drawRow(graphics: GuiGraphicsExtractor, row: PlacedRow, hovered: Boolean) {
        val stack = slotStack(row.menuSlot)
        if (stack.isEmpty) return
        val rarity = WynnItemRarity.of(stack)
        graphics.fill(row.x, row.y, row.x + row.w, row.y + ROW_H, if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG)
        if (rarity != null) graphics.fill(row.x, row.y, row.x + 2, row.y + ROW_H, 0xFF000000.toInt() or rarity.colorRgb)
        graphics.item(stack, row.x + 4, row.y + (ROW_H - 16) / 2)
        val name = stripCodes(plainName(stack))
        val nameColor = rarity?.colorRgb ?: OwTheme.TEXT
        graphics.text(font, name, row.x + 22, row.y + (ROW_H - font.lineHeight) / 2, nameColor)
        val count = "x${stack.count}"
        graphics.text(font, count, row.x + row.w - font.width(count) - 6, row.y + (ROW_H - font.lineHeight) / 2, OwTheme.TEXT_DIM)
    }

    private fun freeSlotCount(): Int =
        (MAIN_SLOTS + HOTBAR_SLOTS)
            .filter { it !in ACCESSORY_EQUIP_SLOTS }
            .count { menu.slots.getOrNull(it)?.item?.isEmpty == true }

    private fun drawFooter(graphics: GuiGraphicsExtractor, panelTop: Int, panelH: Int) {
        val cx = panelLeft() + panelWidth() / 2
        val fy = panelTop + panelH - 4 - FOOTER_H + 3
        val left = "${storedCount()} items \u00B7 "
        val free = freeSlotCount()
        val mid = if (free > 0) "$free free \u00B7 " else "Full \u00B7 "
        val midColor = if (free > 0) OwTheme.TEXT_DIM else OwTheme.BAD
        val right = "%,d emeralds".format(WynnPouches.totalEmeralds(menu.slots.map { it.item }))
        val x0 = cx - (font.width(left) + font.width(mid) + font.width(right)) / 2
        graphics.text(font, left, x0, fy, OwTheme.TEXT_DIM)
        graphics.text(font, mid, x0 + font.width(left), fy, midColor)
        graphics.text(font, right, x0 + font.width(left) + font.width(mid), fy, OwTheme.GOOD)
    }

    private fun selectTab(tab: InvTab) {
        if (tab == invTab) {
            if (tab == InvTab.JOURNAL && journalMenu == null) enterJournal()
            else if (tab == InvTab.CHARACTER && characterMenu == null) enterCharacter()
            return
        }
        if (tab == InvTab.INVENTORY) {
            OverwatchInventory.pendingTransitionTab = InvTab.INVENTORY
        }
        when (tab) {
            InvTab.JOURNAL -> if (!enterJournal()) return
            InvTab.CHARACTER -> if (!enterCharacter()) return

            InvTab.INVENTORY -> if (leaveContainers()) reopenVanillaInventory()

            else -> if (tab != InvTab.SETTINGS) leaveContainers()
        }
        painting = false
        painted.clear()
        lastClickSlot = -1
        invTab = tab
        rebuildWidgets()
    }

    private fun enterJournal(): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (!menu.carried.isEmpty) {
            blockReason("Place the held item first")
            return false
        }
        if (ContentBookQuery.isActive) {
            blockReason("Still working on the book -- try again in a moment")
            return false
        }
        if (journalMenu != null && player.containerMenu === journalMenu) return true

        val slot = ContentBookInterceptor.findBookSlot(player)

        OverwatchInventory.pendingTransitionTab = InvTab.JOURNAL
        val closed = leaveContainers()
        journal.update(ContentBookCache.snapshot ?: emptyList())
        journal.actionMessage = null
        if (slot != null) return fireJournalTrigger(slot)

        if (closed) {
            journal.actionMessage = "Opening..."
            reopenVanillaInventory()
        } else {
            journal.actionMessage = "Couldn't find the Content Book in your inventory"
        }
        return true
    }

    private fun ensureInventoryMenu(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        if (player.containerMenu === menu) return true
        if (journalMenu != null || characterMenu != null) return false

        val shown = Minecraft.getInstance().gui.screen()
        if (ContentBookInterceptor.pendingJournalHost?.let { shown !== it } == true) {
            ContentBookInterceptor.pendingJournalHost = null
        }
        if (CharacterInfo.pendingCharacterHost?.let { shown !== it } == true) {
            CharacterInfo.pendingCharacterHost = null
        }
        if (ContentBookInterceptor.pendingJournalHost != null || CharacterInfo.pendingCharacterHost != null) return false

        try {
            player.closeContainer()
        } catch (t: Throwable) {
            Overwatch.LOGGER.warn("ensureInventoryMenu: closeContainer threw", t)
        }
        if (player.containerMenu !== player.inventoryMenu) {
            player.containerMenu = player.inventoryMenu
        }
        return player.containerMenu === menu
    }

    private fun fireJournalTrigger(menuSlot: Int? = null): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (journalMenu != null) return true
        if (!ensureInventoryMenu()) {
            blockReason("Reopen the journal to refresh")
            return false
        }
        val slot = menuSlot ?: ContentBookInterceptor.findBookSlot(player)
        if (slot == null) {
            blockReason("Couldn't find the Content Book in your inventory")
            return false
        }
        val error = ContentBookInterceptor.openJournalContainer(client, this, slot)
        if (error != null) {
            blockReason(error)
            return false
        }
        return true
    }

    private fun enterCharacter(): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (!menu.carried.isEmpty) {
            blockReason("Place the held item first")
            return false
        }
        if (characterMenu != null && player.containerMenu === characterMenu) return true

        val slot = CharacterInfo.findInfoSlot()

        OverwatchInventory.pendingTransitionTab = InvTab.CHARACTER
        val closed = leaveContainers()
        if (slot != null) return fireCharacterTrigger(slot)

        if (closed) {
            reopenVanillaInventory()
        } else {
            blockReason("Character Info item not found in your inventory")
        }
        return true
    }

    private fun fireCharacterTrigger(menuSlot: Int? = null): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (characterMenu != null) return true
        if (!ensureInventoryMenu()) {
            blockReason("Reopen Character Info to refresh")
            return false
        }
        val slot = menuSlot ?: CharacterInfo.findInfoSlot()
        if (slot == null) {
            blockReason("Character Info item not found in your inventory")
            return false
        }
        val error = CharacterInfo.openCharacterContainer(client, this, slot)
        if (error != null) {
            blockReason(error)
            return false
        }
        return true
    }

    private fun reopenVanillaInventory() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== player.inventoryMenu) player.containerMenu = player.inventoryMenu
        client.gui.setScreen(InventoryScreen(player))
    }

    private fun leaveContainers(): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player
        if (journalMenu == null && characterMenu == null) {
            if (ContentBookInterceptor.pendingJournalHost === this) ContentBookInterceptor.pendingJournalHost = null
            if (CharacterInfo.pendingCharacterHost === this) CharacterInfo.pendingCharacterHost = null
            pendingTabFire = null
            return false
        }
        ContentBookQuery.cancel()
        journalBusy = false
        try {
            player?.closeContainer()
        } catch (t: Throwable) {
            Overwatch.LOGGER.warn("leaveContainers: closeContainer threw", t)
        }

        if (player != null && player.containerMenu !== player.inventoryMenu) {
            player.containerMenu = player.inventoryMenu
        }
        journalMenu = null
        characterMenu = null
        if (ContentBookInterceptor.pendingJournalHost === this) ContentBookInterceptor.pendingJournalHost = null
        if (CharacterInfo.pendingCharacterHost === this) CharacterInfo.pendingCharacterHost = null
        pendingTabFire = null
        return true
    }

    fun attachJournalMenu(menu: AbstractContainerMenu) {
        OverwatchInventory.pendingTransitionTab = null
        journalMenu = menu
        journal.update(ContentBookCache.snapshot ?: emptyList())
        journal.actionMessage = null
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
        if (ContentBookCache.needsRefresh()) refreshJournal()
    }

    fun attachCharacterMenu(menu: AbstractContainerMenu) {
        OverwatchInventory.pendingTransitionTab = null
        characterMenu = menu
        combatInfoPager.reset()
        identityCache = null
        charLoadTicks = 0
        charWasLoading = true
        if (invTab == InvTab.CHARACTER) rebuildWidgets()
    }

    fun updateBookActivities(list: List<ActivityInfo>) {
        journal.update(list)
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
    }

    private fun refreshJournal() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val menu = journalMenu ?: return
        if (player.containerMenu !== menu || journalBusy || ContentBookQuery.isActive) return
        journalBusy = true
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
        ContentBookQuery.start(
            menu = menu,
            seed = journal.activities,
            onProgress = { acts -> if (isJournalLive(menu)) { journal.update(acts); refreshJournalView() } },
            onComplete = { acts ->
                ContentBookCache.commit(acts)
                journalBusy = false
                if (isJournalLive(menu)) { journal.update(acts); refreshJournalView() }
            },
            onFailed = {
                journalBusy = false
                if (isJournalLive(menu)) showJournalMessage("Refresh failed")
            },
        )
    }

    private fun isJournalLive(menu: AbstractContainerMenu): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        return player.containerMenu === menu && journalMenu === menu &&
            invTab == InvTab.JOURNAL && client.gui.screen() === this
    }

    private fun refreshJournalView() {
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
    }

    private fun showJournalMessage(message: String) {
        journal.actionMessage = message
        refreshJournalView()
    }

    private fun blockReason(message: String) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(message), false)
        journal.actionMessage = message
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
    }

    private fun toggleJournalTrack(activity: ActivityInfo) {
        if (activity.trackingState == ActivityTrackingState.UNTRACKABLE) {
            if (DiscoveryTracker.toggle(activity)) rebuildWidgets() else showJournalMessage("${activity.name} can't be tracked")
            return
        }
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val menu = journalMenu
        if (menu == null || player.containerMenu !== menu) {
            showJournalMessage("Reopen the journal to track")
            return
        }
        if (ContentBookQuery.isEnumerating) {
            ContentBookQuery.cancel()
            journalBusy = false
        }
        if (journalBusy) {
            showJournalMessage("Still working on the last change")
            return
        }
        journalBusy = true
        ContentBookQuery.startTrackToggle(
            menu = menu,
            type = activity.type,
            name = activity.name,
            onFound = {
                journalBusy = false
                journal.applyTrackToggleLocal(activity)
                refreshJournalView()
            },
            onFailed = {
                journalBusy = false
                if (isJournalLive(menu)) showJournalMessage("Couldn't find ${activity.name} in the book")
            },
        )
    }

    private fun dockedStack(slot: Int): ItemStack {
        val live = if (slot in 0 until menu.slots.size) menu.slots[slot].item else ItemStack.EMPTY
        if (!live.isEmpty) {
            val cached = dockedStackCache[slot]
            if (cached == null || cached.isEmpty || !ItemStack.isSameItemSameComponents(cached, live) || cached.count != live.count) {
                dockedStackCache[slot] = live.copy()
            }
            return live
        }
        return dockedStackCache[slot] ?: ItemStack.EMPTY
    }

    private fun drawTile(graphics: GuiGraphicsExtractor, tile: PlacedTile, hovered: Boolean) {
        if (tile.menuSlot < 0) return
        val stack = if (tile.docked) dockedStack(tile.menuSlot) else slotStack(tile.menuSlot)

        val rarityRgb = if (stack.isEmpty) null else WynnItemRarity.of(stack)?.colorRgb
        graphics.fill(tile.x, tile.y, tile.x + TILE, tile.y + TILE, rarityRgb?.let { TILE_TINT_ALPHA or it } ?: TILE_BG_EMPTY)
        if (!stack.isEmpty) {
            graphics.item(stack, tile.x + 2, tile.y + 2)
            graphics.itemDecorations(font, stack, tile.x + 2, tile.y + 2)
        }
        val border = if (stack.isEmpty) OwTheme.TILE_BORDER else (rarityRgb?.let { 0xFF000000.toInt() or it } ?: OwTheme.TILE_BORDER)
        graphics.outline(tile.x, tile.y, TILE, TILE, border)
        if (hovered) graphics.outline(tile.x - 1, tile.y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun drawLooseItem(graphics: GuiGraphicsExtractor, tile: PlacedTile, hovered: Boolean) {
        if (tile.menuSlot < 0) return
        val stack = slotStack(tile.menuSlot)
        if (stack.isEmpty) return
        graphics.item(stack, tile.x + 2, tile.y + 2)
        graphics.itemDecorations(font, stack, tile.x + 2, tile.y + 2)
        if (hovered) graphics.outline(tile.x - 1, tile.y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun drawHoverAndTooltip(graphics: GuiGraphicsExtractor, layout: Layout) {        if (hoveredSlot < 0) return
        val stack = slotStack(hoveredSlot)
        if (stack.isEmpty) return
        if (!menu.carried.isEmpty) return
        graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        EquipCompareTooltip.draw(graphics, font, stack, mouseX, mouseY)
    }

    private fun findJournalSlot(layout: Layout, x: Int, y: Int): JournalSlot? {
        if (x < layout.gridX || x >= layout.gridX + layout.gridW) return null
        val header = computeJournalHeader(layout.gridW)
        val listTop = layout.scrollTop + header.listTop
        val listBottom = minOf(layout.scrollBottom, listTop + JOURNAL_LIST_ROWS * ContentBookViewModel.ROW_H)
        if (y < listTop || y >= listBottom) return null
        val contentY = y + journalScrollY - layout.scrollTop
        val colW = layout.gridW / ContentBookViewModel.LIST_COLS
        return layout.journalSlots.firstOrNull { x in it.x until it.x + colW && contentY in it.y until it.y + ContentBookViewModel.ROW_H }
    }

    private fun drawJournalContent(graphics: GuiGraphicsExtractor, player: Player?, layout: Layout) {
        drawFloatingCharacter(graphics, player, layout)
        val gx = layout.gridX
        val gw = layout.gridW
        val top = layout.scrollTop
        graphics.text(font, "JOURNAL", gx, top + 2, OwTheme.ACCENT)
        val header = computeJournalHeader(gw)
        for ((i, line) in header.detailLines.take(JOURNAL_MAX_DETAIL_LINES).withIndex()) {
            graphics.text(font, trimToWidth(line.text, gw), gx, top + header.detailTop + i * JOURNAL_DETAIL_LINE_H, line.color)
        }
        graphics.text(font, journal.statusLine(), gx, top + header.statusTop, OwTheme.TEXT_DIM)
        val listTop = top + header.listTop
        val listBottom = minOf(layout.scrollBottom, listTop + JOURNAL_LIST_ROWS * ContentBookViewModel.ROW_H)
        if (journalMenu == null && journal.activities.isEmpty()) {
            graphics.text(font, "Opening the Content Book...", gx, listTop, OwTheme.TEXT_DIM)
        }
        val colW = gw / ContentBookViewModel.LIST_COLS
        graphics.enableScissor(gx, listTop, gx + gw, listBottom)
        for (slot in layout.journalSlots) {
            val sy = slot.y - journalScrollY + top
            if (sy + ContentBookViewModel.ROW_H <= listTop || sy >= listBottom) continue
            drawJournalRow(graphics, slot.x, sy, colW, slot.activity, slot == hoveredJournal)
        }
        graphics.disableScissor()
    }

    private fun drawJournalRow(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, a: ActivityInfo, hovered: Boolean) {
        val rowH = ContentBookViewModel.ROW_H - 2
        val selected = a === selectedJournal
        val bg = when {
            selected -> OwTheme.TILE_HOVER
            hovered -> OwTheme.PANEL_RAISED
            else -> OwTheme.TILE_BG
        }
        graphics.fill(x, y, x + w - 2, y + rowH, bg)
        graphics.outline(x, y, w - 2, rowH, if (selected) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        graphics.item(a.icon, x + 2, y + 1)
        val prefix = if (a.trackingState == ActivityTrackingState.TRACKED) "* " else ""
        val name = trimToWidth("$prefix${a.name}", w - 22 - 10)
        graphics.text(font, name, x + 22, y + (rowH - 8) / 2, (a.type.colorArgb and 0xFFFFFF) or 0xFF000000.toInt())
        graphics.fill(x + w - 8, y + 2, x + w - 4, y + rowH - 2, statusColorArgb(a.status))
    }

    private fun drawJournalHoverAndTooltip(graphics: GuiGraphicsExtractor) {
        val slot = hoveredJournal ?: return
        if (!menu.carried.isEmpty) return
        graphics.setComponentTooltipForNextFrame(font, buildJournalTooltip(slot.activity), mouseX, mouseY)
    }

    private fun journalClick(x: Int, y: Int, button: Int): Boolean {
        val slot = findJournalSlot(lastLayout ?: return true, x, y) ?: return true
        if (button == 1) {
            Minecraft.getInstance().setScreenAndShow(OverwatchQuestWikiScreen(slot.activity.type, slot.activity.name, this))
            return true
        }
        if (button != 0) return true
        selectedJournal = slot.activity
        rebuildWidgets()
        return true
    }

    private fun statusColorArgb(status: ActivityStatus): Int = when (status) {
        ActivityStatus.STARTED -> 0xFFFFD700.toInt()
        ActivityStatus.AVAILABLE -> 0xFF55FFFF.toInt()
        ActivityStatus.UNAVAILABLE -> 0xFFFF5555.toInt()
        ActivityStatus.COMPLETED -> 0xFF55FF55.toInt()
    }

    private fun buildJournalTooltip(a: ActivityInfo): List<Component> {
        val lines = ArrayList<Component>()
        lines.add(Component.literal(a.name).withColor(a.type.colorArgb and 0xFFFFFF))
        lines.add(Component.literal("${a.type.filterName} - ${statusLabel(a.status)}").withStyle(statusColor(a.status)))
        a.specialInfo?.let { lines.add(Component.literal(it).withStyle(ChatFormatting.GRAY)) }
        a.description?.let { desc ->
            for (wrapped in wrapLines(desc, 200)) lines.add(Component.literal(wrapped).withStyle(ChatFormatting.GRAY))
        }
        if (a.levelReq > 0) {
            lines.add(Component.literal("Combat Lv. Min: ${a.levelReq}").withStyle(if (a.levelReqFulfilled) ChatFormatting.GREEN else ChatFormatting.RED))
        }
        for (req in a.professionReqs) {
            lines.add(Component.literal("${req.profession} Lv. Min: ${req.level}").withStyle(if (req.fulfilled) ChatFormatting.GREEN else ChatFormatting.RED))
        }
        for (req in a.questReqs) {
            lines.add(Component.literal("Quest: ${req.questName}").withStyle(if (req.fulfilled) ChatFormatting.GREEN else ChatFormatting.RED))
        }
        a.length?.let { lines.add(Component.literal("Length: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}").withStyle(ChatFormatting.GRAY)) }
        a.distance?.let { lines.add(Component.literal("Distance: ${it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)}").withStyle(ChatFormatting.GRAY)) }
        a.difficulty?.let { lines.add(Component.literal("Difficulty: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}").withStyle(ChatFormatting.GRAY)) }
        for ((_, items) in a.rewards) {
            for (reward in items) lines.add(Component.literal("- $reward").withStyle(ChatFormatting.LIGHT_PURPLE))
        }
        when (a.trackingState) {
            ActivityTrackingState.TRACKED -> lines.add(Component.literal("Tracked - click to untrack").withStyle(ChatFormatting.YELLOW))
            ActivityTrackingState.TRACKABLE -> lines.add(Component.literal("Click to track").withStyle(ChatFormatting.DARK_GRAY))
            ActivityTrackingState.UNTRACKABLE -> {}
        }
        lines.add(Component.literal("Right-click for wiki info").withStyle(ChatFormatting.DARK_GRAY))
        return lines
    }

    private fun statusLabel(status: ActivityStatus): String = when (status) {
        ActivityStatus.STARTED -> "In progress"
        ActivityStatus.AVAILABLE -> "Available"
        ActivityStatus.UNAVAILABLE -> "Locked"
        ActivityStatus.COMPLETED -> "Completed"
    }

    private fun trackLabel(a: ActivityInfo): String = when (a.trackingState) {
        ActivityTrackingState.TRACKED -> "Untrack"
        ActivityTrackingState.TRACKABLE -> "Track"
        ActivityTrackingState.UNTRACKABLE -> "Can't Track"
    }

    private fun statusColor(status: ActivityStatus): ChatFormatting = when (status) {
        ActivityStatus.STARTED -> ChatFormatting.YELLOW
        ActivityStatus.AVAILABLE -> ChatFormatting.AQUA
        ActivityStatus.UNAVAILABLE -> ChatFormatting.RED
        ActivityStatus.COMPLETED -> ChatFormatting.GREEN
    }

    private fun wrapLines(text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private data class CategoryChip(val x: Int, val y: Int, val w: Int, val value: String?)

    private fun journalCategoryChips(left: Int, top: Int, width: Int): List<CategoryChip> {
        val chips = ArrayList<CategoryChip>()
        var cx = left
        var cy = top
        for (value in journal.filterOptions()) {
            val label = value ?: "All"
            val chipW = font.width(label) + 12
            if (cx != left && cx + chipW > left + width) {
                cx = left
                cy += JOURNAL_TAB_H + 2
            }
            chips.add(CategoryChip(cx, cy, chipW, value))
            cx += chipW + 2
        }
        return chips
    }

    private data class DetailLine(val text: String, val color: Int)

    private fun buildJournalDetailLines(a: ActivityInfo, maxWidth: Int): List<DetailLine> {
        val lines = ArrayList<DetailLine>()
        lines.add(DetailLine(a.name, a.type.colorArgb))
        lines.add(DetailLine("${a.type.filterName} -- ${statusLabel(a.status)}", statusColorArgb(a.status)))
        a.specialInfo?.let { lines.add(DetailLine(it, OwTheme.TEXT_DIM)) }
        a.description?.let { desc -> for (w in wrapLines(desc, maxWidth)) lines.add(DetailLine(w, OwTheme.TEXT_DIM)) }
        if (a.levelReq > 0) {
            lines.add(DetailLine("Combat Lv. Min: ${a.levelReq}", if (a.levelReqFulfilled) OwTheme.GOOD else OwTheme.BAD))
        }
        for (req in a.professionReqs) {
            lines.add(DetailLine("${req.profession} Lv. Min: ${req.level}", if (req.fulfilled) OwTheme.GOOD else OwTheme.BAD))
        }
        for (req in a.questReqs) {
            lines.add(DetailLine("Quest: ${req.questName}", if (req.fulfilled) OwTheme.GOOD else OwTheme.BAD))
        }
        a.length?.let { lines.add(DetailLine("Length: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}", OwTheme.TEXT_DIM)) }
        a.distance?.let { lines.add(DetailLine("Distance: ${it.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)}", OwTheme.TEXT_DIM)) }
        a.difficulty?.let { lines.add(DetailLine("Difficulty: ${it.name.lowercase().replaceFirstChar(Char::uppercase)}", OwTheme.TEXT_DIM)) }
        if (a.rewards.isNotEmpty()) {
            lines.add(DetailLine("Rewards", OwTheme.ACCENT))
            for ((_, items) in a.rewards) for (reward in items) lines.add(DetailLine("- $reward", OwTheme.TEXT))
        }
        return lines
    }

    private data class JournalHeader(
        val chips: List<CategoryChip>,
        val detailLines: List<DetailLine>,
        val detailTop: Int,
        val actionsTop: Int,
        val statusTop: Int,
        val listTop: Int,
    )

    private fun computeJournalHeader(width: Int): JournalHeader {
        val chips = journalCategoryChips(0, JOURNAL_ROW1_H + JOURNAL_SECTION_GAP, width)
        val chipsBottom = (chips.maxOfOrNull { it.y } ?: (JOURNAL_ROW1_H + JOURNAL_SECTION_GAP)) + JOURNAL_TAB_H
        val detailTop = chipsBottom + JOURNAL_SECTION_GAP
        val selected = selectedJournal
        val detailLines = if (selected != null) {
            buildJournalDetailLines(selected, width - 8)
        } else {
            listOf(DetailLine("Select an activity from the list below to see its details.", OwTheme.TEXT_DIM))
        }
        val actionsTop = detailTop + minOf(detailLines.size, JOURNAL_MAX_DETAIL_LINES) * JOURNAL_DETAIL_LINE_H + 4
        val statusTop = actionsTop + JOURNAL_ACTION_BTN_H + JOURNAL_SECTION_GAP
        val listTop = statusTop + 12
        return JournalHeader(chips, detailLines, detailTop, actionsTop, statusTop, listTop)
    }

    private fun charScreenY(contentY: Int, panelTop: Int): Int = panelTop + CONTENT_TOP_REL + contentY - charScrollY

    private fun findCharTile(layout: Layout, x: Int, y: Int): Int {
        if (y < layout.scrollTop + CHAR_STRIP_H || y >= layout.scrollBottom) return -1
        val contentY = y + charScrollY - layout.scrollTop
        return charHoverZones.firstOrNull { x in it.x until it.x + it.w && contentY in it.y until it.y + it.h }?.slot ?: -1
    }

    private fun charSlotStack(slot: Int): ItemStack {
        val menu = characterMenu ?: return ItemStack.EMPTY
        if (slot !in 0 until menu.slots.size) return ItemStack.EMPTY
        return menu.slots[slot].item
    }

    private fun drawCharStrip(graphics: GuiGraphicsExtractor, layout: Layout) {
        val gx = layout.gridX
        val gw = layout.gridW
        val top = layout.scrollTop
        val menu = characterMenu
        val sections = menu?.let { charSections(it) }
        fun value(lines: List<Pair<String, Int>>, key: String): String =
            lines.firstOrNull { it.first.trimStart().startsWith(key) }?.first?.substringAfter(":")?.trim() ?: "--"
        val name = trimToWidth(sections?.name ?: "--", gw)
        graphics.text(font, name, gx, top + CHAR_STRIP_TEXT_Y + 4, OwTheme.ACCENT)
        val summary = buildString {
            append("Lv ${sections?.let { value(it.identity, "Total Lv:") } ?: "--"}")
            append("  ·  Combat ${sections?.let { value(it.identity, "Combat Lv:") } ?: "--"}")
            append("  ·  ${sections?.let { value(it.identity, "Class:") } ?: "--"}")
        }
        graphics.text(font, trimToWidth(summary, gw), gx, top + CHAR_STRIP_TEXT_Y + 15, OwTheme.TEXT_DIM)
        val (skillPoints, abilityPoints) = CharacterInfo.readPoints(CharacterInfo.infoStack())
        val xp = sections?.let { value(it.identity, "XP:") } ?: "--"
        val spText = "Skill Points: ${skillPoints?.toString() ?: "--"}"
        graphics.text(font, spText, gx, top + CHAR_STRIP_TEXT_Y + 28, if ((skillPoints ?: 0) > 0) OwTheme.GOOD else OwTheme.TEXT_DIM)
        var px = gx + font.width(spText) + 12
        val apText = "Ability Points: ${abilityPoints?.toString() ?: "--"}"
        graphics.text(font, apText, px, top + CHAR_STRIP_TEXT_Y + 28, if ((abilityPoints ?: 0) > 0) OwTheme.GOOD else OwTheme.TEXT_DIM)
        px += font.width(apText) + 12
        graphics.text(font, trimToWidth("XP: $xp", (gx + gw - px).coerceAtLeast(20)), px, top + CHAR_STRIP_TEXT_Y + 28, OwTheme.TEXT_DIM)
    }

    private fun drawCharacterContent(graphics: GuiGraphicsExtractor, player: Player?, layout: Layout) {
        drawFloatingCharacter(graphics, player, layout)
        drawCharStrip(graphics, layout)
        if (characterMenu == null) {
            graphics.text(font, "Opening Character Info...", layout.gridX, layout.scrollTop + CHARACTER_GRID_Y, OwTheme.TEXT_DIM)
            return
        }

        val gx = layout.gridX
        val gw = layout.gridW
        val leftW = (gw * 0.44).toInt()
        val rightW = gw - leftW - CHAR_CARD_GAP
        val rightX = gx + leftW + CHAR_CARD_GAP
        val contentTop = layout.scrollTop + CHAR_STRIP_H
        graphics.enableScissor(gx, contentTop, gx + gw, layout.scrollBottom)
        for (label in layout.labels) {
            if (label.docked) continue
            val ly = label.y - charScrollY + layout.scrollTop
            if (ly + STATS_ROW_H <= contentTop || ly >= layout.scrollBottom) continue
            val maxW = if (label.maxW > 0) label.maxW
                else if (label.x < rightX) rightX - label.x - 4 else gx + gw - label.x - 4
            graphics.text(font, trimToWidth(label.text, maxW.coerceAtLeast(20)), label.x, ly + 2, label.color)
        }
        val snap = charSnapshot
        if (snap != null) {
            val gridTop = CHARACTER_GRID_Y

            for ((i, skill) in snap.skills.withIndex()) {
                val sy = gridTop + HEADER_H + i * SKILL_ROW_H - charScrollY + layout.scrollTop
                if (sy + SKILL_ROW_H <= contentTop || sy >= layout.scrollBottom) continue
                val stack = charSlotStack(skill.slot)
                if (!stack.isEmpty) graphics.item(stack, rightX, sy + 1)
                val pts = if (skill.points < 0) "…" else "${skill.points} pts"
                val label = "${skill.name}: $pts (${skill.percent})"
                if (skill.isConfirm) {
                    graphics.text(font, "✓ $label -- click again to confirm", rightX + 20, sy + 5, OwTheme.GOOD)
                } else {
                    graphics.text(font, trimToWidth(label, rightW - 68), rightX + 20, sy + 5, OwTheme.TEXT)
                }
                skill.percent.removeSuffix("%").toFloatOrNull()?.let { pct ->
                    val barX = rightX + 20
                    val barW = rightW - 20 - 48
                    val barY = sy + SKILL_ROW_H - BAR_H - 2
                    graphics.fill(barX, barY, barX + barW, barY + BAR_H, OwTheme.TILE_BORDER)
                    val fillW = (barW * (pct / 100f).coerceIn(0f, 1f)).toInt()
                    if (fillW > 0) graphics.fill(barX, barY, barX + fillW, barY + BAR_H, OwTheme.ACCENT)
                }
            }
        }
        graphics.disableScissor()
    }

    private fun drawCharTile(graphics: GuiGraphicsExtractor, slot: Int, x: Int, y: Int, hovered: Boolean) {
        val stack = charSlotStack(slot)
        val rarityRgb = if (stack.isEmpty) null else WynnItemRarity.of(stack)?.colorRgb
        graphics.fill(x, y, x + TILE, y + TILE, rarityRgb?.let { TILE_TINT_ALPHA or it } ?: TILE_BG_EMPTY)
        if (!stack.isEmpty) {
            graphics.item(stack, x + 2, y + 2)
            graphics.itemDecorations(font, stack, x + 2, y + 2)
        }
        val border = if (stack.isEmpty) OwTheme.TILE_BORDER else (rarityRgb?.let { 0xFF000000.toInt() or it } ?: OwTheme.TILE_BORDER)
        graphics.outline(x, y, TILE, TILE, border)
        if (hovered) graphics.outline(x - 1, y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun drawCharHoverAndTooltip(graphics: GuiGraphicsExtractor, layout: Layout) {
        if (hoveredChar < 0) return
        val stack = charSlotStack(hoveredChar)
        if (stack.isEmpty) return
        if (!(characterMenu?.carried?.isEmpty ?: true)) return
        graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
    }

    private fun drawCharButtonTooltip(graphics: GuiGraphicsExtractor) {
        if (hoveredChar >= 0) return
        if (!(characterMenu?.carried?.isEmpty ?: true)) return
        for ((button, slot) in charButtonSlots) {
            if (!button.visible || !button.isMouseOver(mouseX.toDouble(), mouseY.toDouble())) continue
            val stack = charSlotStack(slot)
            if (stack.isEmpty) return
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
            return
        }
    }

    private fun characterClick(x: Int, y: Int, button: Int, event: MouseButtonEvent): Boolean {
        val slot = findCharTile(lastLayout ?: return true, x, y)
        val menu = characterMenu
        if (slot < 0 || menu == null) {
            if (menu != null && !menu.carried.isEmpty && (button == 0 || button == 1)) {
                sendCharInput(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
            }
            return true
        }
        val player = Minecraft.getInstance().player ?: return true
        if (player.containerMenu !== menu) return true
        when (button) {
            0 -> {
                if (event.hasShiftDown()) {
                    sendCharInput(slot, 0, ContainerInput.QUICK_MOVE)
                } else {
                    val now = System.currentTimeMillis()
                    if (slot == lastClickSlot && now - lastClickTime < DOUBLE_CLICK_MS && !menu.carried.isEmpty) {
                        sendCharInput(slot, 0, ContainerInput.PICKUP_ALL)
                    } else {
                        sendCharInput(slot, 0, ContainerInput.PICKUP)
                    }
                    lastClickSlot = slot
                    lastClickTime = now
                }
                return true
            }
            1 -> {
                if (event.hasShiftDown()) sendCharInput(slot, 1, ContainerInput.QUICK_MOVE)
                else sendCharInput(slot, 1, ContainerInput.PICKUP)
                return true
            }
            2 -> {
                if (player.abilities.instabuild) sendCharInput(slot, 2, ContainerInput.CLONE)
                return true
            }
        }
        return false
    }

    private fun charLoading(): Boolean = characterMenu != null && !combatInfoPager.done

    private fun sendCharInput(slot: Int, button: Int, kind: ContainerInput) {
        lastStatClickNanos = System.nanoTime()
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val menu = characterMenu ?: return
        if (player.containerMenu !== menu) {
            Overwatch.LOGGER.warn("Overwatch character tab: menu no longer current, dropping {} input", kind)
            return
        }
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            Overwatch.LOGGER.error("Overwatch character tab input failed", t)
        }
    }

    private data class CharSections(
        val name: String,
        val identity: List<Pair<String, Int>>,
        val combat: List<Pair<String, Int>>,
        val professions: List<Pair<String, Int>>,
    )

    private fun layoutFlow(items: List<Pair<String, Int>>, labels: MutableList<PlacedLabel>, x: Int, startY: Int, w: Int): Int {
        var cx = x
        var cy = startY
        for ((text, slot) in items) {
            val cellW = (font.width(text) + FLOW_PAD).coerceAtMost(w)
            if (cx + cellW > x + w && cx > x) {
                cx = x
                cy += STATS_ROW_H
            }
            labels.add(PlacedLabel(text, cx + 2, cy + 1, maxW = cellW - 4))
            charHoverZones.add(CharZone(cx, cy, cellW, STATS_ROW_H, slot))
            cx += cellW
        }
        if (cx > x) cy += STATS_ROW_H
        return cy
    }

    private val SECTION_MARKERS = listOf("Combat", "Professions")

    private fun charSections(menu: AbstractContainerMenu): CharSections {
        val lines = readCharacterStats(menu)
        if (lines.isEmpty()) return CharSections("--", emptyList(), emptyList(), emptyList())
        val first = lines.first().first
        val isName = !first.startsWith("  ") && first !in SECTION_MARKERS
        val name = if (isName) first else "--"
        val rest = if (isName) lines.drop(1) else lines

        val markerIdx = SECTION_MARKERS.associateWith { m -> rest.indexOfFirst { it.first == m } }.filterValues { it >= 0 }
        val boundaries = markerIdx.values.sorted() + rest.size
        fun sectionAfter(marker: String): List<Pair<String, Int>> {
            val idx = markerIdx[marker] ?: return emptyList()
            val end = boundaries.first { it > idx }
            return rest.subList(idx + 1, end)
        }
        val identity = rest.subList(0, markerIdx.values.minOrNull() ?: rest.size)
        val combat = sectionAfter("Combat")
        val levelLine = Regex("""Lv\.\s*\d+""")
        val professions = sectionAfter("Professions")
            .map { (text, slot) -> text.trimStart(' ', '-', '–', '•') to slot }
            .filter { (text, _) -> levelLine.containsMatchIn(text) }
        return CharSections(name, identity, combat, professions)
    }

    private class CombatInfoPager {
        var slot = -1
            private set
        var done = false
            private set
        private val seenPages = HashSet<Int>()
        private val merged = LinkedHashSet<String>()
        private var ticksUntilNext = 0

        fun forceDone() {
            done = true
        }

        fun reset() {
            slot = -1
            seenPages.clear()
            merged.clear()
            ticksUntilNext = 0
            done = false
        }

        fun result(): List<String> = merged.toList()

        fun tick(menu: AbstractContainerMenu, click: (Int) -> Unit) {
            if (slot < 0) {
                slot = findSlot(menu)
                if (slot < 0) return
            }
            if (done) return
            val stack = menu.slots.getOrNull(slot)?.item ?: return
            if (stack.isEmpty) return
            if (ticksUntilNext > 0) {
                ticksUntilNext--
                return
            }
            val lore = WynnItemRarity.loreLines(stack)
            val statEnd = lore.indexOfLast { line -> PLAYER_STAT_KEYS.any { key -> line.startsWith(key) } }
            val pageIdx = lore.indexOfFirst { PAGE_LINE.containsMatchIn(it) }
            val pageNum = if (pageIdx >= 0) PAGE_LINE.find(lore[pageIdx])?.groupValues?.get(1)?.toIntOrNull() else null
            val contentStart = if (statEnd >= 0) statEnd + 1 else 0
            val contentEnd = if (pageIdx >= 0) pageIdx else lore.size
            if (contentStart >= contentEnd) {
                done = true
                return
            }
            val content = lore.subList(contentStart, contentEnd).map { it.trim() }.filter { it.isNotEmpty() }
            merged.addAll(content)
            if (pageNum == null || !seenPages.add(pageNum) || merged.size >= MAX_LINES || seenPages.size >= MAX_PAGES) {
                done = true
                return
            }
            click(slot)
            ticksUntilNext = RESYNC_TICKS
        }

        private fun findSlot(menu: AbstractContainerMenu): Int {
            val total = menu.slots.size
            val ownSlots = if (total > PLAYER_INV_SIZE) total - PLAYER_INV_SIZE else total
            for (s in 0 until ownSlots) {
                val stack = menu.slots[s].item
                if (stack.isEmpty) continue
                if (WynnItemRarity.loreLines(stack).any { it.startsWith("Total Lv:") }) return s
            }
            return -1
        }

        private companion object {
            val PAGE_LINE = Regex("""Page (\d+)""")
            const val RESYNC_TICKS = 3
            const val MAX_PAGES = 8
            const val MAX_LINES = 80
        }
    }

    private var identityCache: List<Pair<String, Int>>? = null

    private fun readCharacterStats(menu: AbstractContainerMenu): List<Pair<String, Int>> {
        val lines = ArrayList<Pair<String, Int>>()
        val total = menu.slots.size
        val ownSlots = if (total > PLAYER_INV_SIZE) total - PLAYER_INV_SIZE else total
        for (slot in 0 until ownSlots) {
            val stack = menu.slots[slot].item
            if (stack.isEmpty) {
                if (slot == combatInfoPager.slot) identityCache?.let { lines.addAll(it) }
                continue
            }
            val lore = WynnItemRarity.loreLines(stack)
            if (lore.isEmpty()) {
                if (slot == combatInfoPager.slot) identityCache?.let { lines.addAll(it) }
                continue
            }
            when {
                lore.any { it.startsWith("Total Lv:") } -> {
                    val identityLines = ArrayList<Pair<String, Int>>()
                    identityLines.add(stripCodes(stack.hoverName.string).trim() to slot)
                    for (key in PLAYER_STAT_KEYS) {
                        lore.firstOrNull { it.startsWith(key) }?.let { identityLines.add("  $it" to slot) }
                    }
                    identityCache = identityLines
                    lines.addAll(identityLines)
                    if (combatInfoPager.done) {
                        val combat = combatInfoPager.result()
                        if (combat.isNotEmpty()) {
                            lines.add("Combat" to slot)
                            for (text in combat) lines.add("  $text" to slot)
                        }
                    } else {
                        lines.add("Combat" to slot)
                        lines.add("  Loading..." to slot)
                    }
                }
                lore.any { it.startsWith("Gathering Skills:") || it.startsWith("Crafting Skills:") } -> {
                    val before = lines.size
                    if (lines.none { it.first == "Professions" }) lines.add("Professions" to slot)
                    for (line in lore) {
                        val t = line.trim()
                        if (t.isEmpty()) continue
                        if (t.startsWith("Gathering Skills:") || t.startsWith("Crafting Skills:")) continue
                        if (lines.size < 60) lines.add("  $t" to slot)
                    }

                    if (lines.size == before + 1) lines.removeAt(before)
                }
            }
            if (lines.size >= 60) break
        }
        return lines.take(60)
    }

    private fun stripCodes(text: String): String {
        val noCodes = text.replace(Regex("\u00A7."), "")
        val sb = StringBuilder(noCodes.length)
        var i = 0
        while (i < noCodes.length) {
            val cp = noCodes.codePointAt(i)
            if (cp < 0xE000) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun drawCarried(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, carried: ItemStack) {
        if (carried.isEmpty) return
        graphics.item(carried, mouseX - 8, mouseY - 8)
        graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(event, doubled)) return true
        val x = event.x().toInt()
        val y = event.y().toInt()
        val button = event.button()
        if (button == 2 && OverwatchItemDebug.tryCopyToClipboard(hoveredDebugStack())) return true
        if (invTab == InvTab.JOURNAL) return journalClick(x, y, button)
        if (invTab == InvTab.CHARACTER) return characterClick(x, y, button, event)
        val layout = lastLayout ?: return true

        val dockedSlot = layout.tiles.firstOrNull { it.docked && x in it.x until it.x + TILE && y in layout.scrollTop + it.y until layout.scrollTop + it.y + TILE }?.menuSlot
        if (dockedSlot != null) {
            val player = Minecraft.getInstance().player ?: return true
            if (player.containerMenu === menu) {
                return handleInventoryClick(dockedSlot, x, y, button, event, layout, player)
            }
        }

        if (!inWindow(layout, x, y)) {
            if (!menu.carried.isEmpty && (button == 0 || button == 1)) {
                sendInput(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
            }
            return true
        }
        val player = Minecraft.getInstance().player ?: return true
        if (player.containerMenu !== menu) return true

        val slot = findTile(layout, x, y)
        if (slot < 0) {
            if (!menu.carried.isEmpty && (button == 0 || button == 1)) autoPlace(button)
            return true
        }
        return handleInventoryClick(slot, x, y, button, event, layout, player)
    }

    private fun hoveredDebugStack(): ItemStack = when (invTab) {
        InvTab.INVENTORY -> if (hoveredSlot >= 0) slotStack(hoveredSlot) else ItemStack.EMPTY
        InvTab.CHARACTER -> if (hoveredChar >= 0) charSlotStack(hoveredChar) else ItemStack.EMPTY
        InvTab.JOURNAL -> hoveredJournal?.activity?.icon ?: ItemStack.EMPTY
        else -> ItemStack.EMPTY
    }

    private fun handleInventoryClick(
        slot: Int,
        x: Int,
        y: Int,
        button: Int,
        event: MouseButtonEvent,
        layout: Layout,
        player: net.minecraft.world.entity.player.Player
    ): Boolean {
        val stack = slotStack(slot)
        val isIngredientPouch = WynnPouches.isIngredientPouch(stack)
        val isSellConfirm = WynnPouches.isSellConfirm(stack) || WynnPouches.isConfirmMorph(stack)

        when (button) {
            0 -> {
                if (event.hasShiftDown()) {
                    sendInput(slot, 0, ContainerInput.QUICK_MOVE)
                    shiftDragging = OverwatchConfig.current.shiftDragQuickMove
                    shiftVisited.clear()
                    shiftVisited.add(slot)
                    shiftDragX = x
                    shiftDragY = y
                } else if (isIngredientPouch) {
                    sendInput(slot, 0, ContainerInput.PICKUP)
                } else {
                    val now = System.currentTimeMillis()
                    val doubled = slot == lastClickSlot && now - lastClickTime < DOUBLE_CLICK_MS && !menu.carried.isEmpty
                    when {
                        doubled -> {
                            sendInput(slot, 0, ContainerInput.PICKUP_ALL)
                            beginPaint(slot, right = false)
                        }

                        !menu.carried.isEmpty && !isPrecisePlacement(slot) -> autoPlace(0)
                        else -> {
                            sendInput(slot, 0, ContainerInput.PICKUP)
                            beginPaint(slot, right = false)
                        }
                    }
                    lastClickSlot = slot
                    lastClickTime = now
                }
                return true
            }
            1 -> {
                if (isSellConfirm) {
                    sendInput(slot, 0, ContainerInput.PICKUP)
                } else if (isIngredientPouch && event.hasShiftDown()) {
                    sendInput(slot, 1, ContainerInput.QUICK_MOVE)
                } else if (isIngredientPouch) {
                    sendInput(slot, 0, ContainerInput.PICKUP)
                } else if (event.hasShiftDown()) {
                    sendInput(slot, 1, ContainerInput.QUICK_MOVE)
                } else {
                    if (!menu.carried.isEmpty && !isPrecisePlacement(slot)) {
                        autoPlace(1)
                    } else {
                        sendInput(slot, 1, ContainerInput.PICKUP)
                        beginPaint(slot, right = true)
                    }
                }
                return true
            }
            2 -> {
                if (player.abilities.instabuild) sendInput(slot, 2, ContainerInput.CLONE)
                return true
            }
        }
        return false
    }

    private fun inWindow(layout: Layout, x: Int, y: Int): Boolean {
        val left = panelLeft()
        val top = panelTopFor(layout.panelH)
        return x in left until left + panelWidth() && y in top until top + layout.panelH
    }

    private fun isPrecisePlacement(slot: Int): Boolean {
        if (slot in HOTBAR_SLOTS) return true
        if (slot in ARMOR_SLOTS || slot == OFFHAND_SLOT) return true
        if (slot in ACCESSORY_EQUIP_SLOTS) return true
        return slot >= VANILLA_MENU_SIZE
    }

    private fun autoPlace(button: Int) {
        val stack = menu.carried
        if (stack.isEmpty) return
        val target = firstViableSlot(stack)
        if (target < 0) {
            blockReason("No room for the held item")
            return
        }
        sendInput(target, button, ContainerInput.PICKUP)
    }

    private fun firstViableSlot(stack: ItemStack): Int {
        if (stack.isEmpty) return -1
        var merge = -1
        var empty = -1

        for (slot in MAIN_SLOTS) {
            val s = menu.slots.getOrNull(slot)?.item ?: continue
            if (!s.isEmpty && !isExcluded(s) && s.isStackable && ItemStack.isSameItemSameComponents(s, stack) && s.count < s.maxStackSize) {
                if (merge < 0 || s.count < menu.slots[merge].item.count) merge = slot
            } else if (empty < 0 && s.isEmpty) {
                empty = slot
            }
        }
        if (merge >= 0) return merge
        for (slot in HOTBAR_SLOTS) {
            val s = menu.slots.getOrNull(slot)?.item ?: continue
            if (!s.isEmpty && !isExcluded(s) && s.isStackable && ItemStack.isSameItemSameComponents(s, stack) && s.count < s.maxStackSize) {
                if (merge < 0 || s.count < menu.slots[merge].item.count) merge = slot
            } else if (empty < 0 && s.isEmpty) {
                empty = slot
            }
        }
        if (merge >= 0) return merge
        return empty
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (super.mouseDragged(event, deltaX, deltaY)) return true
        if (shiftDragging) {
            shiftQuickMove(event.x().toInt(), event.y().toInt())
            return true
        }
        if (!painting) return false
        val layout = lastLayout ?: return false
        val slot = findTile(layout, event.x().toInt(), event.y().toInt())
        if (slot < 0 || !painted.add(slot)) return true
        val base = if (paintRight) 4 else 0
        if (painted.size == 2) sendInput(paintOrigin, base, ContainerInput.QUICK_CRAFT)
        sendInput(slot, base + 1, ContainerInput.QUICK_CRAFT)
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (super.mouseReleased(event)) return true
        if (shiftDragging) {
            shiftDragging = false
            shiftVisited.clear()
            return true
        }
        if (!painting) return false
        painting = false
        val hadDrag = painted.size > 1
        painted.clear()
        if (hadDrag) {
            sendInput(paintOrigin, (if (paintRight) 4 else 0) + 2, ContainerInput.QUICK_CRAFT)
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (invTab == InvTab.SETTINGS) {
            if (panelList.handleMouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        if (invTab != InvTab.INVENTORY && invTab != InvTab.JOURNAL && invTab != InvTab.CHARACTER) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        val layout = lastLayout
        if (layout == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        if (mouseX < layout.gridX || mouseX >= layout.gridX + layout.gridW ||
            mouseY < layout.scrollTop || mouseY >= layout.scrollBottom
        ) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        if (invTab == InvTab.JOURNAL) {
            val rowCount = (journal.results().size + ContentBookViewModel.LIST_COLS - 1) / ContentBookViewModel.LIST_COLS
            val maxScroll = (maxOf(0, rowCount - JOURNAL_LIST_ROWS) * ContentBookViewModel.ROW_H)
            this.journalScrollY = (this.journalScrollY - scrollY * SCROLL_STEP).toInt().coerceIn(0, maxScroll)
            return true
        }
        if (invTab == InvTab.CHARACTER) {
            val maxScroll = (layout.contentH - (layout.scrollBottom - layout.scrollTop)).coerceAtLeast(0)
            this.charScrollY = (this.charScrollY - scrollY * SCROLL_STEP).toInt().coerceIn(0, maxScroll)
            return true
        }

        val maxScroll = (layout.contentH - (layout.scrollBottom - layout.scrollTop)).coerceAtLeast(0)
        this.scrollY = (this.scrollY - scrollY * SCROLL_STEP).toInt().coerceIn(0, maxScroll)
        return true
    }

    private fun addClaimButtons(ox: Int, pt: Int) {
        val labels = ArrayList<Pair<String, () -> Unit>>()
        if (ObjectiveClaims.weeklyClaimable) labels.add("Claim weekly" to { ObjectiveClaims.claimWeekly(); rebuildWidgets() })
        if (ObjectiveClaims.dailyClaimable) labels.add("Claim daily" to { selectTab(InvTab.CHARACTER) })
        if (labels.isEmpty()) return
        val by = (pt - CLAIM_BTN_H - 2).coerceAtLeast(2)
        var bx = ox + panelWidth() - MARGIN
        for ((label, action) in labels.asReversed()) {
            val w = font.width(label) + 14
            bx -= w
            addRenderableWidget(OwButton(bx, by, w, CLAIM_BTN_H, Component.literal(label), accent = true) { action() })
            bx -= 4
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val client = Minecraft.getInstance()
        if (isTextInputFocused()) {
            if (event.key() == KEY_ESCAPE) {
                releaseTextInputFocus()
                return true
            }
            return super.keyPressed(event)
        }
        if (event.key() == KEY_ESCAPE || client.options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        val activeHover = if (invTab == InvTab.CHARACTER) hoveredChar else hoveredSlot
        if (invTab != InvTab.SETTINGS && invTab != InvTab.JOURNAL && event.key() in KEY_1..KEY_9 && activeHover >= 0) {
            if (invTab == InvTab.CHARACTER) sendCharInput(activeHover, event.key() - KEY_1, ContainerInput.SWAP)
            else sendInput(activeHover, event.key() - KEY_1, ContainerInput.SWAP)
            return true
        }
        if (invTab != InvTab.SETTINGS && invTab != InvTab.JOURNAL && client.options.keySwapOffhand.matches(event) && activeHover >= 0) {
            if (invTab == InvTab.CHARACTER) sendCharInput(activeHover, OFFHAND_BUTTON, ContainerInput.SWAP)
            else sendInput(activeHover, OFFHAND_BUTTON, ContainerInput.SWAP)
            return true
        }
        if (invTab != InvTab.SETTINGS && invTab != InvTab.JOURNAL && client.options.keyDrop.matches(event) && activeHover >= 0) {
            val dropButton = if ((event.modifiers() and MOD_CONTROL) != 0) 1 else 0
            if (invTab == InvTab.CHARACTER) sendCharInput(activeHover, dropButton, ContainerInput.THROW)
            else sendInput(activeHover, dropButton, ContainerInput.THROW)
            return true
        }
        return super.keyPressed(event)
    }

    private fun shiftHeld(): Boolean {
        val window = Minecraft.getInstance().window
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) ||
            com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)
    }

    private fun shiftQuickMove(mx: Int, my: Int) {
        if (!shiftHeld()) {
            shiftDragging = false
            shiftVisited.clear()
            return
        }
        val layout = lastLayout ?: return
        val player = Minecraft.getInstance().player ?: return
        if (player.containerMenu !== menu) return
        val dx = mx - shiftDragX
        val dy = my - shiftDragY
        val steps = maxOf(1, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) / SHIFT_DRAG_STEP)
        for (i in 1..steps) {
            val px = shiftDragX + dx * i / steps
            val py = shiftDragY + dy * i / steps
            val slot = findTile(layout, px, py)
            if (slot < 0 || !shiftVisited.add(slot)) continue
            val stack = slotStack(slot)
            if (stack.isEmpty || WynnPouches.isIngredientPouch(stack) || WynnPouches.isSellConfirm(stack) || WynnPouches.isConfirmMorph(stack)) continue
            sendInput(slot, 0, ContainerInput.QUICK_MOVE)
        }
        shiftDragX = mx
        shiftDragY = my
    }

    private fun beginPaint(slot: Int, right: Boolean) {
        painting = true
        paintRight = right
        paintOrigin = slot
        painted.clear()
        painted.add(slot)
    }

    private fun sendInput(slot: Int, button: Int, kind: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== menu) {
            Overwatch.LOGGER.warn("Overwatch inventory: menu no longer current, dropping {} input", kind)
            return
        }
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            Overwatch.LOGGER.error("Overwatch inventory input failed", t)
        }
    }

    override fun onClose() {
        OverwatchInventory.pendingTransitionTab = null
        leaveContainers()
        OverwatchConfig.current.save()
        Minecraft.getInstance().gui.setScreen(null)
    }

    override fun isPauseScreen(): Boolean = false

    override val screen: Screen get() = this
    override val panelFont: Font get() = font
    override fun rebuildPanels() = rebuildWidgets()
    override fun installPanelRows(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, w: Int, h: Int) {
        panelList.install(rows, x, y, w, h)
    }
    override fun addPanelWidget(widget: AbstractWidget) {
        addRenderableWidget(widget)
    }
    override fun panelContentBottom(): Int = if (invTab == InvTab.SETTINGS) settingsPanelBottom else height - MARGIN

    companion object {
        private val dockedStackCache = HashMap<Int, ItemStack>()
        fun clearDockedCache() = dockedStackCache.clear()
        const val MARGIN = 10
        const val GAP = 8
        const val TAB_W = 64
        const val TAB_H = 16
        const val CLAIM_BTN_H = 14
        const val OFFHAND_BUTTON = 40
        const val CHAR_LOAD_TIMEOUT_TICKS = 120
        const val STAT_GRACE_NANOS = 2_500_000_000L
        const val SEARCH_H = 16

        const val TAB_Y_REL = 7
        const val SEARCH_Y_REL = TAB_Y_REL + TAB_H + 6
        const val CONTENT_TOP_REL = SEARCH_Y_REL + SEARCH_H + GAP
        const val FOOTER_H = 14
        const val CAPTION_H = 12
        const val PREVIEW_W = 88
        const val PREVIEW_H = 140
        const val PREVIEW_ENTITY_SIZE = 52

        const val ROW_H = 20
        const val JOURNAL_ROW1_H = 16
        const val JOURNAL_SECTION_GAP = 6
        const val JOURNAL_TAB_H = 16
        const val JOURNAL_DETAIL_LINE_H = 10
        const val JOURNAL_MAX_DETAIL_LINES = 16
        const val JOURNAL_ACTION_BTN_H = 18
        const val JOURNAL_LIST_ROWS = 11

        const val STATS_ROW_H = 11

        const val SKILL_ROW_H = 22
        const val BAR_H = 3
        const val OPENER_BTN_H = 20
        const val STAT_TAB_H = 16
        const val CHAR_SECTION_GAP = 6
        const val FLOW_PAD = 12
        const val CHAR_MENU_ROW_Y = 0
        const val OPENER_ROWS = 2
        const val OPENER_ROW_GAP = 2
        const val CHAR_STRIP_TEXT_Y = CHAR_MENU_ROW_Y + OPENER_ROWS * OPENER_BTN_H + (OPENER_ROWS - 1) * OPENER_ROW_GAP + 8
        const val CHAR_STRIP_TEXT_H = 40
        const val CHAR_STRIP_H = CHAR_STRIP_TEXT_Y + CHAR_STRIP_TEXT_H
        const val CHARACTER_GRID_Y = CHAR_STRIP_H + 4
        const val MENU_BTN_GAP = 2

        const val CHAR_CARD_GAP = 8

        const val INGREDIENT_POUCH_SLOT = 13
        const val SHIFT_DRAG_STEP = 4
        const val POUCH_LINES = 12

        const val PLAYER_INV_SIZE = 36

        val PLAYER_STAT_KEYS = listOf("Total Lv:", "Combat Lv:", "Class:", "Quests:", "XP:")
        const val TILE = 22
        const val TILE_STEP = 24
        const val HEADER_H = 16
        const val SECTION_GAP = 6

        const val GLASS_BG = 0xA812100D.toInt()
        const val SCROLL_STEP = 24
        const val DOUBLE_CLICK_MS = 250L
        const val OUTSIDE_SLOT = -999
        const val OFFHAND_SLOT = 45

        const val VANILLA_MENU_SIZE = 46

        val ARMOR_SLOTS = listOf(5, 6, 7, 8)

        val ACCESSORY_EQUIP_SLOTS = listOf(9, 10, 11, 12)

        val MAIN_SLOTS = (9..35).toList()
        val HOTBAR_SLOTS = (36..44).toList()
        const val TILE_BG = 0xF014100B.toInt()

        const val TILE_BG_EMPTY = 0x8014100B.toInt()
        const val TILE_TINT_ALPHA = 0x50000000.toInt()
        const val HOVER_BORDER = 0xFFFFFFFF.toInt()
        const val KEY_ESCAPE = 256
        const val KEY_1 = 49
        const val KEY_9 = 57

        const val MOD_CONTROL = 2
    }
}
