package opal.dev.wynnoverhaul.client

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FirstPersonBody {
    const val ARM_DROP = 0.3f

    fun enabled(): Boolean =
        WynnOverhaulConfig.current.firstPersonBodyEnabled && WynnOverhaulGate.inGame

    fun firstPerson(): Boolean =
        Minecraft.getInstance().options.cameraType.isFirstPerson()

    private fun poseKey(player: LocalPlayer): String = when {
        player.isSleeping -> "sleeping"
        player.isFallFlying -> "gliding"
        player.isAutoSpinAttack -> "spinAttack"
        player.isVisuallySwimming -> if (player.isInWater) "swimming" else "crawling"
        player.isPassenger -> "riding"
        player.isCrouching -> "crouching"
        else -> "standing"
    }

    private fun showingBody(): Boolean {
        if (!enabled() || !firstPerson()) return false
        val player = Minecraft.getInstance().player ?: return false
        return poseKey(player) !in AnimationLibrary.tables().firstPerson.hidden
    }

    private val bodyStates: MutableSet<EntityRenderState> = Collections.newSetFromMap(IdentityHashMap())
    private var selfPass = false

    fun beginExtraction() {
        bodyStates.clear()
        selfPass = false
    }

    fun renderSelf(camera: Camera): Boolean {
        val render = showingBody() && camera.entity() is LocalPlayer
        if (render) selfPass = true
        return render
    }

    fun noteExtracted(entity: Entity, state: EntityRenderState?) {
        if (selfPass && state != null && entity is LocalPlayer) bodyStates.add(state)
    }

    fun handsHidden(): Boolean = showingBody()

    fun applyHeadVisibility(model: HumanoidModel<*>, state: AvatarRenderState) {
        val hide = state in bodyStates
        model.head.visible = !hide
        model.hat.visible = !hide
    }

    fun hidesHeadGear(state: EntityRenderState): Boolean = state in bodyStates

    fun submitShiftX(state: EntityRenderState): Double {
        val shift = shiftAmount(state) ?: return 0.0
        return shift * sin(shiftYaw(state))
    }

    fun submitShiftZ(state: EntityRenderState): Double {
        val shift = shiftAmount(state) ?: return 0.0
        return -shift * cos(shiftYaw(state))
    }

    private fun shiftYaw(state: EntityRenderState): Double =
        (state as AvatarRenderState).bodyRot * PI / 180.0

    fun relaxArms(state: AvatarRenderState): Boolean = state in bodyStates

    private fun shiftAmount(state: EntityRenderState): Double? {
        if (state !in bodyStates) return null
        val player = Minecraft.getInstance().player ?: return null
        val shift = AnimationLibrary.tables().firstPerson.shift
        return (shift[poseKey(player)] ?: shift["standing"] ?: FirstPersonTables.DEFAULT_SHIFT).toDouble()
    }
}
