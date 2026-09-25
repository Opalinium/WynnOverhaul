package opal.dev.wynnoverhaul.client

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel

object CameraMemory {
    private var trackedLevel: ClientLevel? = null
    private var restored = false

    fun tick(client: Minecraft) {
        val config = WynnOverhaulConfig.current
        val level = client.level
        if (level == null) {
            trackedLevel = null
            restored = false
            return
        }
        if (!config.rememberCameraMode) {
            trackedLevel = level
            restored = true
            return
        }
        if (level !== trackedLevel) {
            trackedLevel = level
            restored = false
        }
        val current = client.options.cameraType
        if (!restored) {
            val saved = runCatching { CameraType.valueOf(config.lastCameraMode) }.getOrDefault(CameraType.FIRST_PERSON)
            if (current != saved) client.options.setCameraType(saved)
            restored = true
            return
        }
        if (current.name != config.lastCameraMode) {
            config.lastCameraMode = current.name
            config.save()
        }
    }
}
