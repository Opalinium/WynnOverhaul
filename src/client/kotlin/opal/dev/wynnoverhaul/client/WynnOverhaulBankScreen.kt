package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.CAPTION_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.FOOTER_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.GLASS_BG
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.HOVER_BORDER
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.MARGIN
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.OUTSIDE_SLOT
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_ENTITY_SIZE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.PREVIEW_W
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.SEARCH_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.SECTION_GAP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TAB_H
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TAB_Y_REL
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG_EMPTY
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_STEP
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_TINT_ALPHA

class WynnOverhaulBankScreen(private val menu: AbstractContainerMenu) : Screen(Component.literal("Bank")) {
    private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun contains(px: Int, py: Int): Boolean = px >= x && px < x + w && py >= y && py < y + h
    }

    private class Tile(val rect: Rect, val slot: Int)

    private class DockTile(val rect: Rect, val stack: ItemStack)

    private class Entry(val page: Int, val slot: Int, val stack: ItemStack)

    private class Control(
        val rect: Rect,
        val label: String,
        val enabled: Boolean,
        val accent: Boolean,
        val tint: Int,
        val tipSlot: Int,
        val action: () -> Unit,
    )

    private class Frame(
        val invX: Int,
        val invW: Int,
        val bankX: Int,
        val bankW: Int,
        val pt: Int,
        val panelH: Int,
        val invPanelH: Int,
        val invGridX: Int,
        val invCols: Int,
        val bankGridX: Int,
        val bankCols: Int,
        val bankTop: Int,
        val bankBottom: Int,
        val bankContentH: Int,
        val hotbarY: Int,
        val hotbar: List<Tile>,
        val inventory: List<Tile>,
        val controls: List<Control>,
    )

    private var mouseX = 0
    private var mouseY = 0
    private var bankScroll = 0
    private var searchField: OwTextField? = null
    private var sortButton: OwDropdown? = null
    private var storageDropdown: OwDropdown? = null
    private var warnText: String? = null
    private var warnUntilNanos = 0L
    private var recordedSignature = 0
    private var lastHash = 0
    private var stableTicks = 0
    private var dock: List<DockTile> = emptyList()
    private var dockLabels: List<Triple<String, Int, Int>> = emptyList()
    private var doll: Rect? = null
    private var frame: Frame? = null
    private var entries: List<Entry> = emptyList()
    private var entriesKey = 0
    private var entriesBuilt = false
    private var entryOrder: List<Int> = emptyList()
    private var bankTiles: List<Pair<Rect, Int>> = emptyList()

    private fun stackAt(slot: Int): ItemStack = menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

    private fun pageNumber(slot: Int): Int? {
        val stack = stackAt(slot)
        if (stack.isEmpty) return null
        val name = stack.get(DataComponents.CUSTOM_NAME)?.string ?: return null
        return PAGE.find(TextClean.clean(name))?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun ready(): Boolean = !stackAt(QUICK_SLOT).isEmpty && !stackAt(PREV_SLOT).isEmpty && !stackAt(NEXT_SLOT).isEmpty

    private fun currentPage(): Int = pageNumber(PREV_SLOT)?.plus(1) ?: pageNumber(NEXT_SLOT)?.minus(1) ?: 1

    private fun nextLocked(): Boolean =
        WynnItemRarity.loreLines(stackAt(NEXT_SLOT)).any { it.contains("Purchase", ignoreCase = true) }

    private fun nextAvailable(): Boolean = pageNumber(NEXT_SLOT) != null && !nextLocked()

    private fun jumpTargets(slot: Int): List<Int> {
        val lines = stackAt(slot).get(DataComponents.LORE)?.lines().orEmpty().map { it.string }
        val start = lines.indexOfFirst { it.contains("Jump to") }
        if (start < 0) return emptyList()
        return lines.drop(start + 1).mapNotNull { PAGE.find(TextClean.clean(it))?.groupValues?.get(1)?.toIntOrNull() }
    }

    private fun storageName(): String {
        val lines = stackAt(STORAGE_SLOT).get(DataComponents.LORE)?.lines().orEmpty().map { it.string }
        val body = lines.joinToString(" ") { TextClean.clean(it) }.lowercase()
        if (body.contains("account bank")) return "Account"
        if (body.contains("character bank")) return "Character"
        val active = lines.firstOrNull { it.contains("§6- ") } ?: return "Character"
        return TextClean.clean(active).removePrefix("-").trim().removeSuffix("Storage").trim().ifEmpty { "Character" }
    }

    private fun plainName(stack: ItemStack): String = TextClean.clean(stack.hoverName.string)

    private fun matches(stack: ItemStack, query: String): Boolean {
        if (query.isEmpty()) return true
        if (stack.isEmpty) return false
        if (plainName(stack).lowercase().contains(query)) return true
        return WynnItemRarity.loreLines(stack).any { it.lowercase().contains(query) }
    }

    private fun inventoryHidden(slot: Int): Boolean {
        val offset = slot - INVENTORY_START
        if (offset in 0 until ACCESSORY_COUNT) return true
        if (offset == EMERALD_POUCH_OFFSET) return true
        val stack = stackAt(slot)
        return HotbarHudElement.isHidden(stack) && !WynnPouches.isEmeraldPouch(stack)
    }

    private fun depositable(slot: Int): Boolean = slot >= INVENTORY_START + ACCESSORY_COUNT && slot != INVENTORY_START + EMERALD_POUCH_OFFSET

    private fun availableWidth(): Int = width - MARGIN * 2

    private fun dockW(): Int = TILE + GAP + PREVIEW_W + GAP + TILE

    private fun showDock(): Boolean = availableWidth() >= dockW() + GAP + invPanelW() + GAP + bankPanelW()

    private fun invPanelW(): Int = INV_COLS * TILE_STEP + 2 * MARGIN

    private fun bankPanelW(): Int = BANK_COLS * TILE_STEP + 2 * MARGIN

    private fun startX(): Int {
        val total = (if (showDock()) dockW() + GAP else 0) + invPanelW() + GAP + bankPanelW()
        return ((width - total) / 2).coerceAtLeast(MARGIN)
    }

    private fun dockX(): Int = startX()

    private fun invPanelX(): Int = startX() + (if (showDock()) dockW() + GAP else 0)

    private fun bankPanelX(): Int = invPanelX() + invPanelW() + GAP

    override fun init() {
        OwDropdownOverlay.close()
        val x0 = bankPanelX() + MARGIN
        val inner = bankPanelW() - MARGIN * 2
        storageDropdown = OwDropdown(
            x0, 0, STORAGE_DROPDOWN_W, SEARCH_H,
            "",
            STORAGES.map { OwDropdownOverlay.Option(it, it) },
            { storageName() },
        ) {
            if (it != storageName()) switchStorage()
        }.also { addRenderableWidget(it) }
        val sortW = font.width("Sort: Default") + 34
        searchField = OwTextField(font, x0, 0, inner - sortW - 4, SEARCH_H).also {
            it.value = BankSession.search
            it.setResponder { v ->
                BankSession.search = v
                bankScroll = 0
            }
            addRenderableWidget(it)
        }
        sortButton = OwDropdown(
            x0 + inner - sortW, 0, sortW, SEARCH_H,
            "Sort",
            InventorySort.entries.map { OwDropdownOverlay.Option(it.name, it.label) },
            { InventorySort.parse(WynnOverhaulConfig.current.inventorySort).name },
        ) {
            val config = WynnOverhaulConfig.current
            config.inventorySort = it
            config.save()
        }.also { addRenderableWidget(it) }
    }

    override fun tick() {
        super.tick()
        if (!ready()) return
        val player = Minecraft.getInstance().player ?: return
        if (player.containerMenu !== menu) return
        val page = currentPage()
        val storage = storageName()
        recordPage(storage, page)
        BankSession.noteLock(storage, page, pageNumber(NEXT_SLOT) != null && nextLocked())
        val hash = contentHash()
        if (hash == lastHash) stableTicks++ else {
            stableTicks = 0
            lastHash = hash
        }
        if (BankSession.shouldAutoScan(storage)) BankSession.startScan(page)
        BankSession.arrived(page)
        if (stableTicks < STABLE_TICKS_REQUIRED) return
        if (BankSession.expired() || BankSession.attempts > MAX_ATTEMPTS) {
            BankSession.cancel()
            warn("Could not change page")
            return
        }
        val pending = BankSession.pending
        if (pending != null && pending.page == page && BankSession.navTarget == null) {
            BankSession.consumePending()
            runPending(pending)
            return
        }
        BankSession.advanceScan(page, nextAvailable())
        val target = BankSession.navTarget ?: return
        if (target == page || BankSession.waiting(page, hash)) return
        navigate(page, target, hash)
    }

    private fun jumpOptions(): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (slot in intArrayOf(PREV_SLOT, NEXT_SLOT)) {
            for ((index, page) in jumpTargets(slot).withIndex()) {
                if (index in 0..8) out.add(Triple(slot, index, page))
            }
        }
        return out
    }

    private fun navCost(from: Int, target: Int): Int {
        var best = kotlin.math.abs(target - from)
        for ((_, _, page) in jumpOptions()) {
            if (page != from) best = minOf(best, 1 + kotlin.math.abs(target - page))
        }
        return best
    }

    private fun navigate(page: Int, target: Int, hash: Int) {
        var best = kotlin.math.abs(target - page)
        var jump: Triple<Int, Int, Int>? = null
        for (option in jumpOptions()) {
            if (option.third == page) continue
            val cost = 1 + kotlin.math.abs(target - option.third)
            if (cost < best) {
                best = cost
                jump = option
            }
        }
        if (jump != null) {
            BankSession.markClicked(page, hash)
            send(jump.first, jump.second, ContainerInput.SWAP)
            return
        }
        val slot = if (target > page) NEXT_SLOT else PREV_SLOT
        if (pageNumber(slot) == null || (slot == NEXT_SLOT && nextLocked())) {
            BankSession.cancel()
            warn(if (slot == NEXT_SLOT) "That page is locked" else "No earlier page")
            return
        }
        BankSession.markClicked(page, hash)
        send(slot, 0, ContainerInput.PICKUP)
    }

    private fun runPending(pending: BankSession.Pending) {
        if (pending.needsItem && stackAt(pending.slot).isEmpty) {
            warn("That item moved, page refreshed")
            return
        }
        send(pending.slot, pending.button, pending.kind)
    }

    private fun contentHash(): Int {
        var h = 17
        for (i in 0 until BANK_SLOTS + 9) {
            val stack = stackAt(i)
            h = 31 * h + if (stack.isEmpty) 0 else 31 * System.identityHashCode(stack) + stack.count
        }
        return h
    }

    private fun recordPage(storage: String, page: Int) {
        var signature = page * 31 + storage.hashCode()
        for (i in 0 until BANK_SLOTS) {
            val stack = stackAt(i)
            signature = 31 * signature + if (stack.isEmpty) 0 else 31 * System.identityHashCode(stack) + stack.count
        }
        if (signature == recordedSignature) return
        recordedSignature = signature
        BankSession.record(storage, page, (0 until BANK_SLOTS).map { stackAt(it) })
    }

    private fun warn(text: String) {
        warnText = text
        warnUntilNanos = System.nanoTime() + WARN_NANOS
    }

    private fun refreshEntries(storage: String, query: String) {
        val sortName = WynnOverhaulConfig.current.inventorySort
        val key = 31 * (31 * (31 * BankSession.version + query.hashCode()) + sortName.hashCode()) + storage.hashCode()
        if (entriesBuilt && key == entriesKey) return
        entriesBuilt = true
        entriesKey = key
        val list = ArrayList<Entry>()
        for ((page, data) in BankSession.pagesOf(storage)) {
            for ((slot, stack) in data.stacks.withIndex()) {
                if (!stack.isEmpty && matches(stack, query)) list.add(Entry(page, slot, stack))
            }
        }
        entries = list
        entryOrder = InventorySort.parse(sortName).apply(list.indices.toList()) { list[it].stack }
    }

    private fun freeBankSlot(storage: String): Pair<Int, Int>? {
        val page = currentPage()
        val live = (0 until BANK_SLOTS).firstOrNull { stackAt(it).isEmpty }
        if (live != null) return page to live
        var bestPage = -1
        var bestSlot = -1
        var bestCost = Int.MAX_VALUE
        for ((p, data) in BankSession.pagesOf(storage)) {
            if (p == page) continue
            val slot = data.stacks.indexOfFirst { it.isEmpty }
            if (slot < 0) continue
            val cost = navCost(page, p)
            if (cost < bestCost) {
                bestCost = cost
                bestPage = p
                bestSlot = slot
            }
        }
        if (bestPage >= 0) return bestPage to bestSlot
        return null
    }

    private fun buildFrame(): Frame {
        val query = BankSession.search.trim().lowercase()
        val storage = storageName()
        refreshEntries(storage, query)
        val invX = invPanelX()
        val bankX = bankPanelX()
        val invW = invPanelW()
        val bankW = bankPanelW()
        val invGridX = invX + MARGIN
        val bankGridX = bankX + MARGIN
        val invCols = INV_COLS
        val bankCols = BANK_COLS

        val invOrder = (INVENTORY_START until INVENTORY_START + 27).filter { !inventoryHidden(it) && !stackAt(it).isEmpty }
        val invRows = ((invOrder.size + invCols - 1) / invCols).coerceAtLeast(1)
        val invH = CAPTION_H + TILE_STEP + SECTION_GAP + CAPTION_H + invRows * TILE_STEP
        val bankRows = ((entryOrder.size + bankCols - 1) / bankCols).coerceAtLeast(1)
        val bankContentH = bankRows * TILE_STEP

        val fullH = height - MARGIN * 2 + 8
        val tail = GAP + FOOTER_H + 4
        val leftIdeal = LEFT_TOP + invH + tail
        val rightIdeal = RIGHT_TOP + IDEAL_BANK_ROWS * TILE_STEP + tail
        val panelH = minOf(fullH, maxOf(leftIdeal, rightIdeal)).coerceAtLeast(RIGHT_TOP + MIN_BANK_H + tail)
        val invPanelH = panelH
        val pt = ((height - panelH) / 2).coerceAtLeast(MARGIN - 4)

        val bankTop = pt + RIGHT_TOP
        val bankBottom = pt + panelH - 4 - FOOTER_H - GAP
        val maxScroll = (bankContentH - (bankBottom - bankTop)).coerceAtLeast(0)
        bankScroll = bankScroll.coerceIn(0, maxScroll)

        val hotbarY = pt + LEFT_TOP + CAPTION_H
        val hotbar = ArrayList<Tile>()
        for (i in 0 until INV_COLS) {
            val slot = INVENTORY_START + 27 + i
            val stack = stackAt(slot)
            val show = !HotbarHudElement.isHidden(stack) || WynnPouches.isEmeraldPouch(stack)
            val visible = show
            hotbar.add(Tile(Rect(invGridX + i * TILE_STEP, hotbarY, TILE, TILE), if (visible) slot else -1))
        }
        val invGridTop = hotbarY + TILE_STEP + SECTION_GAP + CAPTION_H
        val inventory = ArrayList<Tile>()
        for ((i, slot) in invOrder.withIndex()) {
            inventory.add(Tile(Rect(invGridX + (i % invCols) * TILE_STEP, invGridTop + (i / invCols) * TILE_STEP, TILE, TILE), slot))
        }

        val controls = ArrayList<Control>()
        val quickReady = !stackAt(QUICK_SLOT).isEmpty
        val scanning = BankSession.scanning
        val rowY = pt + ACTION_ROW_Y
        val inner = bankW - MARGIN * 2
        val lockedAfter = BankSession.lockedPageAfter(storage)
        val labels = ArrayList<String>()
        val tips = ArrayList<Int>()
        val enables = ArrayList<Boolean>()
        val actions = ArrayList<() -> Unit>()
        labels.add("Stash"); tips.add(QUICK_SLOT); enables.add(quickReady)
        actions.add { BankSession.cancel(); send(QUICK_SLOT, 0, ContainerInput.PICKUP) }
        labels.add("Dump"); tips.add(QUICK_SLOT); enables.add(quickReady)
        actions.add { BankSession.cancel(); send(QUICK_SLOT, 1, ContainerInput.PICKUP) }
        labels.add("Dump all"); tips.add(QUICK_SLOT); enables.add(quickReady)
        actions.add { BankSession.cancel(); send(QUICK_SLOT, 1, ContainerInput.QUICK_MOVE) }
        if (lockedAfter != null) {
            labels.add("Buy page ${lockedAfter + 1}")
            tips.add(if (lockedAfter == currentPage()) NEXT_SLOT else -1)
            enables.add(true)
            actions.add {
                BankSession.cancel()
                if (lockedAfter == currentPage()) {
                    send(NEXT_SLOT, 0, ContainerInput.PICKUP)
                } else {
                    BankSession.act(BankSession.Pending(lockedAfter, NEXT_SLOT, 0, ContainerInput.PICKUP, true))
                }
            }
        }
        val count = labels.size
        val usable = inner - (count - 1) * 2
        val weights = IntArray(count) { if (it == 3) 6 else if (it == 2) 4 else 3 }
        val widths = IntArray(count) { usable * weights[it] / weights.sum() }
        widths[count - 1] += usable - widths.sum()
        var ax = bankGridX
        for (i in 0 until count) {
            controls.add(Control(Rect(ax, rowY, widths[i], ACTION_H), labels[i], enables[i], i == 3, if (i == 3) OwTheme.ACCENT else OwTheme.TEXT, tips[i], actions[i]))
            ax += widths[i] + 2
        }
        controls.add(
            Control(
                Rect(bankGridX + inner - RESCAN_W, pt + TOP_ROW_Y, RESCAN_W, ACTION_H), if (scanning) "Stop" else "Rescan", ready(), scanning, OwTheme.TEXT, -1,
            ) {
                if (BankSession.scanning) {
                    BankSession.cancel()
                } else {
                    BankSession.clearPages(storage)
                    recordedSignature = 0
                    BankSession.startScan(currentPage())
                }
            },
        )

        return Frame(invX, invW, bankX, bankW, pt, panelH, invPanelH, invGridX, invCols, bankGridX, bankCols, bankTop, bankBottom, bankContentH, hotbarY, hotbar, inventory, controls)
    }

    private fun switchStorage() {
        BankSession.cancel()
        recordedSignature = 0
        send(STORAGE_SLOT, 0, ContainerInput.PICKUP)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractBlurredBackground(graphics)
        graphics.fill(0, 0, width, height, OwTheme.BG_DIM)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val covered = OwDropdownOverlay.covers(mouseX, mouseY, height)
        val mx = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseX
        val my = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseY
        renderBank(graphics, mx, my, partialTick)
        OwDropdownOverlay.render(graphics, mouseX, mouseY, height)
    }

    private fun renderBank(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        val f = buildFrame()
        frame = f
        storageDropdown?.y = f.pt + TOP_ROW_Y
        searchField?.y = f.pt + SEARCH_ROW_Y
        sortButton?.y = f.pt + SEARCH_ROW_Y

        val storage = storageName()
        panel(graphics, f.invX - 4, f.pt, f.invW + 8, f.invPanelH)
        panel(graphics, f.bankX - 4, f.pt, f.bankW + 8, f.panelH)

        val hoverControl = f.controls.firstOrNull { it.rect.contains(mouseX, mouseY) }
        val inBank = hoverControl == null && mouseInBank(f, mouseX, mouseY)
        val hoverHotbar = f.hotbar.firstOrNull { it.slot >= 0 && it.rect.contains(mouseX, mouseY) }
        val hoverInv = f.inventory.firstOrNull { it.rect.contains(mouseX, mouseY) }

        val scanning = BankSession.scanning
        val pages = BankSession.pagesOf(storage)
        val countText = "${entryOrder.size} items"
        val textX = f.bankGridX + STORAGE_DROPDOWN_W + 8
        graphics.text(font, truncateToWidth(font, countText, f.bankW - MARGIN * 2 - STORAGE_DROPDOWN_W - RESCAN_W - 16), textX, f.pt + TOP_ROW_Y + (SEARCH_H - 8) / 2, OwTheme.ACCENT)
        for (control in f.controls) drawControl(graphics, control, control === hoverControl)

        var hoverEntry: Entry? = null
        graphics.enableScissor(f.bankX, f.bankTop, f.bankX + f.bankW, f.bankBottom)
        val placed = ArrayList<Pair<Rect, Int>>()
        for ((i, index) in entryOrder.withIndex()) {
            val rect = Rect(f.bankGridX + (i % f.bankCols) * TILE_STEP, f.bankTop + (i / f.bankCols) * TILE_STEP - bankScroll, TILE, TILE)
            if (rect.y + TILE <= f.bankTop || rect.y >= f.bankBottom) continue
            placed.add(rect to index)
            val hovered = inBank && rect.contains(mouseX, mouseY)
            if (hovered) hoverEntry = entries[index]
            drawLoose(graphics, rect, entries[index].stack, hovered)
        }
        bankTiles = placed
        if (entryOrder.isEmpty()) {
            if (scanning || pages.isEmpty()) {
                OwSkeleton.bars(graphics, f.bankGridX, f.bankTop + 2, f.bankW - MARGIN * 2, 4, TILE, TILE_STEP - TILE)
            } else {
                graphics.text(font, if (BankSession.search.isBlank()) "(bank is empty)" else "(no matches)", f.bankGridX + 2, f.bankTop + 4, OwTheme.TEXT_DIM)
            }
        }
        graphics.disableScissor()
        drawScrollbar(graphics, f)

        graphics.text(font, "Hotbar", f.invGridX, f.hotbarY - CAPTION_H + 2, OwTheme.TEXT_DIM)
        for (tile in f.hotbar) if (tile.slot >= 0) drawTile(graphics, tile.rect, stackAt(tile.slot), tile === hoverHotbar)
        graphics.text(font, "Inventory", f.invGridX, f.hotbarY + TILE_STEP + SECTION_GAP + 2, OwTheme.TEXT_DIM)
        for (tile in f.inventory) drawLoose(graphics, tile.rect, stackAt(tile.slot), tile === hoverInv)
        drawFooters(graphics, f, hoverEntry, storage)
        val hoverDock = drawDock(graphics, f, mouseX, mouseY)

        if (!menu.carried.isEmpty && !BankSession.navigating()) {
            graphics.item(menu.carried, mouseX - 8, mouseY - 8)
            graphics.itemDecorations(font, menu.carried, mouseX - 8, mouseY - 8)
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        if (menu.carried.isEmpty) {
            val entry = hoverEntry
            val stack = when {
                hoverControl != null -> if (hoverControl.tipSlot >= 0) stackAt(hoverControl.tipSlot) else ItemStack.EMPTY
                entry != null -> entry.stack
                hoverHotbar != null -> stackAt(hoverHotbar.slot)
                hoverInv != null -> stackAt(hoverInv.slot)
                hoverDock != null -> hoverDock
                else -> ItemStack.EMPTY
            }
            if (!stack.isEmpty) graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY)
        }
    }

    private fun drawDock(graphics: GuiGraphicsExtractor, f: Frame, mouseX: Int, mouseY: Int): ItemStack? {
        if (!showDock()) return null
        val player = Minecraft.getInstance().player ?: return null
        val dockX = dockX()
        val dollX = dockX + TILE + GAP
        val dollTop = ((height - PREVIEW_H) / 2).coerceAtLeast(MARGIN)
        val armorX = dollX - GAP - TILE
        val armorTop = dollTop + (PREVIEW_H - 4 * TILE_STEP) / 2
        val tiles = ArrayList<DockTile>()
        for (i in 0 until 4) {
            tiles.add(DockTile(Rect(armorX, armorTop + i * TILE_STEP, TILE, TILE), player.inventoryMenu.slots.getOrNull(ARMOR_FIRST_SLOT + i)?.item ?: ItemStack.EMPTY))
        }
        val rightX = dollX + PREVIEW_W + GAP
        tiles.add(DockTile(Rect(rightX, dollTop, TILE, TILE), player.inventoryMenu.slots.getOrNull(OFFHAND_MENU_SLOT)?.item ?: ItemStack.EMPTY))
        val jx = maxOf(dockX, armorX - TILE_STEP)
        val jy = armorTop + 4 * TILE_STEP + 12
        for (i in 0 until ACCESSORY_COUNT) {
            tiles.add(DockTile(Rect(jx + (i % 2) * TILE_STEP, jy + CAPTION_H + (i / 2) * TILE_STEP, TILE, TILE), stackAt(INVENTORY_START + i)))
        }
        graphics.text(font, "Accessories", jx, jy + 2, OwTheme.TEXT_DIM)
        val level = WynnLevelTracker.level
        if (level != null) {
            val text = "Lv $level"
            graphics.text(font, text, dollX + (PREVIEW_W - font.width(text)) / 2, maxOf(2, dollTop - 14), OwTheme.TEXT_DIM)
        }
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics, dollX, dollTop, dollX + PREVIEW_W, dollTop + PREVIEW_H, PREVIEW_ENTITY_SIZE, 0.0625f,
            mouseX.toFloat(), mouseY.toFloat(), player,
        )
        var hovered: ItemStack? = null
        for (tile in tiles) {
            val over = tile.rect.contains(mouseX, mouseY)
            drawTile(graphics, tile.rect, tile.stack, over)
            if (over && !tile.stack.isEmpty) hovered = tile.stack
        }
        return hovered
    }

    private fun drawScrollbar(graphics: GuiGraphicsExtractor, f: Frame) {
        val viewH = f.bankBottom - f.bankTop
        if (f.bankContentH <= viewH) return
        val x = f.bankX + f.bankW - 5
        val thumbH = (viewH * viewH / f.bankContentH).coerceAtLeast(12)
        val maxScroll = f.bankContentH - viewH
        val thumbY = f.bankTop + (viewH - thumbH) * bankScroll / maxScroll
        graphics.fill(x, f.bankTop, x + 2, f.bankBottom, OwTheme.TILE_BORDER)
        graphics.fill(x, thumbY, x + 2, thumbY + thumbH, OwTheme.HAIRLINE)
    }

    private fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, GLASS_BG)
        graphics.fill(x, y, x + w, y + 1, OwTheme.HAIRLINE)
        graphics.fill(x, y + h - 1, x + w, y + h, OwTheme.HAIRLINE)
        graphics.fill(x, y, x + 1, y + h, OwTheme.HAIRLINE)
        graphics.fill(x + w - 1, y, x + w, y + h, OwTheme.HAIRLINE)
    }

    private fun drawTile(graphics: GuiGraphicsExtractor, r: Rect, stack: ItemStack, hovered: Boolean) {
        val rarityRgb = if (stack.isEmpty) null else WynnItemRarity.of(stack)?.colorRgb
        graphics.fill(r.x, r.y, r.x + TILE, r.y + TILE, rarityRgb?.let { TILE_TINT_ALPHA or it } ?: TILE_BG_EMPTY)
        if (!stack.isEmpty) {
            graphics.item(stack, r.x + 2, r.y + 2)
            graphics.itemDecorations(font, stack, r.x + 2, r.y + 2)
        }
        val border = if (stack.isEmpty) OwTheme.TILE_BORDER else (rarityRgb?.let { 0xFF000000.toInt() or it } ?: OwTheme.TILE_BORDER)
        graphics.outline(r.x, r.y, TILE, TILE, border)
        if (hovered) graphics.outline(r.x - 1, r.y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun drawLoose(graphics: GuiGraphicsExtractor, r: Rect, stack: ItemStack, hovered: Boolean) {
        if (stack.isEmpty) return
        graphics.item(stack, r.x + 2, r.y + 2)
        graphics.itemDecorations(font, stack, r.x + 2, r.y + 2)
        if (hovered) graphics.outline(r.x - 1, r.y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun drawControl(graphics: GuiGraphicsExtractor, c: Control, hovered: Boolean) {
        val r = c.rect
        val active = hovered && c.enabled
        graphics.fill(r.x, r.y, r.x + r.w, r.y + r.h, if (c.accent && c.enabled) OwTheme.ACCENT_DIM else if (active) OwTheme.TILE_HOVER else TILE_BG)
        val border = when {
            !c.enabled -> OwTheme.TILE_BORDER
            active -> OwTheme.BORDER_BRIGHT
            c.accent -> OwTheme.ACCENT
            else -> OwTheme.HAIRLINE
        }
        graphics.outline(r.x, r.y, r.w, r.h, border)
        val color = if (c.enabled) c.tint else OwTheme.TEXT_FAINT
        graphics.centeredText(font, truncateToWidth(font, c.label, r.w - 4), r.x + r.w / 2, r.y + (r.h - 8) / 2, color)
    }

    private fun drawFooters(graphics: GuiGraphicsExtractor, f: Frame, hover: Entry?, storage: String) {
        val fy = f.pt + f.panelH - 4 - FOOTER_H + 3
        val free = (INVENTORY_START + ACCESSORY_COUNT until INVENTORY_START + 36).count { depositable(it) && stackAt(it).isEmpty }
        val freeText = if (free > 0) "$free free" else "Full"
        val emeralds = "%,d emeralds".format(WynnPouches.totalEmeralds((INVENTORY_START until menu.slots.size).map { stackAt(it) }))
        val sep = " · "
        val invX = f.invX + f.invW / 2 - (font.width(freeText) + font.width(sep) + font.width(emeralds)) / 2
        val invFy = f.pt + f.invPanelH - 4 - FOOTER_H + 3
        graphics.text(font, freeText, invX, invFy, if (free > 0) OwTheme.TEXT_DIM else OwTheme.BAD)
        graphics.text(font, sep, invX + font.width(freeText), invFy, OwTheme.TEXT_DIM)
        graphics.text(font, emeralds, invX + font.width(freeText) + font.width(sep), invFy, OwTheme.GOOD)

        val cx = f.bankX + f.bankW / 2
        val warning = warnText
        if (warning != null && System.nanoTime() < warnUntilNanos) {
            graphics.text(font, warning, cx - font.width(warning) / 2, fy, OwTheme.BAD)
            return
        }
        val text = if (BankSession.scanning) {
            "Scanning \u00B7 ${BankSession.scanMillisPerPage()} ms/page"
        } else if (hover != null) {
            "Page ${hover.page} \u00B7 slot ${hover.slot + 1}"
        } else {
            "${BankSession.pagesOf(storage).values.sumOf { data -> data.stacks.count { it.isEmpty } }} free \u00B7 ${BankSession.pagesOf(storage).size} pages"
        }
        graphics.text(font, text, cx - font.width(text) / 2, fy, OwTheme.TEXT_DIM)
    }

    private fun mouseInBank(f: Frame, mx: Int, my: Int): Boolean =
        mx >= f.bankX && mx < f.bankX + f.bankW && my >= f.bankTop && my < f.bankBottom

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (OwDropdownOverlay.mouseClicked(mx, my, height)) return true
        if (super.mouseClicked(event, doubleClick)) return true
        val f = frame ?: return true
        val button = event.button()
        val control = f.controls.firstOrNull { it.rect.contains(mx, my) }
        if (control != null) {
            if (button == 0 && control.enabled) control.action()
            return true
        }
        if (BankSession.scanning) {
            warn("Scanning pages, press Stop to interrupt")
            return true
        }
        val carried = menu.carried
        val hotbarTile = f.hotbar.firstOrNull { it.slot >= 0 && it.rect.contains(mx, my) }
        val invTile = f.inventory.firstOrNull { it.rect.contains(mx, my) }
        val invSlot = hotbarTile?.slot ?: invTile?.slot
        if (invSlot != null) {
            clickInventory(invSlot, button)
            return true
        }
        if (mouseInBank(f, mx, my)) {
            val tile = bankTiles.firstOrNull { it.first.contains(mx, my) }
            if (tile != null) {
                clickBankEntry(entries[tile.second], button)
            } else if (!carried.isEmpty && (button == 0 || button == 1)) {
                depositCursor()
            }
            return true
        }
        if (!carried.isEmpty && (button == 0 || button == 1)) {
            val inBankPanel = mx >= f.bankX - 4 && mx < f.bankX + f.bankW + 4 && my >= f.pt && my < f.pt + f.panelH
            val inInvPanel = mx >= f.invX - 4 && mx < f.invX + f.invW + 4 && my >= f.pt && my < f.pt + f.invPanelH
            when {
                inBankPanel -> depositCursor()
                inInvPanel -> {
                    val slot = (INVENTORY_START + ACCESSORY_COUNT until INVENTORY_START + 36).firstOrNull { depositable(it) && stackAt(it).isEmpty }
                    if (slot != null) send(slot, 0, ContainerInput.PICKUP) else warn("Inventory is full")
                }
                else -> send(OUTSIDE_SLOT, button, ContainerInput.PICKUP)
            }
        }
        return true
    }

    private fun depositCursor() {
        val target = freeBankSlot(storageName())
        if (target == null) {
            warn("The bank is full")
            return
        }
        if (target.first == currentPage()) {
            send(target.second, 0, ContainerInput.PICKUP)
        } else {
            BankSession.act(BankSession.Pending(target.first, target.second, 0, ContainerInput.PICKUP, false))
        }
    }

    private fun clickInventory(slot: Int, button: Int) {
        val stack = stackAt(slot)
        if (!menu.carried.isEmpty) {
            if (button == 0 || button == 1) send(slot, button, ContainerInput.PICKUP)
            return
        }
        when (button) {
            0 -> if (!stack.isEmpty) {
                val target = freeBankSlot(storageName())
                when {
                    target == null -> warn("The bank is full")
                    target.first == currentPage() -> send(slot, 0, ContainerInput.QUICK_MOVE)
                    else -> BankSession.act(BankSession.Pending(target.first, slot, 0, ContainerInput.QUICK_MOVE, true))
                }
            }
            1 -> if (!stack.isEmpty) send(slot, 0, ContainerInput.PICKUP)
            2 -> if (!stack.isEmpty) WynnOverhaulItemDebug.tryCopyToClipboard(stack)
        }
    }

    private fun clickBankEntry(entry: Entry, button: Int) {
        if (!menu.carried.isEmpty) {
            if (button == 0 || button == 1) depositCursor()
            return
        }
        val kind = when (button) {
            0 -> ContainerInput.QUICK_MOVE
            1 -> ContainerInput.PICKUP
            2 -> {
                WynnOverhaulItemDebug.tryCopyToClipboard(entry.stack)
                return
            }
            else -> return
        }
        if (entry.page == currentPage()) {
            if (!stackAt(entry.slot).isEmpty) send(entry.slot, 0, kind)
        } else {
            BankSession.act(BankSession.Pending(entry.page, entry.slot, 0, kind, true))
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (OwDropdownOverlay.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY, height)) return true
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        val f = frame ?: return true
        if (mouseInBank(f, mouseX.toInt(), mouseY.toInt())) bankScroll = (bankScroll - (scrollY * TILE_STEP).toInt()).coerceAtLeast(0)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (OwDropdownOverlay.isOpen && event.key() == KEY_ESCAPE) {
            OwDropdownOverlay.close()
            return true
        }
        if (!isTextInputFocused() && Minecraft.getInstance().options.keyInventory.matches(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    private fun send(slot: Int, button: Int, kind: ContainerInput) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (player.containerMenu !== menu) return
        try {
            client.gameMode?.handleContainerInput(menu.containerId, slot, button, kind, player)
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("WynnOverhaul bank input failed", t)
        }
    }

    override fun onClose() {
        BankSession.cancel()
        try {
            Minecraft.getInstance().player?.closeContainer()
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.warn("WynnOverhaul bank close threw", t)
        }
        Minecraft.getInstance().gui.setScreen(null)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private const val KEY_ESCAPE = 256
        private const val BANK_SLOTS = 45
        private const val QUICK_SLOT = 46
        private const val STORAGE_SLOT = 47
        private const val PREV_SLOT = 51
        private const val NEXT_SLOT = 52
        private const val INVENTORY_START = 54
        private const val ACCESSORY_COUNT = 4
        private const val ARMOR_FIRST_SLOT = 5
        private const val OFFHAND_MENU_SLOT = 45
        private const val EMERALD_POUCH_OFFSET = 5
        private const val ACTION_H = 16
        private const val BANK_COLS = 9
        private const val INV_COLS = 7
        private const val STABLE_TICKS_REQUIRED = 2
        private const val RESCAN_W = 44
        private const val TOP_ROW_Y = 7
        private const val LEFT_TOP = TOP_ROW_Y
        private const val SEARCH_ROW_Y = TOP_ROW_Y + SEARCH_H + 4
        private const val ACTION_ROW_Y = SEARCH_ROW_Y + SEARCH_H + 4
        private const val STORAGE_DROPDOWN_W = 96
        private val STORAGES = listOf("Character", "Account")
        private const val RIGHT_TOP = ACTION_ROW_Y + ACTION_H + 6
        private const val IDEAL_BANK_ROWS = 8
        private const val MAX_BANK_ROWS = 9
        private const val MIN_BANK_H = TILE_STEP * 2
        private const val MAX_ATTEMPTS = 4
        private const val WARN_NANOS = 2_500_000_000L
        private val PAGE = Regex("""Page\s+(\d+)""")

        fun looksLikeBank(menu: AbstractContainerMenu): Boolean {
            if (menu.slots.size != 90) return false
            val name = menu.slots.getOrNull(QUICK_SLOT)?.item?.get(DataComponents.CUSTOM_NAME)?.string ?: return false
            return TextClean.clean(name).contains("Quick Actions")
        }
    }
}
