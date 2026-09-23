package opal.dev.overwatch.client

import java.util.ArrayDeque

enum class ToastStyle(val label: String) {
    CLASSIC("Classic"),
    SOULS("Souls");

    fun next(): ToastStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(raw: String?): ToastStyle = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLASSIC
    }
}

object OverwatchToastQueue {

    enum class Kind(val defaultColor: Int) {
        QUEST(0xFF55FF55.toInt()),
        LEVEL_UP(0xFFFFD700.toInt()),
        DISCOVERY(0xFFFFAA00.toInt()),
        LOCATION(0xFFE6D3A0.toInt()),
    }

    data class Toast(
        val title: String,
        val subtitle: String,
        val colorArgb: Int,
        val style: ToastStyle = ToastStyle.CLASSIC,
        val detail: String = "",
    )

    private class Active(val toast: Toast, val shownAtMillis: Long, val durationMillis: Long)

    private val pending = ArrayDeque<Toast>()

    @Volatile
    private var active: Active? = null

    fun make(kind: Kind, title: String, subtitle: String, colorArgb: Int, detail: String = ""): Toast =
        Toast(title, subtitle, colorArgb, styleFor(kind), detail)

    fun styleFor(kind: Kind): ToastStyle {
        val config = OverwatchConfig.current
        return ToastStyle.parse(
            when (kind) {
                Kind.QUEST -> config.questToastStyle
                Kind.LEVEL_UP -> config.levelUpToastStyle
                Kind.DISCOVERY -> config.discoveryToastStyle
                Kind.LOCATION -> config.locationToastStyle
            },
        )
    }

    fun show(toast: Toast) {
        pending.add(toast)
    }

    fun dropPending(predicate: (Toast) -> Boolean) {
        pending.removeIf(predicate)
    }

    fun clear() {
        pending.clear()
        active = null
    }

    fun current(): Toast? {
        val now = System.currentTimeMillis()
        val current = active
        if (current != null) {
            if (now - current.shownAtMillis < current.durationMillis) return current.toast
            active = null
        }
        val next = pending.poll() ?: return null
        active = Active(next, now, durationFor(next))
        return next
    }

    fun elapsedFraction(): Float {
        val current = active ?: return 0f
        val elapsed = System.currentTimeMillis() - current.shownAtMillis
        return (elapsed.toFloat() / current.durationMillis).coerceIn(0f, 1f)
    }

    fun preview() {
        show(make(Kind.LOCATION, "Entering", "Paths of Sludge", Kind.LOCATION.defaultColor))
        show(make(Kind.DISCOVERY, "Area Discovered", "The Eldritch Outlook", Kind.DISCOVERY.defaultColor, "+300000 XP"))
        show(make(Kind.QUEST, "Quest Completed", "A Journey Further", Kind.QUEST.defaultColor))
        show(make(Kind.LEVEL_UP, "Level Up!", "You reached combat level 100!", Kind.LEVEL_UP.defaultColor))
    }

    private fun durationFor(toast: Toast): Long {
        val base = (OverwatchConfig.current.toastDurationSeconds.coerceIn(1.5, 12.0) * 1000).toLong()
        return if (toast.style == ToastStyle.SOULS) (base * SOULS_DURATION_FACTOR).toLong() else base
    }

    private const val SOULS_DURATION_FACTOR = 1.5
}
