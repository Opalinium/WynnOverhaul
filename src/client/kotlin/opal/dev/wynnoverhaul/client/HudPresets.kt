package opal.dev.wynnoverhaul.client

object HudPresets {
    class Preset(
        val id: String,
        val label: String,
        val barStyle: String,
        val hotbarStyle: String,
        val chatStyle: String,
        val panels: Boolean,
    ) {
        fun matches(config: WynnOverhaulConfig): Boolean =
            config.hudBarStyle == barStyle &&
                config.hotbarStyle == hotbarStyle &&
                config.chatStyle == chatStyle &&
                config.hudPanelsEnabled == panels &&
                !HudLayoutManager.hasStyleOverrides()
    }

    val PRESETS = listOf(
        Preset("DEFAULT", "Default", HudBars.STYLE_CLASSIC, HotbarStyles.CLASSIC, ChatHud.CLASSIC, false),
        Preset("GLASS", "Glass", HudBars.STYLE_CLASSIC, HotbarStyles.GLASS, ChatHud.GLASS, true),
        Preset("ELDEN", "Elden Ring", HudBars.STYLE_ELDEN, HotbarStyles.CROSS, ChatHud.FADE, false),
        Preset("MINIMAL", "Minimal", HudBars.STYLE_MINIMAL, HotbarStyles.TILES, ChatHud.MINIMAL, false),
        Preset("TACTICAL", "Tactical", HudBars.STYLE_CLASSIC, HotbarStyles.ARC, ChatHud.GLASS, true),
    )

    fun current(config: WynnOverhaulConfig): Preset? = PRESETS.firstOrNull { it.matches(config) }

    fun label(config: WynnOverhaulConfig): String = current(config)?.label ?: "Custom"

    fun apply(config: WynnOverhaulConfig, preset: Preset) {
        config.hudBarStyle = preset.barStyle
        config.hotbarStyle = preset.hotbarStyle
        config.chatStyle = preset.chatStyle
        config.hudPanelsEnabled = preset.panels
        HudLayoutManager.clearStyleOverrides()
        HudLayoutManager.forgetSize(HotbarHudElement.ID)
    }

}
