package opal.dev.wynnoverhaul

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WynnOverhaul : ModInitializer {
    override fun onInitialize() {
    }

    companion object {
        const val MOD_ID = "wynnoverhaul"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
