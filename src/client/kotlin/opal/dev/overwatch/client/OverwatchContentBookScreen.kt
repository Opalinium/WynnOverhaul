package opal.dev.overwatch.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OverwatchContentBookScreen(initialActivities: List<ActivityInfo>) : Screen(Component.literal("Content Book")) {

    private var activities: List<ActivityInfo> = initialActivities
    private var query: String = ""
    private var page: Int = 0
    private var filter: String? = OverwatchConfig.current.contentBookFilter.ifEmpty { null }
    private var sort: Sort = runCatching { Sort.valueOf(OverwatchConfig.current.contentBookSort) }.getOrDefault(Sort.RECOMMENDED)
    private var actionMessage: String? = null

    private enum class Sort(val label: String) { RECOMMENDED("Recommended"), NAME("Name"), LEVEL("Level"), STATUS("Status") }

    private data class TabRect(val x: Int, val y: Int, val w: Int, val h: Int, val filterName: String?, val color: Int)
    private data class SlotRect(val x: Int, val y: Int, val activity: ActivityInfo)

    private val tabRects = ArrayList<TabRect>()
    private val slotRects = ArrayList<SlotRect>()
    private var panelBounds = intArrayOf(0, 0, 0, 0)
    private var statusWidget: OwLabel? = null

    fun updateActivities(list: List<ActivityInfo>) {
        activities = list
        rebuildWidgets()
    }

    fun currentActivities(): List<ActivityInfo> = activities

    private fun saveViewPrefs() {
        val config = OverwatchConfig.current
        config.contentBookSort = sort.name
        config.contentBookFilter = filter ?: ""
        config.save()
    }

    override fun init() {
        tabRects.clear()
        slotRects.clear()

        val gridWidth = COLS * SLOT_PITCH - SLOT_GAP
        val panelWidth = gridWidth + PANEL_PAD * 2
        val left = width / 2 - panelWidth / 2
        val top = height / 10
        var y = top + PANEL_PAD

        y += TITLE_HEIGHT

        val sortWidth = 90
        val searchBox = OwTextField(font, left + PANEL_PAD, y, gridWidth - sortWidth - 4, 16)
        searchBox.setMaxLength(64)
        searchBox.setValue(query)
        searchBox.setHint(Component.literal("Search..."))
        searchBox.setResponder { query = it; page = 0; rebuildWidgets() }
        addRenderableWidget(searchBox)
        setInitialFocus(searchBox)

        addRenderableWidget(
            OwButton(left + PANEL_PAD + gridWidth - sortWidth, y, sortWidth, 16, Component.literal(sort.label)) {
                sort = Sort.entries[(sort.ordinal + 1) % Sort.entries.size]
                saveViewPrefs()
                rebuildWidgets()
            },
        )
        y += 20

        var tx = left + PANEL_PAD
        var tabRowY = y
        fun addTab(label: String, color: Int, value: String?) {
            val w = (font.width(label) + 10).coerceAtLeast(28)
            if (tx + w > left + PANEL_PAD + gridWidth) {
                tx = left + PANEL_PAD
                tabRowY += TAB_HEIGHT + 2
            }
            tabRects.add(TabRect(tx, tabRowY, w, TAB_HEIGHT, value, color))
            tx += w + 2
        }
        addTab("All", 0xFFAAAAAA.toInt(), null)
        for (t in ActivityType.entries.distinctBy { it.filterName }.sortedBy { it.filterName }) {
            addTab(t.filterName, t.colorArgb, t.filterName)
        }
        y = tabRowY + TAB_HEIGHT + 6

        val results = filteredSorted()
        val perPage = COLS * ROWS
        val pageCount = maxOf(1, (results.size + perPage - 1) / perPage)
        page = page.coerceIn(0, pageCount - 1)
        val start = page * perPage
        val end = minOf(results.size, start + perPage)

        val gridTop = y
        for (i in start until end) {
            val idx = i - start
            val col = idx % COLS
            val row = idx / COLS
            slotRects.add(SlotRect(left + PANEL_PAD + col * SLOT_PITCH, gridTop + row * SLOT_PITCH, results[i]))
        }
        y = gridTop + ROWS * SLOT_PITCH - SLOT_GAP + 8

        val statusText = actionMessage ?: if (ContentBookQuery.isEnumerating) {
            "${activities.size} activities (refreshing...)"
        } else {
            "${results.size} of ${activities.size} activities"
        }
        statusWidget = addRenderableWidget(OwLabel(left + PANEL_PAD, y, gridWidth, 10, statusText, OwTheme.TEXT_DIM))
        y += 14

        if (results.size > perPage) {
            addRenderableWidget(OwButton(left + PANEL_PAD, y, 20, 16, Component.literal("<")) { page = (page - 1).mod(pageCount); rebuildWidgets() })
            addRenderableWidget(OwButton(left + PANEL_PAD + 22, y, 60, 16, Component.literal("${page + 1}/$pageCount")) { })
            addRenderableWidget(OwButton(left + PANEL_PAD + 84, y, 20, 16, Component.literal(">")) { page = (page + 1).mod(pageCount); rebuildWidgets() })
        }
        addRenderableWidget(
            OwButton(left + PANEL_PAD + gridWidth - 126, y, 60, 16, Component.literal("Refresh")) {
                if (!ContentBookQuery.isActive) ContentBookInterceptor.requestRefresh(Minecraft.getInstance(), this)
            },
        )
        addRenderableWidget(OwButton(left + PANEL_PAD + gridWidth - 60, y, 60, 16, Component.literal("Close")) { onClose() })
        y += 20

        panelBounds = intArrayOf(left, top, left + panelWidth, y + PANEL_PAD)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val b = panelBounds
        graphics.fill(b[0], b[1], b[2], b[3], PANEL_BG)
        graphics.fill(b[0], b[1], b[2], b[1] + 1, PANEL_BORDER)
        graphics.fill(b[0], b[3] - 1, b[2], b[3], PANEL_BORDER)
        graphics.fill(b[0], b[1], b[0] + 1, b[3], PANEL_BORDER)
        graphics.fill(b[2] - 1, b[1], b[2], b[3], PANEL_BORDER)

        val title = "Content Book"
        graphics.text(font, title, (b[0] + b[2]) / 2 - font.width(title) / 2, b[1] + PANEL_PAD - 2, TITLE_COLOR)

        for (tab in tabRects) {
            val active = tab.filterName == filter
            val bg = if (active) (tab.color and 0xFFFFFF) or 0xC0000000.toInt() else (tab.color and 0xFFFFFF) or 0x40000000
            graphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + tab.h, bg)
            if (active) graphics.fill(tab.x, tab.y + tab.h - 2, tab.x + tab.w, tab.y + tab.h, 0xFFFFFFFF.toInt())
            val label = tab.filterName ?: "All"
            val tw = font.width(label)
            graphics.text(font, label, tab.x + tab.w / 2 - tw / 2, tab.y + tab.h / 2 - 4, 0xFFFFFFFF.toInt())
        }

        var hovered: SlotRect? = null
        for (slot in slotRects) {
            val a = slot.activity
            val hover = mouseX >= slot.x && mouseX < slot.x + SLOT_SIZE && mouseY >= slot.y && mouseY < slot.y + SLOT_SIZE
            if (hover) hovered = slot
            val bg = if (hover) SLOT_BG_HOVER else SLOT_BG
            graphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, bg)
            val border = (a.type.colorArgb and 0xFFFFFF) or 0xFF000000.toInt()
            graphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + 1, border)
            graphics.fill(slot.x, slot.y + SLOT_SIZE - 1, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, border)
            graphics.fill(slot.x, slot.y, slot.x + 1, slot.y + SLOT_SIZE, border)
            graphics.fill(slot.x + SLOT_SIZE - 1, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, border)
            graphics.item(a.icon, slot.x + (SLOT_SIZE - 16) / 2, slot.y + (SLOT_SIZE - 16) / 2)
            graphics.fill(slot.x + 2, slot.y + SLOT_SIZE - 3, slot.x + SLOT_SIZE - 2, slot.y + SLOT_SIZE - 1, statusColorArgb(a.status))
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        hovered?.let { slot ->
            graphics.setComponentTooltipForNextFrame(font, buildTooltip(slot.activity), mouseX, mouseY)
        }
    }

    private fun buildTooltip(a: ActivityInfo): List<Component> {
        val lines = ArrayList<Component>()
        lines.add(Component.literal(a.name).withColor(a.type.colorArgb and 0xFFFFFF))
        lines.add(Component.literal("${a.type.filterName} - ${statusLabel(a.status)}").withStyle(statusColor(a.status)))
        a.specialInfo?.let { lines.add(Component.literal(it).withStyle(ChatFormatting.GRAY)) }
        a.description?.let { desc ->
            for (wrapped in wrap(desc, 200)) lines.add(Component.literal(wrapped).withStyle(ChatFormatting.GRAY))
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

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (super.mouseClicked(event, doubled)) return true
        if (event.button() == 1) {
            for (slot in slotRects) {
                if (event.x() >= slot.x && event.x() < slot.x + SLOT_SIZE && event.y() >= slot.y && event.y() < slot.y + SLOT_SIZE) {
                    Minecraft.getInstance().setScreenAndShow(OverwatchQuestWikiScreen(slot.activity.type, slot.activity.name, this))
                    return true
                }
            }
            return false
        }
        if (event.button() != 0) return false
        for (tab in tabRects) {
            if (event.x() >= tab.x && event.x() < tab.x + tab.w && event.y() >= tab.y && event.y() < tab.y + tab.h) {
                filter = tab.filterName
                page = 0
                actionMessage = null
                saveViewPrefs()
                rebuildWidgets()
                return true
            }
        }
        for (slot in slotRects) {
            if (event.x() >= slot.x && event.x() < slot.x + SLOT_SIZE && event.y() >= slot.y && event.y() < slot.y + SLOT_SIZE) {
                toggleTrack(slot.activity)
                return true
            }
        }
        return false
    }

    private fun toggleTrack(activity: ActivityInfo) {
        if (activity.trackingState == ActivityTrackingState.UNTRACKABLE) {
            showMessage("${activity.name} can't be tracked")
            return
        }
        val failure = ContentBookInterceptor.toggleTracking(Minecraft.getInstance(), this, activity)
        if (failure != null) showMessage(failure)
    }

    fun showMessage(message: String) {
        actionMessage = message
        rebuildWidgets()
    }

    fun applyTrackToggle(activity: ActivityInfo) {
        val patched = ContentBookCache.applyTrackToggle(activity.type, activity.name)
            ?: activities.map { info ->
                when {
                    info.name == activity.name && info.type == activity.type ->
                        info.copy(trackingState = if (info.trackingState == ActivityTrackingState.TRACKED) ActivityTrackingState.TRACKABLE else ActivityTrackingState.TRACKED)
                    info.trackingState == ActivityTrackingState.TRACKED -> info.copy(trackingState = ActivityTrackingState.TRACKABLE)
                    else -> info
                }
            }
        activities = patched
        actionMessage = null
        rebuildWidgets()
    }

    private fun filteredSorted(): List<ActivityInfo> {
        var list = activities.asSequence()
        val f = filter
        if (f != null) list = list.filter { it.type.filterName == f }
        val q = query.trim()
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        var result = list.toList()
        result = when (sort) {
            Sort.RECOMMENDED -> result
            Sort.NAME -> result.sortedBy { it.name }
            Sort.LEVEL -> result.sortedByDescending { it.levelReq }
            Sort.STATUS -> result.sortedBy { it.status.ordinal }
        }
        result = result.sortedByDescending { it.trackingState == ActivityTrackingState.TRACKED }
        return result
    }

    private fun statusLabel(status: ActivityStatus): String = when (status) {
        ActivityStatus.STARTED -> "In progress"
        ActivityStatus.AVAILABLE -> "Available"
        ActivityStatus.UNAVAILABLE -> "Locked"
        ActivityStatus.COMPLETED -> "Completed"
    }

    private fun statusColor(status: ActivityStatus): ChatFormatting = when (status) {
        ActivityStatus.STARTED -> ChatFormatting.YELLOW
        ActivityStatus.AVAILABLE -> ChatFormatting.AQUA
        ActivityStatus.UNAVAILABLE -> ChatFormatting.RED
        ActivityStatus.COMPLETED -> ChatFormatting.GREEN
    }

    private fun statusColorArgb(status: ActivityStatus): Int = when (status) {
        ActivityStatus.STARTED -> 0xFFFFD700.toInt()
        ActivityStatus.AVAILABLE -> 0xFF55FFFF.toInt()
        ActivityStatus.UNAVAILABLE -> 0xFFFF5555.toInt()
        ActivityStatus.COMPLETED -> 0xFF55FF55.toInt()
    }

    private fun wrap(text: String, maxWidth: Int): List<String> {
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

    override fun onClose() {
        ContentBookQuery.cancel()
        Minecraft.getInstance().player?.closeContainer()
        Minecraft.getInstance().gui.setScreen(null)
    }

    private companion object {
        const val COLS = 9
        const val ROWS = 5
        const val SLOT_SIZE = 24
        const val SLOT_GAP = 4
        const val SLOT_PITCH = SLOT_SIZE + SLOT_GAP
        const val TAB_HEIGHT = 16
        const val TITLE_HEIGHT = 14
        const val PANEL_PAD = 14
        val PANEL_BG = 0xE0121016.toInt()
        val PANEL_BORDER = 0xFF4A4658.toInt()
        val TITLE_COLOR = 0xFFE0C060.toInt()
        val SLOT_BG = 0xFF201E28.toInt()
        val SLOT_BG_HOVER = 0xFF302C3C.toInt()
    }
}
