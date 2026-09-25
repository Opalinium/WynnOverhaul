package opal.dev.overwatch.client

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Optional

object ActivityItemParser {
    private val TITLE_PATTERN = Regex("""^(.+) \[(.+)]$""")
    private val LEGACY_CODE = Regex("§.")
    private val SPACER_RUN = Regex("À+")
    private val CP1252: Charset = Charset.forName("windows-1252")

    private fun fixMojibake(text: String): String {
        return try {
            val encoder = CP1252.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val bytes = encoder.encode(CharBuffer.wrap(text))
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(bytes).toString()
        } catch (t: Exception) {
            text
        }
    }

    private fun cleanName(raw: String): String =
        fixMojibake(raw).replace(LEGACY_CODE, "").replace(SPACER_RUN, " ").trim()

    fun parse(stack: ItemStack): ActivityInfo? {
        if (stack.isEmpty || stack.item != Items.POTION) return null

        val nameComponent = stack.hoverName
        val titleMatch = TITLE_PATTERN.matchEntire(cleanName(nameComponent.string)) ?: return null
        val activityName = titleMatch.groupValues[1].trim()
        val typeText = titleMatch.groupValues[2].trim()
        val type = ActivityType.from(firstColor(nameComponent), typeText) ?: return null

        val loreLines = stack.get(DataComponents.LORE)?.lines() ?: return null
        if (loreLines.isEmpty()) return null
        val lines = ArrayDeque(loreLines)

        val statusLineComponent = lines.removeFirstOrNull() ?: return null
        val statusLineText = statusLineComponent.string

        var specialInfo: String?
        val statusMessage: String

        if (type == ActivityType.WORLD_EVENT) {
            specialInfo = statusLineText.trim().ifBlank { null }
            statusMessage = lines.removeFirstOrNull()?.string ?: return null
        } else {
            val parts = statusLineText.split(" - ", limit = 2)
            if (parts.size == 1) {
                specialInfo = null
                statusMessage = parts[0]
            } else {
                specialInfo = parts[0].trim()
                statusMessage = parts[1]
            }
        }

        val status = statusOf(statusMessage) ?: return null

        lines.removeFirstOrNull()

        var length: ActivityLength? = null
        var distance: ActivityDistance? = null
        var difficulty: ActivityDifficulty? = null
        var levelReq = 0
        var levelReqFulfilled = true
        val professionReqs = ArrayList<ProfessionRequirement>()
        val questReqs = ArrayList<QuestRequirement>()
        val rewards = LinkedHashMap<ActivityRewardType, MutableList<String>>()
        val descriptionLines = ArrayList<String>()
        var trackingState = ActivityTrackingState.UNTRACKABLE
        var previousRewardType: ActivityRewardType? = null
        var inRewardsSection = false

        for (line in lines) {
            val text = line.string.trim()
            if (text.isEmpty()) {
                previousRewardType = null
                continue
            }

            val levelReqMatch = LEVEL_REQ_TEXT.find(text)
            if (levelReqMatch != null) {
                levelReq = levelReqMatch.groupValues[1].toIntOrNull() ?: 0
                levelReqFulfilled = isFulfilled(line)
                continue
            }

            val professionMatch = PROFESSION_REQ_TEXT.find(text)
            if (professionMatch != null) {
                professionReqs.add(
                    ProfessionRequirement(professionMatch.groupValues[1], professionMatch.groupValues[2].toIntOrNull() ?: 0, isFulfilled(line)),
                )
                continue
            }

            val questMatch = QUEST_REQ_TEXT.find(text)
            if (questMatch != null) {
                questReqs.add(QuestRequirement(questMatch.groupValues[1].trim(), isFulfilled(line)))
                continue
            }

            if (text.contains("Distance:")) {
                distance = distanceOf(text.substringAfter("Distance:").trim())
                continue
            }
            if (text.contains("Length:")) {
                length = lengthOf(text.substringAfter("Length:").trim())
                continue
            }
            if (text.contains("Difficulty:")) {
                difficulty = difficultyOf(text.substringAfter("Difficulty:").trim())
                continue
            }
            if (text.contains("Rewards:") && text.length < 20) {
                inRewardsSection = true
                continue
            }
            if (inRewardsSection && (text.contains("- ") || text.startsWith("+"))) {
                val rewardText = text.substringAfter("- ", text).removePrefix("+").trim()
                val rewardType = rewardTypeOf(rewardText)
                rewards.getOrPut(rewardType) { ArrayList() }.add(rewardText)
                previousRewardType = rewardType
                continue
            }
            if (text.contains("Click", ignoreCase = true) && text.contains("Untrack", ignoreCase = true)) {
                trackingState = ActivityTrackingState.TRACKED
                continue
            }
            if (text.contains("Click", ignoreCase = true) && text.contains("Track", ignoreCase = true)) {
                trackingState = ActivityTrackingState.TRACKABLE
                continue
            }
            val prev = previousRewardType
            if (prev != null) {
                val list = rewards[prev]
                if (!list.isNullOrEmpty()) {
                    list[list.size - 1] = "${list.last()} $text"
                    continue
                }
            }

            descriptionLines.add(text)
        }

        return ActivityInfo(
            icon = stack.copy(),
            type = type,
            name = activityName,
            status = status,
            specialInfo = specialInfo,
            description = descriptionLines.joinToString(" ").ifBlank { null },
            length = length,
            distance = distance,
            difficulty = difficulty,
            levelReq = levelReq,
            levelReqFulfilled = levelReqFulfilled,
            professionReqs = professionReqs,
            questReqs = questReqs,
            rewards = rewards,
            trackingState = trackingState,
        )
    }

    private fun statusOf(text: String): ActivityStatus? = when {
        text.contains("Currently in progress") || text.contains("Currently tracking") || text.contains("Event has started") -> ActivityStatus.STARTED
        text.contains("Can be") || text.contains("Event starting in") -> ActivityStatus.AVAILABLE
        text.contains("Cannot be") || text.contains("Event is not active") || text.contains("do not meet the requirements") -> ActivityStatus.UNAVAILABLE
        text.contains("Already completed") -> ActivityStatus.COMPLETED
        else -> null
    }

    private fun lengthOf(text: String): ActivityLength? = when {
        text.startsWith("Short") -> ActivityLength.SHORT
        text.startsWith("Medium") -> ActivityLength.MEDIUM
        text.startsWith("Long") -> ActivityLength.LONG
        else -> null
    }

    private fun distanceOf(text: String): ActivityDistance? = when {
        text.startsWith("Very Near") -> ActivityDistance.VERY_NEAR
        text.startsWith("Near") -> ActivityDistance.NEAR
        text.startsWith("Medium") -> ActivityDistance.MEDIUM
        text.startsWith("Far") -> ActivityDistance.FAR
        else -> null
    }

    private fun difficultyOf(text: String): ActivityDifficulty? = when {
        text.startsWith("Easy") -> ActivityDifficulty.EASY
        text.startsWith("Medium") -> ActivityDifficulty.MEDIUM
        text.startsWith("Hard") -> ActivityDifficulty.HARD
        else -> null
    }

    private fun rewardTypeOf(text: String): ActivityRewardType = when {
        Regex("""\d+(?: .+)? XP$""").containsMatchIn(text) -> ActivityRewardType.XP
        text.endsWith("Emeralds") -> ActivityRewardType.EMERALDS
        text.startsWith("Access to ") -> ActivityRewardType.ACCESS
        else -> ActivityRewardType.ITEM
    }

    private fun isFulfilled(component: Component): Boolean = firstColor(component) == 0x55FF55

    private fun firstColor(component: Component): Int? {
        var result: Int? = null
        var seen = false
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                if (!seen && string.isNotEmpty()) {
                    seen = true
                    result = style.color?.value
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private val LEVEL_REQ_TEXT = Regex("""Combat Lv\.?\s*Min:?\s*(\d+)""")
    private val PROFESSION_REQ_TEXT = Regex("""(\w+)\s+Lv\.\s*Min:\s*(\d+)""")
    private val QUEST_REQ_TEXT = Regex("""Quest:\s*(.+)""")
}
