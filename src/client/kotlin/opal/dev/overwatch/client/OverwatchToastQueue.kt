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

    enum class Kind(val label: String, val defaultColor: Int) {
        QUEST("Quest", 0xFF55FF55.toInt()),
        LEVEL_UP("Level Up", 0xFFFFD700.toInt()),
        DISCOVERY("Discovery", 0xFFFFAA00.toInt()),
        LOCATION("Location", 0xFFE6D3A0.toInt()),
    }

    data class Toast(
        val title: String,
        val subtitle: String,
        val colorArgb: Int,
        val style: ToastStyle = ToastStyle.CLASSIC,
        val detail: String = "",
        val kind: Kind = Kind.QUEST,
    )

    private class Active(val toast: Toast, val shownAtMillis: Long, val durationMillis: Long)

    private val pending = ArrayDeque<Toast>()

    @Volatile
    private var active: Active? = null

    fun make(kind: Kind, title: String, subtitle: String, colorArgb: Int, detail: String = ""): Toast =
        Toast(title, subtitle, colorArgb, styleFor(kind), detail, kind)

    fun styleName(kind: Kind): String {
        val config = OverwatchConfig.current
        return when (kind) {
            Kind.QUEST -> config.questToastStyle
            Kind.LEVEL_UP -> config.levelUpToastStyle
            Kind.DISCOVERY -> config.discoveryToastStyle
            Kind.LOCATION -> config.locationToastStyle
        }
    }

    fun styleFor(kind: Kind): ToastStyle = ToastStyle.parse(styleName(kind))

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
        val settings = OverwatchConfig.current.toast(next.kind)
        NotificationSounds.play(settings.sound, settings.soundVolume)
        return next
    }

    fun elapsedFraction(): Float {
        val current = active ?: return 0f
        val elapsed = System.currentTimeMillis() - current.shownAtMillis
        return (elapsed.toFloat() / current.durationMillis).coerceIn(0f, 1f)
    }

    fun preview() {
        Kind.entries.forEach { preview(it) }
    }

    fun preview(kind: Kind) {
        show(
            when (kind) {
                Kind.LOCATION -> make(kind, "Entering", "Paths of Sludge", kind.defaultColor)
                Kind.DISCOVERY -> make(kind, "Area Discovered", "The Eldritch Outlook", kind.defaultColor, "+300000 XP")
                Kind.QUEST -> make(kind, "Quest Completed", "A Journey Further", kind.defaultColor)
                Kind.LEVEL_UP -> make(kind, "Level Up!", "You reached combat level 100!", kind.defaultColor)
            },
        )
    }

    private fun durationFor(toast: Toast): Long =
        (OverwatchConfig.current.toast(toast.kind).durationSeconds.coerceIn(1.5, 20.0) * 1000).toLong()
}
