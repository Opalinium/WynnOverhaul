package opal.dev.overwatch.client

import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class OverwatchQuestWikiScreen(
    private val activityType: ActivityType,
    private val activityName: String,
    parent: Screen,
) : OwScreen(Component.literal(activityName), parent) {

    private val page: QuestWikiFetcher.WikiPage? = QuestWikiFetcher.find(activityType, activityName)
    private val revealedSpoilers = HashSet<Int>()
    private val collapsedSections = HashSet<Int>()
    private var collapsedInitialized = false

    override fun init() {
        super.init()
        if (!collapsedInitialized) {
            collapsedInitialized = true
            initializeCollapsedSections()
        }
        rebuildLines()
    }

    private fun initializeCollapsedSections() {
        if (!activityType.isQuest) return
        val sections = page?.sections ?: return
        val expandIndex = currentStageSectionIndex()
        for (index in sections.indices) {
            if (index != expandIndex) collapsedSections.add(index)
        }
    }

    private fun currentStageSectionIndex(): Int? {
        val tracked = WynnScoreboardTracker.current
        if (tracked == null) return null
        if (!tracked.name.equals(activityName, ignoreCase = true)) return null
        val stage = QuestWaypoints.findCurrentStage(activityName, tracked.nextTask) ?: return null
        val title = "Stage ${stage.stage}"
        val index = page?.sections?.indexOfFirst { it.title == title } ?: -1
        if (index < 0) return null
        return index
    }

    private fun rebuildLines() {
        val left = contentLeft
        val w = contentWidth
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun textLine(text: String, color: Int) {
            rows += OwLabel(left, 0, w, LINE_HEIGHT, text, color) to LINE_HEIGHT
        }

        fun clickableLine(text: String, color: Int? = null, onClick: () -> Unit) {
            rows += OwButton(left, 0, w, LINE_HEIGHT, Component.literal(text), textColor = color) { onClick() } to LINE_HEIGHT
        }

        val p = page
        if (activityName == "???") {
            textLine("This secret hasn't been discovered yet.", GRAY)
            textLine("Wynncraft doesn't reveal its identity until you find it in the world.", GRAY)
        } else if (p == null) {
            textLine("No wiki page found for \"$activityName\".", GRAY)
        } else {
            var spoilerId = 0
            fun emit(line: QuestWikiFetcher.WikiLine) {
                when {
                    line.spoiler -> {
                        val id = spoilerId++
                        if (id in revealedSpoilers) {
                            clickableLine("[Spoiler] ${line.title} - click to hide", SPOILER_LABEL) { toggleSpoiler(id) }
                            for (wrapped in wrap(line.text, w)) textLine(wrapped, SPOILER_TEXT)
                        } else {
                            clickableLine("[Spoiler] ${line.title} - click to reveal", SPOILER_LABEL) { toggleSpoiler(id) }
                        }
                    }
                    line.objective -> for (wrapped in wrap(line.text, w)) textLine(wrapped, WHITE)
                    else -> for (wrapped in wrap(line.text, w)) textLine(wrapped, if (line.italic) DIALOGUE else GRAY)
                }
            }

            for (line in p.intro) emit(line)
            if (p.intro.isNotEmpty()) textLine("", GRAY)
            if (p.sections.isEmpty() && p.intro.isEmpty()) {
                textLine("No notable wiki content found for this activity.", GRAY)
            }
            for ((index, section) in p.sections.withIndex()) {
                val collapsed = index in collapsedSections
                val arrow = if (collapsed) "▶" else "▼"
                clickableLine("$arrow ${section.title}", GOLD) { toggleSection(index) }
                if (!collapsed) {
                    for (line in section.lines) emit(line)
                    textLine("", GRAY)
                }
            }
            textLine("Reference data pulled from wynncraft.wiki.gg -- may drift from the live game.", DARK_GRAY)
        }

        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    private fun toggleSpoiler(id: Int) {
        if (!revealedSpoilers.add(id)) revealedSpoilers.remove(id)
        rebuildWidgets()
    }

    private fun toggleSection(index: Int) {
        if (!collapsedSections.add(index)) collapsedSections.remove(index)
        rebuildWidgets()
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

    private companion object {
        const val LINE_HEIGHT = 12
        val WHITE = OwTheme.TEXT
        val GRAY = OwTheme.TEXT_DIM
        val DARK_GRAY = OwTheme.TEXT_FAINT
        val DIALOGUE = 0xFF9BAF7A.toInt()
        val SPOILER_TEXT = 0xFFD9B38C.toInt()
        val SPOILER_LABEL = OwTheme.BAD
        val GOLD = OwTheme.ACCENT
    }
}
