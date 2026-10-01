package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.client.CharacterMenuModel

class WynnOverhaulInventoryScreen(
    private val menu: InventoryMenu,
    initialTab: InvTab = InvTab.INVENTORY,
) : Screen(Component.literal("WynnOverhaul - Inventory")),
    WynnOverhaulSettingsPanels.Host {
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
        val pouchSlot: Int = -1,
        val pouchX: Int = 0,
        val pouchY: Int = 0,
        val emeraldPouchSlot: Int = -1,
        val emeraldPouchX: Int = 0,
        val emeraldPouchY: Int = 0,
    )

    private var searchField: OwTextField? = null
    private var inventorySortButton: AbstractWidget? = null
    private var searchText: String = ""
    private val tabButtons = ArrayList<OwButton>()

    private data class CommandShortcut(val id: String, val label: String, val command: String)

    private val commandShortcuts = listOf(
        CommandShortcut("pet", "Pets", "pet"),
        CommandShortcut("class", "Classes", "class"),
        CommandShortcut("totem", "Totems", "totem"),
        CommandShortcut("shop", "Shop", "use"),
        CommandShortcut("crates", "Crates", "crates"),
        CommandShortcut("guild", "Guild", "guild manage"),
    )

    private var lastShortcutId = ""
    private var scrollY: Int = 0
    private var invTab: InvTab = initialTab
    private val panels = WynnOverhaulSettingsPanels(this)
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
    private var journalSortButton: AbstractWidget? = null
    private val journalCategoryButtons = ArrayList<Pair<OwButton, Int>>()
    private var journalTrackButton: OwButton? = null
    private var journalWikiButton: OwButton? = null
    private var journalActionsTop: Int = 0
    private var journalRefreshButton: OwButton? = null
    private var selectedJournal: ActivityInfo? = null
    private var hoveredChar: Int = -1
    private var hoveredJournal: JournalSlot? = null

    private val fade = OwFade()
    private var lastCharReady = false
    private var lastJournalReady = false

    private val charWidgets = ArrayList<OwButton>()
    private var charSnapshot: CharacterMenuModel.Snapshot? = null
    private val combatInfoPager = CombatInfoPager()
    private enum class CharStatsTab(val label: String) {
        COMBAT("Combat"),
        IDENTIFICATIONS("Identifications"),
        PROFESSIONS("Professions"),
    }
    private var charStatsTab = CharStatsTab.COMBAT
    private data class CharZone(val x: Int, val y: Int, val w: Int, val h: Int, val slot: Int)
    private data class CharPlaced(val row: CharacterStatRows.Row, val x: Int, val y: Int, val w: Int, val stripe: Boolean)
    private val charHoverZones = ArrayList<CharZone>()
    private val charPlaced = ArrayList<CharPlaced>()
    private var charCardTop = 0
    private var charCardH = 0
    private val charWidgetRows = ArrayList<Pair<OwButton, Int>>()
    private val charPinnedWidgetRows = ArrayList<Pair<OwButton, Int>>()
    private val charButtonSlots = ArrayList<Pair<OwButton, Int>>()
    private var charTickCounter = 0
    private var charLoadTicks = 0
    private var charWasLoading = false
    private var lastStatClickNanos = 0L

    private var pendingTabFire: Pair<InvTab, () -> Boolean>? = null
    private var pendingTabFireTimeout: Int = 0

    private var pouchMenu: AbstractContainerMenu? = null
    private var pouchKind: PouchInterceptor.PouchKind? = null
    private var pouchPendingKind: PouchInterceptor.PouchKind? = null
    private var pouchPendingAtNanos = 0L
    private val pouchFade = OwFade()
    private val pouchCloseFade = OwFade()
    private var pouchClosingKind: PouchInterceptor.PouchKind? = null
    private val pouchTiles = ArrayList<PlacedTile>()
    private var pouchHoverSlot = -1
    private var pouchPX = 0
    private var pouchPY = 0
    private var pouchPW = 0
    private var pouchPH = 0

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
        OwDropdownOverlay.close()
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
        addShortcutDropdown(ox, pt)
        if (invTab == InvTab.INVENTORY) {
            val fieldX = ox + MARGIN
            val fieldY = pt + SEARCH_Y_REL
            val sortW = font.width("Sort: Default") + 34
            searchField = OwTextField(font, fieldX, fieldY, panelWidth() - MARGIN * 2 - sortW - 4, SEARCH_H).also {
                it.value = searchText
                addRenderableWidget(it)
            }
            inventorySortButton = OwDropdown(
                fieldX + panelWidth() - MARGIN * 2 - sortW, fieldY, sortW, SEARCH_H,
                "Sort",
                InventorySort.entries.map { OwDropdownOverlay.Option(it.name, it.label) },
                { InventorySort.parse(WynnOverhaulConfig.current.inventorySort).name },
            ) {
                val config = WynnOverhaulConfig.current
                config.inventorySort = it
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
            val sortW = 100
            val refreshW = 56
            journalSearchField = OwTextField(font, gx, rowY(-JOURNAL_TOP_SHIFT), gw - sortW - refreshW - 8, SEARCH_H).also {
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
            journalSortButton = OwDropdown(
                gx + gw - sortW - refreshW - 4, rowY(-JOURNAL_TOP_SHIFT), sortW, SEARCH_H,
                "",
                ContentBookViewModel.Sort.entries.map { OwDropdownOverlay.Option(it.name, it.label) },
                { journal.sort.name },
            ) {
                journal.selectSort(ContentBookViewModel.Sort.valueOf(it))
                rebuildWidgets()
            }.also { addRenderableWidget(it) }
            journalRefreshButton = OwButton(gx + gw - refreshW, rowY(-JOURNAL_TOP_SHIFT), refreshW, SEARCH_H, Component.literal("Refresh")) {
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
                    gx + JOURNAL_CARD_PAD, rowY(header.actionsTop), 120, JOURNAL_ACTION_BTN_H, Component.literal(trackText),
                    enabled = { selected.trackingState != ActivityTrackingState.UNTRACKABLE || mapLocated != null },
                ) {
                    toggleJournalTrack(selected)
                }.also { addRenderableWidget(it) }
                journalWikiButton = OwButton(gx + JOURNAL_CARD_PAD + 124, rowY(header.actionsTop), 100, JOURNAL_ACTION_BTN_H, Component.literal("Wiki Info")) {
                    Minecraft.getInstance().setScreenAndShow(WynnOverhaulQuestWikiScreen(selected.type, selected.name, this))
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
                val rightX = gx + leftW + CHAR_CARD_GAP + CARD_PAD
                val rightInnerW = rightW - CARD_PAD * 2

                val statsTabY = CHARACTER_GRID_Y
                val statsTabBy = charScreenY(statsTabY, pt)
                val tabs = CharStatsTab.entries
                val tabInnerW = leftW - CARD_PAD * 2
                val tabNatural = tabs.map { font.width(it.label) + 14 }
                val tabExtra = ((tabInnerW - MENU_BTN_GAP * (tabs.size - 1) - tabNatural.sum()) / tabs.size).coerceAtLeast(0)
                var tabX = gx + CARD_PAD
                for ((i, tab) in tabs.withIndex()) {
                    val tabW = if (i == tabs.size - 1) gx + CARD_PAD + tabInnerW - tabX else tabNatural[i] + tabExtra
                    charWidgets.add(
                        OwButton(tabX, statsTabBy, tabW, STAT_TAB_H, Component.literal(tab.label), accent = charStatsTab == tab) {
                            charStatsTab = tab
                            charScrollY = 0
                            rebuildWidgets()
                        }.also { addRenderableWidget(it); charWidgetRows.add(it to statsTabY) },
                    )
                    tabX += tabW + MENU_BTN_GAP
                }

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
                    charWidgets.add(OwButton(rightX + rightInnerW - 44, by, 20, 16, Component.literal("-")) {
                        sendCharInput(skill.slot, 1, if (shiftHeld()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to cellY) })
                    charWidgets.add(OwButton(rightX + rightInnerW - 22, by, 20, 16, Component.literal("+")) {
                        sendCharInput(skill.slot, 0, if (shiftHeld()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
                    }.also { addRenderableWidget(it); charWidgetRows.add(it to cellY) })
                }
                val wy = CHARACTER_GRID_Y + HEADER_H + maxOf(5, snap.skills.size) * SKILL_ROW_H + CHAR_SECTION_GAP
                if (snap.skillCrystalSlot >= 0) {
                    val crystal = snap.skillCrystalSlot
                    val by = charScreenY(wy, pt)
                    charWidgets.add(
                        OwButton(rightX, by, rightInnerW, OPENER_BTN_H, Component.literal("Reset Skills"), icon = charSlotStack(crystal)) {
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
        lastShown = this
        lastShownNanos = System.nanoTime()
        val covered = OwDropdownOverlay.covers(mouseX, mouseY, height)
        val mx = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseX
        val my = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseY
        renderInventoryScreen(graphics, mx, my, partialTick)
        drawContentFade(graphics)
        OwDropdownOverlay.render(graphics, mouseX, mouseY, height)
    }

    private fun drawContentFade(graphics: GuiGraphicsExtractor) {
        val charReady = characterMenu != null
        if (charReady != lastCharReady) {
            lastCharReady = charReady
            if (charReady && invTab == InvTab.CHARACTER) fade.restart()
        }
        val journalReady = journalMenu != null || journal.activities.isNotEmpty()
        if (journalReady != lastJournalReady) {
            lastJournalReady = journalReady
            if (journalReady && invTab == InvTab.JOURNAL) fade.restart()
        }
        if (invTab == InvTab.SETTINGS) return
        val layout = lastLayout ?: return
        val top = layout.scrollTop
        val bottom = panelTopFor(layout.panelH) + layout.panelH
        fade.overlay(graphics, panelLeft(), top, panelWidth(), bottom - top)
    }

    private fun renderInventoryScreen(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
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
                journalSearchField?.let { field -> field.y = pt + CONTENT_TOP_REL - JOURNAL_TOP_SHIFT }
                journalSortButton?.let { button -> button.y = pt + CONTENT_TOP_REL - JOURNAL_TOP_SHIFT }
                journalRefreshButton?.let { button -> button.y = pt + CONTENT_TOP_REL - JOURNAL_TOP_SHIFT }
                for ((button, relY) in journalCategoryButtons) button.y = pt + CONTENT_TOP_REL + relY
                journalTrackButton?.let { button -> button.y = pt + CONTENT_TOP_REL + journalActionsTop }
                journalWikiButton?.let { button -> button.y = pt + CONTENT_TOP_REL + journalActionsTop }
                drawOuterPanel(graphics, pt, it.panelH)
                when (invTab) {
                    InvTab.INVENTORY -> {
                        drawScrollContent(graphics, player, it)
                        drawFooter(graphics, pt, it.panelH)
                        drawPouchPanel(graphics, pt)
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
        if (invTab == InvTab.INVENTORY && pouchHoverSlot >= 0) {
            val stack = pouchStack(pouchHoverSlot)
            if (!stack.isEmpty && activeCarried().isEmpty) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
        if (invTab == InvTab.CHARACTER) {
            drawCharHoverAndTooltip(graphics, layout)
            drawCharButtonTooltip(graphics)
        }
        if (invTab == InvTab.JOURNAL) drawJournalHoverAndTooltip(graphics)

        if (invTab == InvTab.INVENTORY) drawCarried(graphics, mouseX, mouseY, activeCarried())
    }

    private fun computeSettingsLayout(): Layout {
        val contentH = 380
        val panelH = panelHFor(contentH)
        val panelTop = panelTopFor(panelH)
        settingsPanelBottom = panelTop + panelH
        val ox = panelLeft() + MARGIN
        val w = panelWidth() - MARGIN * 2
        val scrollTop = panelTop + CONTENT_TOP_REL
        val scrollBottom = (panelTop + panelH - 4 - FOOTER_H - GAP).coerceAtLeast(scrollTop + 40)
        return Layout(emptyList(), emptyList(), emptyList(), contentH, panelH, ox, w, scrollTop, scrollBottom, emptyList())
    }

    private class InventoryLayoutData(
        val signature: Int,
        val builtAtNanos: Long,
        val filtered: List<Int>,
        val pouchSlot: Int,
        val pouchEntries: List<Pair<Int, String>>,
    )

    private var inventoryCache: InventoryLayoutData? = null

    private fun inventoryLayoutData(query: String): InventoryLayoutData {
        val sortName = WynnOverhaulConfig.current.inventorySort
        var signature = query.hashCode()
        signature = 31 * signature + sortName.hashCode()
        signature = 31 * signature + System.identityHashCode(menu)
        for (slot in MAIN_SLOTS) signature = 31 * signature + stackSignature(slotStack(slot))
        for (slot in HOTBAR_SLOTS) signature = 31 * signature + stackSignature(slotStack(slot))
        val now = System.nanoTime()
        val cached = inventoryCache
        if (cached != null && cached.signature == signature && now - cached.builtAtNanos < INVENTORY_CACHE_NANOS) return cached

        val pouchSlot = findIngredientPouch()
        val storage = if (onWynncraft()) MAIN_SLOTS.drop(4) else MAIN_SLOTS
        val dockReserved = emeraldDockReserved()
        val filtered = InventorySort.parse(sortName)
            .apply(
                storage.filter {
                    val stack = slotStack(it)
                    slotVisible(it, query) && !(it == EMERALD_POUCH_SLOT && dockReserved) &&
                        (!isExcluded(stack) || WynnPouches.isEmeraldPouch(stack))
                },
                ::slotStack,
            )
        val entries = if (pouchSlot >= 0) WynnPouches.ingredientEntries(slotStack(pouchSlot)) else emptyList()
        return InventoryLayoutData(signature, now, filtered, pouchSlot, entries).also { inventoryCache = it }
    }

    private fun stackSignature(stack: ItemStack): Int =
        if (stack.isEmpty) 0 else 31 * System.identityHashCode(stack) + stack.count

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
        var emeraldSlotOut = -1
        var emeraldXOut = 0
        var emeraldYOut = 0

        charHoverZones.clear()

        if (invTab == InvTab.INVENTORY) {
            labels.add(PlacedLabel("Hotbar", x, y, color = OwTheme.ACCENT))
            y += CAPTION_H
            var hx = x
            for (slot in HOTBAR_SLOTS) {
                val stack = slotStack(slot)
                val show = (!isExcluded(stack) || WynnPouches.isEmeraldPouch(stack)) &&
                    (query.isEmpty() || stack.isEmpty || matchesQuery(stack, query))
                tiles.add(PlacedTile(if (show) slot else -1, hx, y))
                hx += TILE_STEP
            }
            y += TILE_STEP + SECTION_GAP

            val inv = inventoryLayoutData(query)
            val pouchSlot = inv.pouchSlot
            val filtered = inv.filtered
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
                val entries = inv.pouchEntries
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
            if (emeraldDockReserved()) {
                emeraldSlotOut = EMERALD_POUCH_SLOT
                if (pouchSlot >= 0) {
                    emeraldXOut = pouchX + TILE_STEP
                    emeraldYOut = pouchY
                } else {
                    val px = x + 3 * TILE_STEP + GAP
                    var py = gridTop - CAPTION_H
                    labels.add(PlacedLabel("EMERALD POUCH", px, py, color = OwTheme.ACCENT))
                    py += CAPTION_H
                    emeraldXOut = px
                    emeraldYOut = py
                    val dockStack = slotStack(EMERALD_POUCH_SLOT)
                    val hint = if (WynnPouches.isEmeraldPouch(dockStack)) "Right-click: view contents" else "Reserved for an Emerald Pouch"
                    labels.add(PlacedLabel(hint, px + 2, py + TILE_STEP + 1, color = OwTheme.TEXT_DIM))
                }
            }
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

        if (invTab == InvTab.CHARACTER) {
            charHoverZones.clear()
            charPlaced.clear()
            y = CHARACTER_GRID_Y
            val charMenu = characterMenu
            val snap = charSnapshot ?: characterMenu?.let { CharacterMenuModel.snapshot(it) }
            if (charMenu != null) {
                val leftW = (w * 0.44).toInt()
                val rightW = w - leftW - CHAR_CARD_GAP
                val leftX = x + CARD_PAD
                val leftInnerW = leftW - CARD_PAD * 2
                val rightX = x + leftW + CHAR_CARD_GAP + CARD_PAD
                val rightInnerW = rightW - CARD_PAD * 2
                val sections = charSections(charMenu)

                val leftBottom = layoutCharRows(charRowsFor(sections), leftX, y + STAT_TAB_H + CHAR_TAB_GAP, leftInnerW)
                val leftHeight = leftBottom - y

                val skillRows = maxOf(5, snap?.skills?.size ?: 0)
                val resetTop = y + HEADER_H + skillRows * SKILL_ROW_H + CHAR_SECTION_GAP
                val rightHeight: Int
                labels.add(PlacedLabel("SKILLS", rightX, y + 2, color = OwTheme.ACCENT_DIM))
                if (snap != null) {
                    for ((i, skill) in snap.skills.withIndex()) {
                        val sy = y + HEADER_H + i * SKILL_ROW_H
                        charHoverZones.add(CharZone(rightX, sy, rightInnerW, SKILL_ROW_H, skill.slot))
                    }
                    rightHeight = (resetTop - y) + (if (snap.skillCrystalSlot >= 0) SKILL_ROW_H else 0)
                } else {
                    rightHeight = HEADER_H + skillRows * SKILL_ROW_H + CHAR_SECTION_GAP
                }

                val inner = maxOf(leftHeight, rightHeight)
                charCardTop = y - CARD_PAD
                charCardH = inner + CARD_PAD * 2
                y += inner + CARD_PAD + SECTION_GAP
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
        return Layout(tiles, labels, rows, contentH, panelH, x, w, scrollTop, scrollBottom, journalSlotsOut, pouchSlotOut, pouchXOut, pouchYOut, emeraldSlotOut, emeraldXOut, emeraldYOut)
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

    private fun findPouch(preferredSlot: Int, isMatch: (ItemStack) -> Boolean): Int {
        var fallback = -1
        for (slot in MAIN_SLOTS + HOTBAR_SLOTS) {
            val stack = menu.slots.getOrNull(slot)?.item ?: continue
            if (!isMatch(stack)) continue
            if (slot == preferredSlot) return slot
            if (fallback < 0) fallback = slot
        }
        return fallback
    }

    private fun findIngredientPouch(): Int = findPouch(INGREDIENT_POUCH_SLOT, WynnPouches::isIngredientPouch)

    private fun onWynncraft(): Boolean = WorldContext.isWynncraft(Minecraft.getInstance())

    private fun emeraldDockReserved(): Boolean {
        if (!onWynncraft()) return false
        val stack = slotStack(EMERALD_POUCH_SLOT)
        return stack.isEmpty || WynnPouches.isEmeraldPouch(stack)
    }

    private fun rejectsReservedPlacement(slot: Int, button: Int, kind: ContainerInput, carried: ItemStack): Boolean {
        if (slot != EMERALD_POUCH_SLOT || !onWynncraft()) return false
        val incoming = when (kind) {
            ContainerInput.PICKUP, ContainerInput.QUICK_CRAFT -> carried
            ContainerInput.SWAP -> when (button) {
                in 0 until HOTBAR_SLOTS.size -> menu.slots.getOrNull(HOTBAR_SLOTS.first() + button)?.item
                OFFHAND_BUTTON -> menu.slots.getOrNull(OFFHAND_SLOT)?.item
                else -> null
            } ?: ItemStack.EMPTY
            else -> ItemStack.EMPTY
        }
        return !incoming.isEmpty && !WynnPouches.isEmeraldPouch(incoming)
    }

    private fun pouchKindOf(stack: ItemStack): PouchInterceptor.PouchKind? = when {
        stack.isEmpty -> null
        WynnPouches.isEmeraldPouch(stack) -> PouchInterceptor.PouchKind.EMERALD
        WynnPouches.isSellConfirm(stack) || WynnPouches.isConfirmMorph(stack) -> null
        WynnPouches.isIngredientPouch(stack) -> PouchInterceptor.PouchKind.INGREDIENT
        else -> null
    }

    private fun tryOpenPouch(slot: Int, button: Int, shift: Boolean): Boolean {
        if (shift || (button != 0 && button != 1)) return false
        if (!activeCarried().isEmpty) return false
        val kind = pouchKindOf(slotStack(slot)) ?: return false
        if (kind == PouchInterceptor.PouchKind.EMERALD && button != 1) return false
        togglePouch(kind, slot)
        return true
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
        val pouch = pouchMenu
        val player = Minecraft.getInstance().player
        if (pouch != null && player != null && player.containerMenu === pouch && invTab == InvTab.INVENTORY) {
            mirrorSlot(slot)?.let { if (it in 0 until pouch.slots.size) return pouch.slots[it].item }
        }
        if (slot !in 0 until menu.slots.size) return ItemStack.EMPTY
        return menu.slots[slot].item
    }

    private fun activeCarried(): ItemStack {
        val pouch = pouchMenu
        val player = Minecraft.getInstance().player
        return if (pouch != null && player != null && player.containerMenu === pouch) pouch.carried else menu.carried
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
        if (layout.emeraldPouchSlot >= 0 && x in layout.emeraldPouchX until layout.emeraldPouchX + TILE && contentY in layout.emeraldPouchY until layout.emeraldPouchY + TILE) {
            return layout.emeraldPouchSlot
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
        if (layout.emeraldPouchSlot >= 0) {
            val ey = layout.emeraldPouchY - scrollY + layout.scrollTop
            if (ey + TILE > layout.scrollTop && ey < layout.scrollBottom) {
                drawTile(graphics, PlacedTile(layout.emeraldPouchSlot, layout.emeraldPouchX, ey), layout.emeraldPouchSlot == hoveredSlot)
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

    private fun drawPouchPanel(graphics: GuiGraphicsExtractor, pt: Int) {
        pouchTiles.clear()
        pouchHoverSlot = -1
        pouchPW = 0
        pouchPH = 0
        val closing = pouchClosingKind
        if (closing != null && pouchMenu == null && pouchPendingKind == null) {
            val remaining = 1f - pouchCloseFade.progress()
            if (remaining <= 0f) {
                pouchClosingKind = null
            } else {
                drawPouchSkeleton(graphics, pt, closing, remaining * remaining)
                return
            }
        }
        val pending = pouchPendingKind
        if (pending != null && pouchMenu == null) {
            if (System.nanoTime() - pouchPendingAtNanos > POUCH_PENDING_NANOS) {
                pouchPendingKind = null
            } else {
                drawPouchSkeleton(graphics, pt, pending)
                return
            }
        }
        val pouch = pouchMenu ?: return
        val player = Minecraft.getInstance().player
        if (player == null || player.containerMenu !== pouch) {
            if (player != null && player.containerMenu === menu) {
                pouchMenu = null
                pouchKind = null
            }
            return
        }
        val chestSlots = (pouch.slots.size - POUCH_MIRROR_SIZE).coerceAtLeast(0)
        if (chestSlots <= 0) {
            pouchMenu = null
            pouchKind = null
            return
        }
        val cols = 9
        val rows = (chestSlots + cols - 1) / cols
        val px = panelLeft() + panelWidth() + 4 + GAP
        val label = if (pouchKind == PouchInterceptor.PouchKind.EMERALD) "EMERALD POUCH" else "INGREDIENT POUCH"
        pouchPX = px - 4
        pouchPY = pt
        pouchPW = cols * TILE_STEP + 8
        pouchPH = 4 + CAPTION_H + rows * TILE_STEP + 4
        graphics.fill(pouchPX, pouchPY, pouchPX + pouchPW, pouchPY + pouchPH, GLASS_BG)
        graphics.fill(pouchPX, pouchPY, pouchPX + pouchPW, pouchPY + 1, OwTheme.HAIRLINE)
        graphics.fill(pouchPX, pouchPY + pouchPH - 1, pouchPX + pouchPW, pouchPY + pouchPH, OwTheme.HAIRLINE)
        graphics.fill(pouchPX, pouchPY, pouchPX + 1, pouchPY + pouchPH, OwTheme.HAIRLINE)
        graphics.fill(pouchPX + pouchPW - 1, pouchPY, pouchPX + pouchPW, pouchPY + pouchPH, OwTheme.HAIRLINE)
        graphics.text(font, label, px, pt + 4, OwTheme.ACCENT)
        var i = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (i >= chestSlots) break
                pouchTiles.add(PlacedTile(i, px + col * TILE_STEP, pt + 4 + CAPTION_H + row * TILE_STEP))
                i++
            }
        }
        for (tile in pouchTiles) drawTile(graphics, tile, tile.menuSlot == pouchHoverSlot) { pouchStack(it.menuSlot) }
        pouchHoverSlot = pouchTiles.firstOrNull { mouseX in it.x until it.x + TILE && mouseY in it.y until it.y + TILE }?.menuSlot ?: -1
        pouchFade.overlay(graphics, pouchPX, pouchPY, pouchPW, pouchPH, GLASS_BG)
    }

    private fun withAlpha(color: Int, strength: Float): Int {
        val a = ((color ushr 24) * strength).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0xFFFFFF)
    }

    private fun drawPouchSkeleton(graphics: GuiGraphicsExtractor, pt: Int, kind: PouchInterceptor.PouchKind, strength: Float = 1f) {
        val cols = 9
        val rows = POUCH_SKELETON_ROWS
        val px = panelLeft() + panelWidth() + 4 + GAP
        val left = px - 4
        val w = cols * TILE_STEP + 8
        val h = 4 + CAPTION_H + rows * TILE_STEP + 4
        val line = withAlpha(OwTheme.HAIRLINE, strength)
        graphics.fill(left, pt, left + w, pt + h, withAlpha(GLASS_BG, strength))
        graphics.fill(left, pt, left + w, pt + 1, line)
        graphics.fill(left, pt + h - 1, left + w, pt + h, line)
        graphics.fill(left, pt, left + 1, pt + h, line)
        graphics.fill(left + w - 1, pt, left + w, pt + h, line)
        val label = if (kind == PouchInterceptor.PouchKind.EMERALD) "EMERALD POUCH" else "INGREDIENT POUCH"
        graphics.text(font, label, px, pt + 4, withAlpha(OwTheme.ACCENT, strength))
        OwSkeleton.bars(graphics, px, pt + 4 + CAPTION_H, cols * TILE_STEP - 2, rows, TILE, TILE_STEP - TILE, strength)
        if (strength >= 1f) {
            pouchPX = left
            pouchPY = pt
        }
    }

    private fun findPouchTile(x: Int, y: Int): Int? {
        if (pouchMenu == null || invTab != InvTab.INVENTORY) return null
        if (x !in pouchPX until pouchPX + pouchPW || y !in pouchPY until pouchPY + pouchPH) return null
        return pouchTiles.firstOrNull { x in it.x until it.x + TILE && y in it.y until it.y + TILE }?.menuSlot ?: -1
    }

    private fun drawFooter(graphics: GuiGraphicsExtractor, panelTop: Int, panelH: Int) {
        val cx = panelLeft() + panelWidth() / 2
        val fy = panelTop + panelH - 4 - FOOTER_H + 3
        val warning = warnText
        if (warning != null && System.nanoTime() < warnUntilNanos) {
            graphics.text(font, warning, cx - font.width(warning) / 2, fy, OwTheme.BAD)
            return
        }
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
        if (tab != InvTab.INVENTORY && pouchMenu != null) leavePouch()
        if (tab == invTab) {
            if (tab == InvTab.JOURNAL && journalMenu == null) enterJournal()
            else if (tab == InvTab.CHARACTER && characterMenu == null) enterCharacter()
            return
        }
        if (tab == InvTab.INVENTORY) {
            WynnOverhaulInventory.pendingTransitionTab = InvTab.INVENTORY
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
        fade.restart()
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

        WynnOverhaulInventory.pendingTransitionTab = InvTab.JOURNAL
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
            ScreenHold.keepOpen { player.closeContainer() }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("ensureInventoryMenu: closeContainer threw", t)
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

        WynnOverhaulInventory.pendingTransitionTab = InvTab.CHARACTER
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
        if (client.gui.screen() === this) {
            WynnOverhaulInventory.pendingTransitionTab = null
            return
        }
        client.gui.setScreen(InventoryScreen(player))
    }

    private fun leaveContainers(): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player
        if (journalMenu == null && characterMenu == null && pouchMenu == null) {
            if (ContentBookInterceptor.pendingJournalHost === this) ContentBookInterceptor.pendingJournalHost = null
            if (CharacterInfo.pendingCharacterHost === this) CharacterInfo.pendingCharacterHost = null
            PouchInterceptor.clear(this)
            pendingTabFire = null
            return false
        }
        ContentBookQuery.cancel()
        journalBusy = false
        try {
            ScreenHold.keepOpen { player?.closeContainer() }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("leaveContainers: closeContainer threw", t)
        }

        if (player != null && player.containerMenu !== player.inventoryMenu) {
            player.containerMenu = player.inventoryMenu
        }
        journalMenu = null
        characterMenu = null
        pouchMenu = null
        pouchKind = null
        pouchTiles.clear()
        pouchHoverSlot = -1
        if (ContentBookInterceptor.pendingJournalHost === this) ContentBookInterceptor.pendingJournalHost = null
        if (CharacterInfo.pendingCharacterHost === this) CharacterInfo.pendingCharacterHost = null
        PouchInterceptor.clear(this)
        pendingTabFire = null
        return true
    }

    fun attachJournalMenu(menu: AbstractContainerMenu) {
        WynnOverhaulInventory.pendingTransitionTab = null
        journalMenu = menu
        journal.update(ContentBookCache.snapshot ?: emptyList())
        journal.actionMessage = null
        if (invTab == InvTab.JOURNAL) rebuildWidgets()
        if (ContentBookCache.needsRefresh()) refreshJournal()
    }

    fun attachCharacterMenu(menu: AbstractContainerMenu) {
        WynnOverhaulInventory.pendingTransitionTab = null
        characterMenu = menu
        combatInfoPager.reset()
        identityCache = null
        charLoadTicks = 0
        charWasLoading = true
        if (invTab == InvTab.CHARACTER) rebuildWidgets()
    }

    private fun togglePouch(kind: PouchInterceptor.PouchKind, slot: Int) {
        val open = pouchKind
        if (open != null) leavePouch()
        if (open != kind) firePouchTrigger(kind, slot)
    }

    private fun firePouchTrigger(kind: PouchInterceptor.PouchKind, clicked: Int) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (pouchMenu != null) return
        if (journalMenu != null || characterMenu != null) leaveContainers()
        if (!ensureInventoryMenu()) {
            blockReason("Close the current container first")
            return
        }
        if (!menu.carried.isEmpty) {
            blockReason("Place the held item first")
            return
        }
        val slot = clicked.takeIf { pouchKindOf(menu.slots.getOrNull(it)?.item ?: ItemStack.EMPTY) == kind } ?: findPouchSlot(kind)
        if (slot == null) {
            blockReason(if (kind == PouchInterceptor.PouchKind.INGREDIENT) "Ingredient Pouch not found in your inventory" else "Emerald Pouch not found in your inventory")
            return
        }
        val button = if (kind == PouchInterceptor.PouchKind.INGREDIENT) LEFT_CLICK else RIGHT_CLICK
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, ContainerInput.PICKUP, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul pouch trigger failed", t)
            return
        }
        PouchInterceptor.arm(this, kind)
        pouchPendingKind = kind
        pouchPendingAtNanos = System.nanoTime()
    }

    private fun findPouchSlot(kind: PouchInterceptor.PouchKind): Int? {
        val player = Minecraft.getInstance().player ?: return null
        val inventory = player.inventory
        val index = (0 until POUCH_SCAN_SIZE).firstOrNull {
            val stack = inventory.getItem(it)
            if (kind == PouchInterceptor.PouchKind.INGREDIENT) WynnPouches.isIngredientPouch(stack) else WynnPouches.isEmeraldPouch(stack)
        } ?: return null
        return if (index < HOTBAR_SLOTS.size) HOTBAR_SLOTS.first() + index else index
    }

    private fun leavePouch(): Boolean {
        PouchInterceptor.clear(this)
        pouchPendingKind = null
        if (pouchMenu == null) return false
        pouchClosingKind = pouchKind
        pouchCloseFade.restart()
        pouchMenu = null
        pouchKind = null
        pouchTiles.clear()
        pouchHoverSlot = -1
        try {
            ScreenHold.keepOpen { Minecraft.getInstance().player?.closeContainer() }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("leavePouch: closeContainer threw", t)
        }
        val player = Minecraft.getInstance().player
        if (player != null && player.containerMenu !== menu) player.containerMenu = menu
        rebuildWidgets()
        return true
    }

    fun attachPouchMenu(menu: AbstractContainerMenu, kind: PouchInterceptor.PouchKind) {
        pouchMenu = menu
        pouchKind = kind
        pouchPendingKind = null
        pouchFade.restart()
        rebuildWidgets()
    }

    private fun mirrorSlot(ourSlot: Int): Int? {
        val pouch = pouchMenu ?: return null
        val base = pouch.slots.size - POUCH_MIRROR_SIZE
        if (base <= 0) return null
        val mirror = when (ourSlot) {
            in HOTBAR_SLOTS -> base + POUCH_MIRROR_COUNT + (ourSlot - HOTBAR_SLOTS.first())
            in MAIN_SLOTS -> base + (ourSlot - MAIN_SLOTS.first())
            else -> return null
        }
        return mirror.takeIf { it in 0 until pouch.slots.size }
    }

    private fun pouchStack(slot: Int): ItemStack {
        val pouch = pouchMenu ?: return ItemStack.EMPTY
        if (slot !in 0 until pouch.slots.size) return ItemStack.EMPTY
        return pouch.slots[slot].item
    }

    private fun sendPouchInput(slot: Int, button: Int, kind: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val pouch = pouchMenu ?: return
        if (player.containerMenu !== pouch) {
            pouchMenu = null
            pouchKind = null
            rebuildWidgets()
            return
        }
        try {
            client.gameMode?.handleContainerInput(pouch.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul pouch input failed", t)
        }
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

    private var warnText: String? = null
    private var warnUntilNanos = 0L

    private fun blockReason(message: String) {
        warnText = message
        warnUntilNanos = System.nanoTime() + WARN_NANOS
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
        val resumeScan = ContentBookQuery.isEnumerating
        if (resumeScan) {
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
                if (resumeScan && isJournalLive(menu)) refreshJournal()
            },
            onFailed = {
                journalBusy = false
                if (isJournalLive(menu)) {
                    showJournalMessage("Couldn't find ${activity.name} in the book")
                    if (resumeScan) refreshJournal()
                }
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

    private fun drawTile(graphics: GuiGraphicsExtractor, tile: PlacedTile, hovered: Boolean, stackOf: (PlacedTile) -> ItemStack = { if (it.docked) dockedStack(it.menuSlot) else slotStack(it.menuSlot) }) {
        if (tile.menuSlot < 0) return
        val stack = stackOf(tile)

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
        if (stack.isEmpty) {
            if (hoveredSlot == EMERALD_POUCH_SLOT && emeraldDockReserved() && activeCarried().isEmpty) {
                graphics.setTooltipForNextFrame(font, Component.literal("Emerald Pouch slot: only an Emerald Pouch fits here"), mouseX, mouseY)
            }
            return
        }
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
        
        val header = computeJournalHeader(gw)
        drawJournalProgress(graphics, gx, gw, top - JOURNAL_TOP_SHIFT + JOURNAL_ROW1_H + 5)

        val shownLines = header.detailLines.take(JOURNAL_MAX_DETAIL_LINES)
        val cardTop = top + header.detailTop - JOURNAL_CARD_PAD
        val cardBottom = if (selectedJournal != null) {
            top + header.actionsTop + JOURNAL_ACTION_BTN_H + JOURNAL_CARD_PAD
        } else {
            top + header.detailTop + shownLines.size * JOURNAL_DETAIL_LINE_H + JOURNAL_CARD_PAD
        }
        graphics.fill(gx, cardTop, gx + gw, cardBottom, CHAR_CARD_BG)
        graphics.outline(gx, cardTop, gw, cardBottom - cardTop, OwTheme.HAIRLINE)
        for ((i, line) in shownLines.withIndex()) {
            drawJournalDetailLine(graphics, line, gx + JOURNAL_CARD_PAD, top + header.detailTop + i * JOURNAL_DETAIL_LINE_H, gw - JOURNAL_CARD_PAD * 2, i)
        }
        graphics.text(font, journal.statusLine(), gx, top + header.statusTop, OwTheme.TEXT_DIM)
        val listTop = top + header.listTop
        val listBottom = minOf(layout.scrollBottom, listTop + JOURNAL_LIST_ROWS * ContentBookViewModel.ROW_H)
        if (journalMenu == null && journal.activities.isEmpty()) {
            OwSkeleton.bars(graphics, gx, listTop, gw, 6, ContentBookViewModel.ROW_H - 4, 2)
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
        val typeColor = (a.type.colorArgb and 0xFFFFFF) or 0xFF000000.toInt()
        val bg = when {
            selected -> OwTheme.TILE_HOVER
            hovered -> OwTheme.PANEL_RAISED
            else -> OwTheme.TILE_BG
        }
        graphics.fill(x, y, x + w - 2, y + rowH, bg)
        graphics.outline(x, y, w - 2, rowH, if (selected) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        graphics.fill(x + 1, y + 1, x + 3, y + rowH - 1, if (selected || hovered) typeColor else OwTheme.ACCENT_DIM)
        graphics.item(a.icon, x + 5, y + 1)
        val prefix = if (a.trackingState == ActivityTrackingState.TRACKED) "* " else ""
        val name = trimToWidth("$prefix${a.name}", w - 26 - 14)
        graphics.text(font, name, x + 25, y + (rowH - 8) / 2, typeColor)
        graphics.fill(x + w - 9, y + 3, x + w - 5, y + rowH - 3, statusColorArgb(a.status))
    }

    private fun drawJournalProgress(graphics: GuiGraphicsExtractor, gx: Int, gw: Int, y: Int) {
        val entries = (Minecraft.getInstance().player?.let { ContentBookProgress.read(it) } ?: emptyList()).ifEmpty { derivedJournalProgress() }
        if (entries.isEmpty()) {
            OwSkeleton.bars(graphics, gx, y + 2, gw, 1, 8)
            return
        }
        val gap = 8
        val cellW = (gw - gap * (entries.size - 1)) / entries.size
        for ((i, entry) in entries.withIndex()) {
            val cx = gx + i * (cellW + gap)
            val value = "${entry.done}/${entry.total}"
            val valueW = font.width(value)
            graphics.text(font, trimToWidth(entry.label, cellW - valueW - 6), cx, y, OwTheme.TEXT_DIM)
            graphics.text(font, value, cx + cellW - valueW, y, OwTheme.TEXT)
            val barY = y + 11
            graphics.fill(cx, barY, cx + cellW, barY + BAR_H, OwTheme.TILE_BORDER)
            val fillW = cellW * entry.percent.coerceIn(0, 100) / 100
            if (fillW > 0) graphics.fill(cx, barY, cx + fillW, barY + BAR_H, journalProgressColor(entry.label))
        }
    }

    private fun derivedJournalProgress(): List<ContentBookProgress.Entry> {
        val groups = listOf<Pair<String, (ActivityType) -> Boolean>>(
            "Quests" to { it.isQuest },
            "Territorial" to { it == ActivityType.TERRITORIAL_DISCOVERY },
            "World" to { it == ActivityType.WORLD_DISCOVERY },
            "Secret" to { it == ActivityType.SECRET_DISCOVERY },
        )
        return groups.mapNotNull { (label, matches) ->
            val items = journal.activities.filter { matches(it.type) }
            if (items.isEmpty()) return@mapNotNull null
            val done = items.count { it.status == ActivityStatus.COMPLETED }
            ContentBookProgress.Entry(label, done, items.size, done * 100 / items.size)
        }
    }

    private fun journalProgressColor(label: String): Int = when {
        label.startsWith("Quest") -> 0xFFB86BE0.toInt()
        label.startsWith("Territorial") -> 0xFFD8D2C4.toInt()
        label.startsWith("World") -> 0xFFE0A63A.toInt()
        label.startsWith("Secret") -> 0xFF58C7E8.toInt()
        else -> OwTheme.ACCENT
    }

    private fun drawJournalDetailLine(graphics: GuiGraphicsExtractor, line: DetailLine, x: Int, y: Int, w: Int, index: Int) {
        val text = line.text
        val labelEnd = text.indexOf(": ")
        val requirement = labelEnd > 0 && (line.color == OwTheme.GOOD || line.color == OwTheme.BAD)
        when {
            index == 0 -> graphics.text(font, trimToWidth(text, w), x, y, line.color)
            text == "Rewards" -> {
                graphics.text(font, "REWARDS", x, y, OwTheme.ACCENT_DIM)
                graphics.fill(x, y + 9, x + w, y + 10, OwTheme.HAIRLINE)
            }
            text.startsWith("- ") -> {
                graphics.text(font, "-", x + 2, y, OwTheme.TEXT_FAINT)
                graphics.text(font, trimToWidth(text.removePrefix("- "), w - 10), x + 10, y, line.color)
            }
            requirement -> {
                val label = text.substring(0, labelEnd + 1)
                graphics.text(font, label, x, y, OwTheme.TEXT_DIM)
                graphics.text(font, trimToWidth(text.substring(labelEnd + 2), w - font.width(label) - 4), x + font.width(label) + 4, y, line.color)
            }
            else -> graphics.text(font, trimToWidth(text, w), x, y, line.color)
        }
    }

    private fun drawJournalHoverAndTooltip(graphics: GuiGraphicsExtractor) {
        val slot = hoveredJournal ?: return
        if (!menu.carried.isEmpty) return
        graphics.setComponentTooltipForNextFrame(font, buildJournalTooltip(slot.activity), mouseX, mouseY)
    }

    private fun journalClick(x: Int, y: Int, button: Int): Boolean {
        val slot = findJournalSlot(lastLayout ?: return true, x, y) ?: return true
        if (button == 1) {
            Minecraft.getInstance().setScreenAndShow(WynnOverhaulQuestWikiScreen(slot.activity.type, slot.activity.name, this))
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
        val chipsTop = JOURNAL_ROW1_H + JOURNAL_PROGRESS_H + JOURNAL_SECTION_GAP - JOURNAL_TOP_SHIFT
        val chips = journalCategoryChips(0, chipsTop, width)
        val chipsBottom = (chips.maxOfOrNull { it.y } ?: chipsTop) + JOURNAL_TAB_H
        val detailTop = chipsBottom + JOURNAL_SECTION_GAP + JOURNAL_CARD_PAD
        val selected = selectedJournal
        val detailLines = if (selected != null) {
            buildJournalDetailLines(selected, width - JOURNAL_CARD_PAD * 2 - 8)
        } else {
            listOf(DetailLine("Select an activity from the list below to see its details.", OwTheme.TEXT_DIM))
        }
        val actionsTop = detailTop + minOf(detailLines.size, JOURNAL_MAX_DETAIL_LINES) * JOURNAL_DETAIL_LINE_H + 4
        val statusTop = actionsTop + JOURNAL_ACTION_BTN_H + JOURNAL_CARD_PAD + JOURNAL_SECTION_GAP
        val listTop = statusTop + 14
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
            append("  ·  Quests ${sections?.let { value(it.identity, "Quests:") } ?: "--"}")
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
            val sx = layout.gridX
            val sw = layout.gridW
            val sLeftW = (sw * 0.44).toInt()
            val sRightW = sw - sLeftW - CHAR_CARD_GAP
            val cardY = layout.scrollTop + CHARACTER_GRID_Y - CARD_PAD
            drawCharCard(graphics, sx, cardY, sLeftW, SKELETON_CARD_H)
            drawCharCard(graphics, sx + sLeftW + CHAR_CARD_GAP, cardY, sRightW, SKELETON_CARD_H)
            OwSkeleton.bars(graphics, sx + CARD_PAD, cardY + CARD_PAD + STAT_TAB_H + CHAR_TAB_GAP, sLeftW - CARD_PAD * 2, 9, 9)
            OwSkeleton.bars(graphics, sx + sLeftW + CHAR_CARD_GAP + CARD_PAD, cardY + CARD_PAD + HEADER_H, sRightW - CARD_PAD * 2, 5, 16, 6)
            return
        }

        val gx = layout.gridX
        val gw = layout.gridW
        val leftW = (gw * 0.44).toInt()
        val rightW = gw - leftW - CHAR_CARD_GAP
        val rightX = gx + leftW + CHAR_CARD_GAP + CARD_PAD
        val rightInnerW = rightW - CARD_PAD * 2
        val contentTop = layout.scrollTop + CHAR_STRIP_H
        graphics.enableScissor(gx, contentTop, gx + gw, layout.scrollBottom)

        val cardY = charCardTop - charScrollY + layout.scrollTop
        drawCharCard(graphics, gx, cardY, leftW, charCardH)
        drawCharCard(graphics, gx + leftW + CHAR_CARD_GAP, cardY, rightW, charCardH)

        for (label in layout.labels) {
            if (label.docked) continue
            val ly = label.y - charScrollY + layout.scrollTop
            if (ly + STATS_ROW_H <= contentTop || ly >= layout.scrollBottom) continue
            val maxW = if (label.maxW > 0) label.maxW else gx + gw - label.x - 4
            graphics.text(font, trimToWidth(label.text, maxW.coerceAtLeast(20)), label.x, ly + 2, label.color)
        }
        val skillsHeaderY = CHARACTER_GRID_Y - charScrollY + layout.scrollTop
        graphics.fill(rightX, skillsHeaderY + HEADER_H - 3, rightX + rightInnerW, skillsHeaderY + HEADER_H - 2, OwTheme.HAIRLINE)

        for (placed in charPlaced) {
            val py = placed.y - charScrollY + layout.scrollTop
            if (py + CHAR_ROW_H <= contentTop || py >= layout.scrollBottom) continue
            when (val row = placed.row) {
                is CharacterStatRows.Header -> {
                    graphics.text(font, trimToWidth(row.text.uppercase(), placed.w - 40), placed.x, py + 3, OwTheme.ACCENT_DIM)
                    if (row.tag.isNotEmpty()) {
                        graphics.text(font, row.tag, placed.x + placed.w - font.width(row.tag) - 2, py + 3, OwTheme.TEXT_DIM)
                    }
                    graphics.fill(placed.x, py + CHAR_HEADER_H - 2, placed.x + placed.w, py + CHAR_HEADER_H - 1, OwTheme.HAIRLINE)
                }
                is CharacterStatRows.Stat -> {
                    if (placed.stripe) graphics.fill(placed.x, py, placed.x + placed.w, py + CHAR_ROW_H, CHAR_STRIPE)
                    val valueW = font.width(row.value)
                    graphics.text(font, trimToWidth(row.label, placed.w - valueW - 10), placed.x + 3, py + 3, row.labelColor)
                    graphics.text(font, row.value, placed.x + placed.w - valueW - 3, py + 3, row.valueColor)
                }
                is CharacterStatRows.Note -> {
                    graphics.text(font, trimToWidth(row.text, placed.w - 6), placed.x + 3, py + 3, OwTheme.TEXT_FAINT)
                }
            }
        }

        if (charStatsTab == CharStatsTab.IDENTIFICATIONS && !combatInfoPager.done) {
            val barsY = (charPlaced.maxOfOrNull { it.y + CHAR_ROW_H } ?: (CHARACTER_GRID_Y + STAT_TAB_H + CHAR_TAB_GAP)) + 4
            OwSkeleton.bars(graphics, gx + CARD_PAD, barsY - charScrollY + layout.scrollTop, leftW - CARD_PAD * 2, 6, 9)
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
                    graphics.text(font, trimToWidth(label, rightInnerW - 68), rightX + 20, sy + 5, OwTheme.TEXT)
                }
                skill.percent.removeSuffix("%").toFloatOrNull()?.let { pct ->
                    val barX = rightX + 20
                    val barW = rightInnerW - 20 - 48
                    val barY = sy + SKILL_ROW_H - BAR_H - 2
                    graphics.fill(barX, barY, barX + barW, barY + BAR_H, OwTheme.TILE_BORDER)
                    val fillW = (barW * (pct / 100f).coerceIn(0f, 1f)).toInt()
                    if (fillW > 0) graphics.fill(barX, barY, barX + fillW, barY + BAR_H, OwTheme.ACCENT)
                }
            }
        }
        graphics.disableScissor()
    }

    private fun drawCharCard(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, CHAR_CARD_BG)
        graphics.outline(x, y, w, h, OwTheme.HAIRLINE)
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
            WynnOverhaul.LOGGER.warn("WynnOverhaul character tab: menu no longer current, dropping {} input", kind)
            return
        }
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul character tab input failed", t)
        }
    }

    private data class CharSections(
        val name: String,
        val identity: List<Pair<String, Int>>,
        val combat: List<Pair<String, Int>>,
        val professions: List<Pair<String, Int>>,
        val identifications: List<Pair<String, Int>>,
    )

    private fun charRowsFor(sections: CharSections): List<CharacterStatRows.Row> {
        val rows = when (charStatsTab) {
            CharStatsTab.COMBAT -> CharacterStatRows.combat(sections.combat)
            CharStatsTab.IDENTIFICATIONS -> CharacterStatRows.identifications(sections.identifications) { combatInfoPager.colorOf(it) }
            CharStatsTab.PROFESSIONS -> CharacterStatRows.professions(sections.professions)
        }
        return rows.ifEmpty { listOf(CharacterStatRows.Note("Nothing to show yet", -1)) }
    }

    private fun layoutCharRows(rows: List<CharacterStatRows.Row>, x: Int, startY: Int, w: Int): Int {
        var y = startY
        var stripe = false
        var i = 0
        val halfW = (w - CHAR_COL_GAP) / 2
        fun place(row: CharacterStatRows.Row, px: Int, pw: Int, rowH: Int, striped: Boolean) {
            charPlaced.add(CharPlaced(row, px, y, pw, striped))
            charHoverZones.add(CharZone(px, y, pw, rowH, row.slot))
        }
        while (i < rows.size) {
            val row = rows[i]
            if (row is CharacterStatRows.Header) {
                if (y > startY) y += CHAR_HEADER_GAP
                place(row, x, w, CHAR_HEADER_H, false)
                y += CHAR_HEADER_H
                stripe = false
                i++
            } else if (row is CharacterStatRows.Stat && row.half) {
                place(row, x, halfW, CHAR_ROW_H, stripe)
                val next = rows.getOrNull(i + 1) as? CharacterStatRows.Stat
                if (next != null && next.half) {
                    place(next, x + halfW + CHAR_COL_GAP, halfW, CHAR_ROW_H, stripe)
                    i += 2
                } else {
                    i++
                }
                y += CHAR_ROW_H
                stripe = !stripe
            } else if (row is CharacterStatRows.Stat) {
                place(row, x, w, CHAR_ROW_H, stripe)
                y += CHAR_ROW_H
                stripe = !stripe
                i++
            } else {
                place(row, x, w, CHAR_ROW_H, false)
                y += CHAR_ROW_H
                i++
            }
        }
        return y
    }

    private val SECTION_MARKERS = listOf("Combat", "Identifications", "Professions")

    private fun charSections(menu: AbstractContainerMenu): CharSections {
        val lines = readCharacterStats(menu)
        if (lines.isEmpty()) return CharSections("--", emptyList(), emptyList(), emptyList(), emptyList())
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
        val professions = sectionAfter("Professions")
            .map { (text, slot) -> text.trimStart(' ', '-', '–', '•') to slot }
            .filter { (text, _) -> LEVEL_LINE.containsMatchIn(text) }
        val identifications = sectionAfter("Identifications")
        return CharSections(name, identity, combat, professions, identifications)
    }

    private class CombatInfoPager {
        var slot = -1
            private set
        var done = false
            private set
        var total = 0
            private set
        private val seenPages = HashSet<Int>()
        private val merged = LinkedHashSet<String>()
        private val colors = HashMap<String, Int>()
        private var ticksUntilNext = 0

        fun forceDone() {
            done = true
        }

        fun reset() {
            slot = -1
            seenPages.clear()
            merged.clear()
            colors.clear()
            ticksUntilNext = 0
            total = 0
            done = false
        }

        fun result(): List<String> = merged.toList()

        fun colorOf(line: String): Int? = colors[line.trim()]

        fun status(): String = if (total > 0) "Reading page ${seenPages.size.coerceAtLeast(1)} of $total..." else "Loading..."

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
            lore.firstOrNull { it.contains('«') }?.let { line -> total = line.count { it == '■' || it == '□' } }
            val statEnd = lore.indexOfLast { line -> PLAYER_STAT_KEYS.any { key -> line.startsWith(key) } }
            val pageIdx = lore.indexOfFirst { PAGE_LINE.containsMatchIn(it) }
            val pageNum = if (pageIdx >= 0) PAGE_LINE.find(lore[pageIdx])?.groupValues?.get(1)?.toIntOrNull() else null
            val contentStart = if (statEnd >= 0) statEnd + 1 else 0
            val contentEnd = if (pageIdx >= 0) pageIdx else lore.size
            if (contentStart >= contentEnd) {
                done = true
                return
            }
            val components = stack.get(DataComponents.LORE)?.lines()
            for (idx in contentStart until contentEnd) {
                val text = lore[idx].trim()
                if (text.isEmpty()) continue
                merged.add(text)
                components?.getOrNull(idx)?.let { LoreColors.lastColor(it) }?.let { colors.putIfAbsent(text, 0xFF000000.toInt() or it) }
            }
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

        var combatInfoSlot = -1
        var combatInfoLines: List<String>? = null
        for (slot in 0 until ownSlots) {
            val stack = menu.slots[slot].item
            if (stack.isEmpty) continue
            if (stripCodes(stack.hoverName.string).trim() == "Combat Information") {
                combatInfoSlot = slot
                combatInfoLines = WynnItemRarity.loreLines(stack).map { it.trim() }.filter { it.isNotEmpty() }
                break
            }
        }

        var identity: List<Pair<String, Int>>? = null
        val professions = ArrayList<Pair<String, Int>>()
        for (slot in 0 until ownSlots) {
            if (slot == combatInfoSlot) continue
            val stack = menu.slots[slot].item
            if (stack.isEmpty) continue
            val lore = WynnItemRarity.loreLines(stack)
            if (lore.isEmpty()) continue
            when {
                lore.any { it.startsWith("Total Lv:") } -> {
                    val identityLines = ArrayList<Pair<String, Int>>()
                    identityLines.add(stripCodes(stack.hoverName.string).trim() to slot)
                    for (key in PLAYER_STAT_KEYS) {
                        lore.firstOrNull { it.startsWith(key) }?.let { identityLines.add("  $it" to slot) }
                    }
                    identityCache = identityLines
                    identity = identityLines
                }
                lore.any { it.startsWith("Gathering Skills:") || it.startsWith("Crafting Skills:") } -> {
                    for (line in lore) {
                        val t = line.trim()
                        if (t.isEmpty()) continue
                        if (t.startsWith("Gathering Skills:") || t.startsWith("Crafting Skills:")) continue
                        professions.add("  $t" to slot)
                    }
                }
            }
        }

        val headSlot = combatInfoPager.slot
        lines.addAll(identity ?: identityCache ?: emptyList())
        if (combatInfoLines != null) {
            lines.add("Combat" to combatInfoSlot)
            for (text in combatInfoLines) lines.add("  $text" to combatInfoSlot)
        }
        if (combatInfoPager.done) {
            val ids = combatInfoPager.result()
            if (ids.isNotEmpty()) {
                lines.add("Identifications" to headSlot)
                for (text in ids) lines.add("  $text" to headSlot)
            }
        } else {
            lines.add("Identifications" to headSlot)
            lines.add("  ${combatInfoPager.status()}" to headSlot)
        }
        if (professions.isNotEmpty()) {
            lines.add("Professions" to professions.first().second)
            lines.addAll(professions)
        }
        return lines.take(MAX_CHAR_LINES)
    }

    private fun stripCodes(text: String): String {
        val noCodes = text.replace(SECTION_CODE, "")
        val sb = StringBuilder(noCodes.length)
        var i = 0
        while (i < noCodes.length) {
            val cp = noCodes.codePointAt(i)
            if (cp < 0xE000) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    private fun drawCarried(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, carried: ItemStack) {
        if (carried.isEmpty) return
        graphics.item(carried, mouseX - 8, mouseY - 8)
        graphics.itemDecorations(font, carried, mouseX - 8, mouseY - 8)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (OwDropdownOverlay.mouseClicked(event.x().toInt(), event.y().toInt(), height)) return true
        if (super.mouseClicked(event, doubled)) return true
        val x = event.x().toInt()
        val y = event.y().toInt()
        val button = event.button()
        if (button == 2 && WynnOverhaulItemDebug.tryCopyToClipboard(hoveredDebugStack())) return true
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

        if (pouchMenu != null && invTab == InvTab.INVENTORY) {
            findPouchTile(x, y)?.let {
                if (it >= 0 && (button == 0 || button == 1)) {
                    sendPouchInput(it, button, if (event.hasShiftDown()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
                }
                return true
            }
        }
        if (!inWindow(layout, x, y)) {
            if (!menu.carried.isEmpty && (button == 0 || button == 1)) {
                sendInput(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
            }
            return true
        }
        val player = Minecraft.getInstance().player ?: return true
        if (player.containerMenu !== menu) {
            if (pouchMenu != null && player.containerMenu === pouchMenu) return handlePouchMirrorClick(layout, x, y, button, event)
            return true
        }

        val slot = findTile(layout, x, y)
        if (slot < 0) {
            if (!menu.carried.isEmpty && (button == 0 || button == 1)) autoPlace(button)
            return true
        }
        return handleInventoryClick(slot, x, y, button, event, layout, player)
    }

    private fun handlePouchMirrorClick(layout: Layout, x: Int, y: Int, button: Int, event: MouseButtonEvent): Boolean {
        if (button != 0 && button != 1) return true
        val slot = findTile(layout, x, y)
        if (slot < 0) return true
        if (tryOpenPouch(slot, button, event.hasShiftDown())) return true
        if (rejectsReservedPlacement(slot, button, ContainerInput.PICKUP, activeCarried())) {
            blockReason(RESERVED_SLOT_MESSAGE)
            return true
        }
        val mirror = mirrorSlot(slot) ?: run {
            blockReason("Close the pouch first")
            return true
        }
        sendPouchInput(mirror, button, if (event.hasShiftDown()) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
        return true
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
        if (tryOpenPouch(slot, button, event.hasShiftDown())) return true
        if ((button == 0 || button == 1) && rejectsReservedPlacement(slot, button, ContainerInput.PICKUP, menu.carried)) {
            blockReason(RESERVED_SLOT_MESSAGE)
            return true
        }
        val stack = slotStack(slot)
        val isIngredientPouch = WynnPouches.isIngredientPouch(stack)
        val isSellConfirm = WynnPouches.isSellConfirm(stack) || WynnPouches.isConfirmMorph(stack)

        if ((button == 0 || button == 1) && !event.hasShiftDown() && isPowderApplication(menu.carried, stack)) {
            sendInput(slot, button, ContainerInput.PICKUP)
            return true
        }

        when (button) {
            0 -> {
                if (event.hasShiftDown()) {
                    sendInput(slot, 0, ContainerInput.QUICK_MOVE)
                    shiftDragging = WynnOverhaulConfig.current.shiftDragQuickMove
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

    private fun isPowderApplication(carried: ItemStack, target: ItemStack): Boolean {
        if (carried.isEmpty || target.isEmpty) return false
        if (!POWDER_NAME.containsMatchIn(TextClean.clean(carried.hoverName.string))) return false
        return !POWDER_NAME.containsMatchIn(TextClean.clean(target.hoverName.string))
    }

    private fun inWindow(layout: Layout, x: Int, y: Int): Boolean {
        val left = panelLeft()
        val top = panelTopFor(layout.panelH)
        return x in left until left + panelWidth() && y in top until top + layout.panelH
    }

    private val LEVEL_LINE = Regex("""Lv\.\s*\d+""")
    private val WHITESPACE_RUN = Regex("\\s+")
    private val SECTION_CODE = Regex("\u00A7.")
    private val POWDER_NAME = Regex("""(?:Earth|Thunder|Water|Fire|Air) Powder [IV]{1,3}$""")

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

        val reserved = onWynncraft()
        if (reserved && WynnPouches.isEmeraldPouch(stack) && menu.slots.getOrNull(EMERALD_POUCH_SLOT)?.item?.isEmpty == true) {
            return EMERALD_POUCH_SLOT
        }
        for (slot in MAIN_SLOTS) {
            if (reserved && slot == EMERALD_POUCH_SLOT) continue
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
        if (OwDropdownOverlay.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY, height)) return true
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

    private fun addShortcutDropdown(ox: Int, pt: Int) {
        val by = (pt - CLAIM_BTN_H - 2).coerceAtLeast(2)
        addRenderableWidget(
            OwDropdown(
                ox + MARGIN, by, SHORTCUT_DROPDOWN_W, CLAIM_BTN_H,
                "Commands",
                commandShortcuts.map { OwDropdownOverlay.Option(it.id, it.label) },
                { lastShortcutId },
            ) { id ->
                commandShortcuts.firstOrNull { it.id == id }?.let {
                    lastShortcutId = it.id
                    Minecraft.getInstance().connection?.sendCommand(it.command)
                }
            },
        )
    }

    private fun addClaimButtons(ox: Int, pt: Int) {
        val labels = ArrayList<Pair<String, () -> Unit>>()
        if (ObjectiveClaims.weeklyClaimable) labels.add("Claim weekly" to { ObjectiveClaims.claimWeekly(); rebuildWidgets() })
        if (ObjectiveClaims.objectiveClaimable) labels.add("Claim objective" to { ObjectiveClaims.claimObjective(); rebuildWidgets() })
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
        if (OwDropdownOverlay.isOpen && event.key() == KEY_ESCAPE) {
            OwDropdownOverlay.close()
            return true
        }
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
            WynnOverhaul.LOGGER.warn("WynnOverhaul inventory: menu no longer current, dropping {} input", kind)
            return
        }
        if (rejectsReservedPlacement(slot, button, kind, menu.carried)) {
            blockReason(RESERVED_SLOT_MESSAGE)
            return
        }
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul inventory input failed", t)
        }
    }

    override fun onClose() {
        WynnOverhaulInventory.pendingTransitionTab = null
        leaveContainers()
        WynnOverhaulConfig.current.save()
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
        private var lastShown: WynnOverhaulInventoryScreen? = null
        private var lastShownNanos = 0L
        private const val RECENT_NANOS = 400_000_000L

        fun recent(): WynnOverhaulInventoryScreen? =
            lastShown?.takeIf { System.nanoTime() - lastShownNanos < RECENT_NANOS }

        private val dockedStackCache = HashMap<Int, ItemStack>()
        fun clearDockedCache() = dockedStackCache.clear()
        const val MARGIN = 10
        const val GAP = 8
        const val TAB_W = 64
        const val TAB_H = 16
        const val CLAIM_BTN_H = 14
        const val SHORTCUT_DROPDOWN_W = 118
        const val LEFT_CLICK = 0
        const val RIGHT_CLICK = 1
        const val POUCH_SCAN_SIZE = 36
        const val POUCH_MIRROR_SIZE = 36
        const val POUCH_MIRROR_COUNT = 27
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
        const val JOURNAL_TOP_SHIFT = CONTENT_TOP_REL - SEARCH_Y_REL
        const val JOURNAL_SECTION_GAP = 6
        const val JOURNAL_TAB_H = 16
        const val JOURNAL_DETAIL_LINE_H = 11
        const val JOURNAL_MAX_DETAIL_LINES = 16
        const val JOURNAL_ACTION_BTN_H = 18
        const val JOURNAL_LIST_ROWS = 11

        const val STATS_ROW_H = 11

        const val SKILL_ROW_H = 22
        const val BAR_H = 3
        const val OPENER_BTN_H = 20
        const val STAT_TAB_H = 16
        const val CHAR_SECTION_GAP = 6
        const val SKELETON_CARD_H = 150
        const val JOURNAL_CARD_PAD = 6
        const val JOURNAL_PROGRESS_H = 22
        const val CHAR_CARD_BG = 0x38000000
        const val CHAR_STRIPE = 0x16FFFFFF
        const val CARD_PAD = 6
        const val CHAR_TAB_GAP = 5
        const val CHAR_COL_GAP = 6
        const val CHAR_ROW_H = 13
        const val CHAR_HEADER_H = 15
        const val CHAR_HEADER_GAP = 4
        const val MAX_CHAR_LINES = 160
        const val CHAR_MENU_ROW_Y = 0
        const val OPENER_ROWS = 2
        const val OPENER_ROW_GAP = 2
        const val CHAR_STRIP_TEXT_Y = CHAR_MENU_ROW_Y + OPENER_ROWS * OPENER_BTN_H + (OPENER_ROWS - 1) * OPENER_ROW_GAP + 8
        const val CHAR_STRIP_TEXT_H = 40
        const val CHAR_STRIP_H = CHAR_STRIP_TEXT_Y + CHAR_STRIP_TEXT_H
        const val CHARACTER_GRID_Y = CHAR_STRIP_H + 4 + CARD_PAD
        const val MENU_BTN_GAP = 2

        const val CHAR_CARD_GAP = 8

        const val INGREDIENT_POUCH_SLOT = 13
        const val EMERALD_POUCH_SLOT = 14
        const val RESERVED_SLOT_MESSAGE = "Only an Emerald Pouch fits in this slot"
        private const val INVENTORY_CACHE_NANOS = 1_000_000_000L
        const val SHIFT_DRAG_STEP = 4
        const val POUCH_LINES = 12
        const val WARN_NANOS = 2_500_000_000L
        const val POUCH_SKELETON_ROWS = 4
        const val POUCH_PENDING_NANOS = 3_000_000_000L

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
