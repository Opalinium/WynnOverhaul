package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.PlayerModelPart

class WynnOverhaulOptionsScreen(parent: Screen?) : OwScreen(Component.literal("Options"), parent) {
    private enum class Tab(val label: String) {
        VIDEO("Video"),
        CONTROLS("Controls"),
        KEYS("Key Binds"),
        AUDIO("Audio"),
        CHAT("Chat"),
        ACCESS("Accessibility"),
        SKIN("Skin"),
        MORE("More"),
    }

    override val panelWidth: Int get() = (width * 0.82).toInt().coerceIn(480, 900).coerceAtMost((width - 20).coerceAtLeast(300))
    override val panelHeight: Int get() = (height * 0.86).toInt().coerceIn(300, 560)

    private val options = Minecraft.getInstance().options
    private var listening: KeyMapping? = null

    private val tabWidth: Int get() = 112
    private val bodyLeft: Int get() = contentLeft + tabWidth + OwTheme.PAD
    private val bodyWidth: Int get() = contentWidth - tabWidth - OwTheme.PAD

    override fun init() {
        super.init()
        var y = contentTop
        for (tab in Tab.entries) {
            addRenderableWidget(
                OwButton(contentLeft, y, tabWidth, OwTheme.ROW_H, Component.literal(tab.label), accent = tab == current) {
                    current = tab
                    listening = null
                    rebuildWidgets()
                },
            )
            y += OwTheme.ROW_H + 4
        }
        val rows = ArrayList<Pair<AbstractWidget, Int>>()
        if (current == Tab.VIDEO && sodiumLoaded && prepareEmbedded()) return
        when (current) {
            Tab.VIDEO -> video(rows)
            Tab.CONTROLS -> controls(rows)
            Tab.KEYS -> keys(rows)
            Tab.AUDIO -> audio(rows)
            Tab.CHAT -> chat(rows)
            Tab.ACCESS -> access(rows)
            Tab.SKIN -> skin(rows)
            Tab.MORE -> more(rows)
        }
        installScrollList(rows, bodyLeft, contentTop, bodyWidth, contentBottom - contentTop)
    }

    private var embedded: Screen? = null
    private var embeddedFailed = false

    private val embeddedHeight: Int get() = contentBottom - contentTop

    private fun createSodiumScreen(): Screen? = runCatching {
        Class.forName("me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen")
            .getConstructor(Screen::class.java)
            .newInstance(this) as Screen
    }.getOrNull() ?: runCatching {
        Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
            .getMethod("createScreen", Screen::class.java)
            .invoke(null, this) as Screen
    }.getOrNull()

    private fun prepareEmbedded(): Boolean {
        if (embeddedFailed) return false
        val screen = embedded ?: createSodiumScreen()
        if (screen == null || runCatching { screen.init(bodyWidth, embeddedHeight) }.isFailure) {
            embedded = null
            embeddedFailed = true
            return false
        }
        embedded = screen
        return true
    }

    private fun embeddedLive(): Screen? = if (current == Tab.VIDEO) embedded else null

    private fun shifted(event: MouseButtonEvent): MouseButtonEvent =
        MouseButtonEvent(event.x() - bodyLeft, event.y() - contentTop, event.buttonInfo())

    private fun overBody(x: Double, y: Double): Boolean =
        x >= bodyLeft && x < bodyLeft + bodyWidth && y >= contentTop && y < contentBottom

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val inner = embeddedLive() ?: return
        graphics.enableScissor(bodyLeft, contentTop, bodyLeft + bodyWidth, contentBottom)
        graphics.pose().pushMatrix()
        graphics.pose().translate(bodyLeft.toFloat(), contentTop.toFloat())
        inner.extractRenderState(graphics, mouseX - bodyLeft, mouseY - contentTop, partialTick)
        graphics.pose().popMatrix()
        graphics.disableScissor()
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val inner = embeddedLive() ?: return super.mouseReleased(event)
        return inner.mouseReleased(shifted(event)) || super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val inner = embeddedLive() ?: return super.mouseDragged(event, dx, dy)
        return inner.mouseDragged(shifted(event), dx, dy) || super.mouseDragged(event, dx, dy)
    }

    override fun mouseMoved(x: Double, y: Double) {
        embeddedLive()?.mouseMoved(x - bodyLeft, y - contentTop)
        super.mouseMoved(x, y)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val inner = embeddedLive()
        if (inner != null && overBody(mouseX, mouseY) && inner.mouseScrolled(mouseX - bodyLeft, mouseY - contentTop, scrollX, scrollY)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyReleased(event: KeyEvent): Boolean =
        embeddedLive()?.keyReleased(event) == true || super.keyReleased(event)

    override fun charTyped(event: CharacterEvent): Boolean =
        embeddedLive()?.charTyped(event) == true || super.charTyped(event)

    override fun tick() {
        super.tick()
        embeddedLive()?.tick()
    }

    private fun header(rows: MutableList<Pair<AbstractWidget, Int>>, text: String) {
        rows += OwLabel(bodyLeft, 0, bodyWidth, 12, text.uppercase(), OwTheme.ACCENT) to 20
    }

    private fun grid(rows: MutableList<Pair<AbstractWidget, Int>>, items: List<OptionInstance<*>>) {
        val half = (bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP * 2) / 2
        var i = 0
        while (i < items.size) {
            val left = items[i].createButton(options, bodyLeft, 0, half)
            val right = items.getOrNull(i + 1)?.createButton(options, bodyLeft + half + OwTheme.GAP, 0, half)
            if (right != null) {
                rows += left to 0
                rows += right to 24
            } else {
                rows += left to 24
            }
            i += 2
        }
    }

    private fun video(rows: MutableList<Pair<AbstractWidget, Int>>) {
        rows += OwButton(bodyLeft, 0, bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP, 20, Component.literal("Open the full video settings (Sodium and shader options)..."), accent = true) {
            OptionsInterceptor.openVanilla(this, "options.video")
        } to 28
        header(rows, "Display")
        grid(rows, listOf(options.graphicsPreset(), options.fullscreen(), options.enableVsync(), options.framerateLimit(), options.fov(), options.guiScale(), options.gamma(), options.inactivityFpsLimit()))
        header(rows, "World detail")
        grid(rows, listOf(options.renderDistance(), options.simulationDistance(), options.entityDistanceScaling(), options.cloudStatus(), options.cloudRange(), options.weatherRadius(), options.particles(), options.biomeBlendRadius(), options.ambientOcclusion(), options.entityShadows(), options.cutoutLeaves(), options.improvedTransparency()))
        header(rows, "Quality")
        grid(rows, listOf(options.mipmapLevels(), options.textureFiltering(), options.maxAnisotropyBit(), options.chunkSectionFadeInTime(), options.prioritizeChunkUpdates(), options.vignette()))
        header(rows, "Camera and effects")
        grid(rows, listOf(options.bobView(), options.attackIndicator(), options.screenEffectScale(), options.fovEffectScale(), options.darknessEffectScale(), options.damageTiltStrength(), options.glintSpeed(), options.glintStrength()))
        header(rows, "Menus")
        grid(rows, listOf(options.menuBackgroundBlurriness(), options.preferredGraphicsBackend()))
    }

    private fun controls(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Mouse")
        grid(rows, listOf(options.sensitivity(), options.mouseWheelSensitivity(), options.invertMouseX(), options.invertMouseY(), options.discreteMouseScroll(), options.rawMouseInput(), options.allowCursorChanges()))
        header(rows, "Movement and actions")
        grid(rows, listOf(options.toggleCrouch(), options.toggleSprint(), options.toggleAttack(), options.toggleUse(), options.sprintWindow(), options.autoJump(), options.rotateWithMinecart(), options.operatorItemsTab()))
    }

    private fun keys(rows: MutableList<Pair<AbstractWidget, Int>>) {
        val mappings = options.keyMappings.sortedWith(compareBy({ it.category.label().string }, { Component.translatable(it.name).string }))
        var category: String? = null
        val keyWidth = 130
        val resetWidth = 44
        val labelWidth = bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP * 3 - keyWidth - resetWidth
        for (mapping in mappings) {
            val cat = mapping.category.label().string
            if (cat != category) {
                category = cat
                header(rows, cat)
            }
            val conflict = options.keyMappings.any { it !== mapping && !mapping.isUnbound && mapping.same(it) }
            val label = if (listening === mapping) "> press a key <" else mapping.translatedKeyMessage.string
            rows += OwLabel(bodyLeft, 0, labelWidth, 18, Component.translatable(mapping.name).string) to 0
            rows += OwButton(
                bodyLeft + labelWidth + OwTheme.GAP, 0, keyWidth, 18, Component.literal(label),
                accent = listening === mapping, textColor = if (conflict) OwTheme.BAD else null,
            ) {
                listening = if (listening === mapping) null else mapping
                rebuildWidgets()
            } to 0
            rows += OwButton(bodyLeft + labelWidth + keyWidth + OwTheme.GAP * 2, 0, resetWidth, 18, Component.literal("Reset"), enabled = { !mapping.isDefault }) {
                mapping.setKey(mapping.defaultKey)
                KeyMapping.resetMapping()
                rebuildWidgets()
            } to 22
        }
    }

    private fun audio(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Volume")
        grid(rows, SoundSource.entries.map { options.getSoundSourceOptionInstance(it) })
        header(rows, "Sound")
        grid(rows, listOf(options.soundDevice(), options.showSubtitles(), options.directionalAudio(), options.musicFrequency(), options.musicToast()))
    }

    private fun chat(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Chat")
        grid(rows, listOf(options.chatVisibility(), options.chatColors(), options.chatLinks(), options.chatLinksPrompt(), options.autoSuggestions(), options.hideMatchedNames(), options.onlyShowSecureChat(), options.saveChatDrafts(), options.reducedDebugInfo()))
        header(rows, "Appearance")
        grid(rows, listOf(options.chatOpacity(), options.textBackgroundOpacity(), options.backgroundForChatOnly(), options.chatScale(), options.chatLineSpacing(), options.chatDelay(), options.chatWidth(), options.chatHeightUnfocused(), options.chatHeightFocused()))
    }

    private fun access(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Accessibility")
        grid(rows, listOf(options.narrator(), options.narratorHotkey(), options.highContrast(), options.highContrastBlockOutline(), options.hideLightningFlash(), options.notificationDisplayTime(), options.forceUnicodeFont(), options.japaneseGlyphVariants()))
        header(rows, "Menus")
        grid(rows, listOf(options.panoramaSpeed(), options.hideSplashTexts(), options.darkMojangStudiosBackground(), options.showAutosaveIndicator(), options.inGameNotification()))
    }

    private fun skin(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Visible model parts")
        val half = (bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP * 2) / 2
        val parts = PlayerModelPart.entries
        var i = 0
        while (i < parts.size) {
            for (k in 0..1) {
                val part = parts.getOrNull(i + k) ?: continue
                val on = options.isModelPartEnabled(part)
                val button = OwButton(bodyLeft + k * (half + OwTheme.GAP), 0, half, 20, Component.literal(part.getName().string + ": " + (if (on) "On" else "Off")), accent = on) {
                    options.setModelPart(part, !on)
                    rebuildWidgets()
                }
                rows += button to (if (k == 0 && i + 1 < parts.size) 0 else 24)
            }
            i += 2
        }
        header(rows, "Hand")
        grid(rows, listOf(options.mainHand()))
    }

    private fun more(rows: MutableList<Pair<AbstractWidget, Int>>) {
        header(rows, "Online")
        grid(rows, listOf(options.realmsNotifications(), options.allowServerListing(), options.sharePresence(), options.telemetryOptInExtra()))
        header(rows, "Other settings")
        rows += OwButton(bodyLeft, 0, bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP, 20, Component.literal("Vanilla options (resource packs, language, shaders, mods)...")) {
            OptionsInterceptor.openVanilla(this, null)
        } to 26
        header(rows, "WynnOverhaul")
        rows += OwButton(bodyLeft, 0, bodyWidth - OwTheme.SCROLLBAR_W - OwTheme.GAP, 20, Component.literal("WynnOverhaul settings...")) {
            Minecraft.getInstance().setScreenAndShow(WynnOverhaulHubScreen(this))
        } to 26
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val mapping = listening
        if (mapping != null) {
            if (event.key() != KEY_ESCAPE) {
                mapping.setKey(InputConstants.getKey(event))
                KeyMapping.resetMapping()
            }
            listening = null
            rebuildWidgets()
            return true
        }
        if (event.key() != KEY_ESCAPE && embeddedLive()?.keyPressed(event) == true) return true
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        val mapping = listening
        if (mapping != null && event.x() >= bodyLeft) {
            mapping.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()))
            KeyMapping.resetMapping()
            listening = null
            rebuildWidgets()
            return true
        }
        val inner = embeddedLive()
        if (inner != null && overBody(event.x(), event.y()) && inner.mouseClicked(shifted(event), doubled)) return true
        return super.mouseClicked(event, doubled)
    }

    override fun onClose() {
        embedded?.removed()
        options.save()
        super.onClose()
    }

    private companion object {
        const val KEY_ESCAPE = 256
        private val sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium")
        var current = Tab.VIDEO
    }
}

object OptionsInterceptor {
    @JvmField
    var bypass = false
    private var swapping = false

    fun openVanilla(from: Screen, pageKey: String?) {
        val client = Minecraft.getInstance()
        bypass = true
        val screen = OptionsScreen(from, client.options, false)
        client.setScreenAndShow(screen)
        if (pageKey == null) return
        val label = Component.translatable(pageKey).string
        net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)
            .firstOrNull { it.message.string == label }
            ?.let { (it as? net.minecraft.client.gui.components.Button)?.onPress(MouseButtonEvent(0.0, 0.0, net.minecraft.client.input.MouseButtonInfo(0, 0))) }
    }

    fun register() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register { client, screen, _, _ ->
            if (swapping) return@register
            val options = screen.javaClass == OptionsScreen::class.java
            val multiplayer = screen.javaClass == net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen::class.java
            if (!options && !multiplayer) return@register
            if (bypass) {
                bypass = false
                return@register
            }
            if (!WynnOverhaulConfig.current.customMenusEnabled) return@register
            client.schedule {
                if (client.gui.screen() !== screen) return@schedule
                swapping = true
                try {
                    val parent: Screen = if (client.level == null) net.minecraft.client.gui.screens.TitleScreen() else net.minecraft.client.gui.screens.PauseScreen(true)
                    client.setScreenAndShow(if (options) WynnOverhaulOptionsScreen(parent) else WynnOverhaulMultiplayerScreen(parent))
                } finally {
                    swapping = false
                }
            }
        }
    }
}
