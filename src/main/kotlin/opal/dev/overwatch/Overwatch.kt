package opal.dev.overwatch

import net.fabricmc.api.ModInitializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Overwatch : ModInitializer {

    override fun onInitialize() {
    }

    companion object {
        const val MOD_ID = "overwatch"
        val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
