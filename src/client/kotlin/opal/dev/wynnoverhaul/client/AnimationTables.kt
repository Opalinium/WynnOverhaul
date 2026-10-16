package opal.dev.wynnoverhaul.client

import opal.dev.wynnoverhaul.client.WeaponAnimations.ComboStep
import opal.dev.wynnoverhaul.client.WeaponAnimations.Cue
import opal.dev.wynnoverhaul.client.WeaponAnimations.Pose
import opal.dev.wynnoverhaul.client.WeaponAnimations.Stance

internal class AnimationTables(
    val poses: Map<String, Pose>,
    val stances: Map<String, Stance>,
    val combos: Map<String, List<ComboStep>>,
    val movesetStances: Map<String, String>,
    val openers: Map<String, ComboStep>,
    val sfx: Map<String, List<Cue>>,
    val impact: Map<String, List<Cue>>,
    val finisher: Cue,
    val thump: Cue,
    val tail: Cue,
    val slam: Cue,
    val lungeFamilies: Set<String>,
    val handShift: Map<String, Float>,
    val handSprintExtra: Map<String, Float>,
    val trailSpans: Map<String, Pair<Float, Float>>,
    val labels: Map<String, String>,
    val spellKeys: Map<String, String>,
    val castStyles: Map<String, CastStyle>,
    val movesetSpells: Map<String, Map<String, String>>,
    val locomotion: LocomotionTables,
    val firstPerson: FirstPersonTables,
)

internal object AnimationChannels {
    val NAMES = listOf(
        "pitch", "across", "abduct", "offPitch", "offAcross", "offAbduct", "turn", "reach",
        "headA", "headY", "twist", "grip", "lean", "rootF", "footLF", "footRF", "bodyY", "bodyR", "hipY", "footLS", "footRS", "rootS", "rootH", "footLU", "footRU",
    )
    val ZERO_DEFAULT = setOf(6, 7, 10)
    fun indexOf(name: String): Int = NAMES.indexOf(name)
}

internal class FirstPersonTables(val hidden: Set<String>, val shift: Map<String, Float>) {
    companion object {
        val POSES = listOf("standing", "crouching", "riding", "swimming", "crawling", "gliding", "spinAttack", "sleeping")
        const val DEFAULT_SHIFT = 0.25f
    }
}
