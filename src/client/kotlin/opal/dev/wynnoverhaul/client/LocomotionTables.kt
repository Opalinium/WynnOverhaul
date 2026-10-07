package opal.dev.wynnoverhaul.client

internal object LocoSignals {
    val NAMES = listOf(
        "k", "wAir", "wRise", "wFall", "wLand", "wMove", "wSprint", "wClimb", "climbMove", "wSwim", "wTread", "wCrawl",
        "wRide", "wFly", "wUse", "wBlock", "wHurt", "wDead", "wCrouch", "pace", "sp", "boost", "vanillaSp", "phase",
        "gait", "gaitOpp", "age", "grounded", "turnRate", "slopePitch", "launchAge", "leadRight", "launch",
    )
    const val K = 0
    const val W_AIR = 1
    const val W_RISE = 2
    const val W_FALL = 3
    const val W_LAND = 4
    const val W_MOVE = 5
    const val W_SPRINT = 6
    const val W_CLIMB = 7
    const val CLIMB_MOVE = 8
    const val W_SWIM = 9
    const val W_TREAD = 10
    const val W_CRAWL = 11
    const val W_RIDE = 12
    const val W_FLY = 13
    const val W_USE = 14
    const val W_BLOCK = 15
    const val W_HURT = 16
    const val W_DEAD = 17
    const val W_CROUCH = 18
    const val PACE = 19
    const val SP = 20
    const val BOOST = 21
    const val VANILLA_SP = 22
    const val PHASE = 23
    const val GAIT = 24
    const val GAIT_OPP = 25
    const val AGE = 26
    const val GROUNDED = 27
    const val TURN_RATE = 28
    const val SLOPE_PITCH = 29
    const val LAUNCH_AGE = 30
    const val LEAD_RIGHT = 31
    const val LAUNCH = 32
    val COUNT = NAMES.size
}

internal object LocoTargets {
    val PARTS = listOf("head", "rightArm", "leftArm", "rightLeg", "leftLeg", "torso")
    val PART_AXES = listOf("x", "y", "z", "posY")
    val TORSO_AXES = listOf("pitch", "roll", "yaw", "shift")
    const val TORSO = 5
    const val HEAD = 0
    const val RIGHT_ARM = 1
    const val LEFT_ARM = 2
    const val RIGHT_LEG = 3
    const val LEFT_LEG = 4

    fun code(target: String): Int {
        val part = target.substringBefore('.')
        val axis = target.substringAfter('.', "")
        val p = PARTS.indexOf(part)
        if (p < 0) return -1
        val a = (if (p == TORSO) TORSO_AXES else PART_AXES).indexOf(axis)
        return if (a < 0) -1 else p * 4 + a
    }
}

internal class StyleDef(val label: String, val values: FloatArray)

internal class SwingStyle(val speed: Float, val body: Float, val arc: Float, val blend: Float) {
    companion object {
        val NEUTRAL = SwingStyle(1f, 1f, 1f, 1f)
        val PARAMS = listOf("swingSpeed", "swingBody", "swingArc", "swingBlend")
    }
}

internal class LocoEffect(
    val target: Int,
    val value: AnimExpr,
    val rate: AnimExpr?,
    val approach: Boolean,
    val freeArmsOnly: Boolean,
)

internal class LocoState(
    val name: String,
    val toggle: String?,
    val gate: Int,
    val localSlots: IntArray,
    val locals: Array<AnimExpr>,
    val effects: List<LocoEffect>,
)

internal class LocomotionTables(
    val styleParams: List<String>,
    val styles: LinkedHashMap<String, StyleDef>,
    val states: List<LocoState>,
    val envSize: Int,
) {
    fun styleSlot(param: String): Int = LocoSignals.COUNT + styleParams.indexOf(param)

    private val swingStyles: Map<String, SwingStyle> = styles.mapValues { (_, def) ->
        fun value(name: String): Float {
            val i = styleParams.indexOf(name)
            return if (i < 0) 1f else def.values[i].coerceIn(0.4f, 2f)
        }
        SwingStyle(value("swingSpeed"), value("swingBody"), value("swingArc"), value("swingBlend"))
    }

    fun swingFor(style: String): SwingStyle = swingStyles[style] ?: SwingStyle.NEUTRAL

    companion object {
        val EMPTY = LocomotionTables(emptyList(), LinkedHashMap(), emptyList(), LocoSignals.COUNT)
    }
}

internal object LocoToggles {
    val NAMES = setOf(
        "locomotionWalk", "locomotionJump", "locomotionCrouch", "locomotionSwim", "locomotionClimb",
        "locomotionRide", "locomotionElytra", "locomotionUseItem", "locomotionHurt", "locomotionDeath",
    )

    fun enabled(config: WynnOverhaulConfig, name: String): Boolean = when (name) {
        "locomotionWalk" -> config.locomotionWalk
        "locomotionJump" -> config.locomotionJump
        "locomotionCrouch" -> config.locomotionCrouch
        "locomotionSwim" -> config.locomotionSwim
        "locomotionClimb" -> config.locomotionClimb
        "locomotionRide" -> config.locomotionRide
        "locomotionElytra" -> config.locomotionElytra
        "locomotionUseItem" -> config.locomotionUseItem
        "locomotionHurt" -> config.locomotionHurt
        "locomotionDeath" -> config.locomotionDeath
        else -> true
    }
}
