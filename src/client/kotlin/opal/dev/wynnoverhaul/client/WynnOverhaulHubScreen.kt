package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class WynnOverhaulHubScreen(parent: Screen? = null) : OwScreen(Component.literal("WynnOverhaul"), parent),
    WynnOverhaulSettingsPanels.Host {
    private val panels = WynnOverhaulSettingsPanels(this)

    override val panelWidth: Int get() = (width * 0.75).toInt().coerceIn(400, 560)
    override val panelLeft: Int get() = if (panels.animationsActive) (width - panelWidth - 12).coerceAtLeast(0) else (width - panelWidth) / 2
    override val panelHeight: Int get() = (height * 0.85).toInt().coerceIn(320, 520)

    override fun init() {
        super.init()
        panels.buildTabs(contentLeft, contentWidth, contentTop)
    }

    override fun tick() {
        super.tick()
        panels.tick()
    }

    override fun onClose() {
        panels.save()
        super.onClose()
    }

    override val screen: Screen get() = this
    override val panelFont: Font get() = font
    override fun rebuildPanels() = rebuildWidgets()
    override fun installPanelRows(rows: List<Pair<AbstractWidget, Int>>, x: Int, y: Int, w: Int, h: Int) {
        installScrollList(rows, x, y, w, h)
    }
    override fun addPanelWidget(widget: AbstractWidget) {
        addRenderableWidget(widget)
    }
    override fun panelContentBottom(): Int = contentBottom
}
