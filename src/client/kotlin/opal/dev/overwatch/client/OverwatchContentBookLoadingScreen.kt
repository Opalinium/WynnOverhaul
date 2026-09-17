package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class OverwatchContentBookLoadingScreen : OwScreen(Component.literal("Content Book"), null) {

    override val panelWidth: Int get() = 200
    override val panelHeight: Int get() = 60

    override fun init() {
        super.init()
        val text = "Reading Content Book..."
        val textWidth = font.width(text)
        addRenderableWidget(OwLabel(contentLeft + (contentWidth - textWidth) / 2, contentTop, textWidth, contentBottom - contentTop, text))
    }

    override fun onClose() {
        ContentBookQuery.cancel()
        Minecraft.getInstance().player?.closeContainer()
        super.onClose()
    }
}
