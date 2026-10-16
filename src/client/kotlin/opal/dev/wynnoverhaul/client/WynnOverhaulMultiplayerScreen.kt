package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.NativeImage
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.network.EventLoopGroupHolder
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

class WynnOverhaulMultiplayerScreen(parent: Screen?) : OwScreen(Component.literal("Multiplayer"), parent) {
    override val panelWidth: Int get() = (width * 0.82).toInt().coerceIn(520, 940).coerceAtMost((width - 20).coerceAtLeast(320))
    override val panelHeight: Int get() = (height * 0.86).toInt().coerceIn(320, 600)

    private val client = Minecraft.getInstance()
    private val servers = ServerList(client).also { it.load() }
    private val pinger = ServerStatusPinger()
    private var selected = -1
    private var filter = ""
    private var confirmDelete = false
    private var list: ServerRows? = null

    private val sideWidth = 150

    override fun init() {
        super.init()
        val listWidth = contentWidth - sideWidth - OwTheme.PAD
        val search = OwTextField(font, contentLeft, contentTop, listWidth, 20)
        search.setHint(Component.literal("Search servers"))
        search.value = filter
        search.clearable = true
        search.setResponder {
            filter = it
            list?.refresh()
        }
        addRenderableWidget(search)
        val rows = ServerRows(contentLeft, contentTop + 26, listWidth, contentBottom - contentTop - 26, this)
        list = rows
        addRenderableWidget(rows)
        val x = contentLeft + listWidth + OwTheme.PAD
        var y = contentTop
        fun button(label: String, accent: Boolean = false, enabled: () -> Boolean = { true }, action: () -> Unit) {
            addRenderableWidget(OwButton(x, y, sideWidth, OwTheme.ROW_H, Component.literal(label), accent = accent, enabled = enabled, onPress = action))
            y += OwTheme.ROW_H + 4
        }
        button("Join Server", accent = true, enabled = { selected in 0 until servers.size() }) { join(selected) }
        button("Direct Connect") { directConnect() }
        button("Add Server") { addServer() }
        button("Edit Server", enabled = { selected in 0 until servers.size() }) { editServer() }
        button(if (confirmDelete) "Click again to delete" else "Delete Server", enabled = { selected in 0 until servers.size() }) { deleteServer() }
        y += 8
        button("Move Up", enabled = { selected > 0 }) { move(-1) }
        button("Move Down", enabled = { selected in 0 until servers.size() - 1 }) { move(1) }
        y += 8
        button("Refresh") { refresh() }
        if (FabricLoader.getInstance().isModLoaded("authme")) {
            y += 8
            button("Accounts") { openAccounts() }
        }
        pingAll()
    }

    private fun openAccounts() {
        runCatching {
            Class.forName("me.axieum.mcmod.authme.api.gui.screen.AuthMethodScreen")
                .getConstructor(Screen::class.java)
                .newInstance(this) as Screen
        }.getOrNull()?.let { client.setScreenAndShow(it) }
    }

    private fun pingAll() {
        for (i in 0 until servers.size()) ping(servers.get(i))
    }

    private fun ping(data: ServerData) {
        if (data.state() != ServerData.State.INITIAL) return
        data.setState(ServerData.State.PINGING)
        data.motd = Component.empty()
        data.playerList = emptyList()
        try {
            pinger.pingServer(data, { ServerList.saveSingleServer(data) }, {}, EventLoopGroupHolder.remote(client.options.useNativeTransport()))
        } catch (e: Exception) {
            data.setState(ServerData.State.UNREACHABLE)
        }
    }

    private fun refresh() {
        pinger.removeAll()
        for (i in 0 until servers.size()) servers.get(i).setState(ServerData.State.INITIAL)
        pingAll()
    }

    fun serverCount(): Int = servers.size()
    fun server(i: Int): ServerData = servers.get(i)
    fun selectedIndex(): Int = selected
    fun filterText(): String = filter

    fun select(index: Int) {
        if (selected != index) {
            confirmDelete = false
            selected = index
            rebuildWidgetsKeepList()
        }
    }

    private fun rebuildWidgetsKeepList() {
        val scroll = list?.scrollValue() ?: 0f
        rebuildWidgets()
        list?.setScroll(scroll)
    }

    fun join(index: Int) {
        if (index !in 0 until servers.size()) return
        val data = servers.get(index)
        servers.save()
        ConnectScreen.startConnecting(this, client, ServerAddress.parseString(data.ip), data, false, null)
    }

    private fun directConnect() {
        client.setScreenAndShow(
            WynnOverhaulServerEditScreen(this, "Direct Connect", false, "", client.options.lastMpIp ?: "", "Connect") { _, address ->
                val data = ServerData("Direct", address, ServerData.Type.OTHER)
                client.options.lastMpIp = address
                client.options.save()
                ConnectScreen.startConnecting(this, client, ServerAddress.parseString(address), data, false, null)
            },
        )
    }

    private fun addServer() {
        client.setScreenAndShow(
            WynnOverhaulServerEditScreen(this, "Add Server", true, "Minecraft Server", "", "Add Server") { name, address ->
                servers.add(ServerData(name, address, ServerData.Type.OTHER), false)
                servers.save()
                selected = servers.size() - 1
            },
        )
    }

    private fun editServer() {
        val data = servers.get(selected)
        client.setScreenAndShow(
            WynnOverhaulServerEditScreen(this, "Edit Server", true, data.name, data.ip, "Save") { name, address ->
                data.name = name
                data.ip = address
                data.setState(ServerData.State.INITIAL)
                servers.save()
            },
        )
    }

    private fun deleteServer() {
        if (!confirmDelete) {
            confirmDelete = true
            rebuildWidgetsKeepList()
            return
        }
        servers.remove(servers.get(selected))
        servers.save()
        confirmDelete = false
        selected = min(selected, servers.size() - 1)
        rebuildWidgetsKeepList()
    }

    private fun move(delta: Int) {
        val to = selected + delta
        if (to !in 0 until servers.size()) return
        servers.swap(selected, to)
        servers.save()
        selected = to
        rebuildWidgetsKeepList()
    }

    override fun tick() {
        super.tick()
        pinger.tick()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!isTextInputFocused()) {
            when (event.key()) {
                KEY_UP -> {
                    select(max(0, selected - 1))
                    return true
                }
                KEY_DOWN -> {
                    select(min(servers.size() - 1, selected + 1))
                    return true
                }
                KEY_ENTER -> {
                    join(selected)
                    return true
                }
            }
        }
        return super.keyPressed(event)
    }

    override fun removed() {
        pinger.removeAll()
        servers.save()
        super.removed()
    }

    private companion object {
        const val KEY_UP = 265
        const val KEY_DOWN = 264
        const val KEY_ENTER = 257
    }
}

private class ServerRows(x: Int, y: Int, width: Int, height: Int, private val screen: WynnOverhaulMultiplayerScreen) :
    AbstractWidget(x, y, width, height, Component.literal("Servers")) {
    private var scroll = 0f
    private var indices: List<Int> = emptyList()
    private var lastClickMillis = 0L
    private var draggingBar = false
    private var lastClickRow = -1
    private val icons = HashMap<String, Identifier>()

    init {
        refresh()
    }

    fun refresh() {
        val f = screen.filterText().trim().lowercase()
        indices = (0 until screen.serverCount()).filter {
            val s = screen.server(it)
            f.isEmpty() || s.name.lowercase().contains(f) || s.ip.lowercase().contains(f)
        }
        clampScroll()
    }

    fun scrollValue(): Float = scroll

    fun setScroll(value: Float) {
        scroll = value
        clampScroll()
    }

    private fun clampScroll() {
        scroll = scroll.coerceIn(0f, max(0, indices.size * ROW_H - height).toFloat())
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        graphics.fill(x, y, x + width, y + height, OwTheme.PANEL_ALT or 0xFF000000.toInt())
        graphics.outline(x, y, width, height, OwTheme.HAIRLINE)
        if (indices.isEmpty()) {
            val text = if (screen.serverCount() == 0) "No servers yet. Add one or use Direct Connect." else "No servers match your search."
            graphics.centeredText(font, text, x + width / 2, y + height / 2 - 4, OwTheme.TEXT_DIM)
            return
        }
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1)
        for ((slot, index) in indices.withIndex()) {
            val ry = y + slot * ROW_H - scroll.toInt()
            if (ry + ROW_H < y || ry > y + height) continue
            drawRow(graphics, font, index, ry, mouseX, mouseY)
        }
        graphics.disableScissor()
        val total = indices.size * ROW_H
        if (total > height) {
            val barH = max(16, height * height / total)
            val barY = y + ((height - barH) * (scroll / (total - height))).toInt()
            graphics.fill(x + width - 4, barY, x + width - 1, barY + barH, OwTheme.ACCENT_DIM)
        }
    }

    private fun drawRow(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, index: Int, ry: Int, mouseX: Int, mouseY: Int) {
        val data = screen.server(index)
        val hovered = mouseX >= x && mouseX < x + width - 6 && mouseY >= max(ry, y) && mouseY < min(ry + ROW_H, y + height) && mouseY >= ry
        val selected = index == screen.selectedIndex()
        graphics.fill(x + 1, ry, x + width - 1, ry + ROW_H - 2, if (selected) 0xFF2E2413.toInt() else if (hovered) OwTheme.TILE_HOVER or 0xFF000000.toInt() else OwTheme.TILE_BG or 0xFF000000.toInt())
        graphics.outline(x + 1, ry, width - 2, ROW_H - 2, if (selected) OwTheme.ACCENT else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.TILE_BORDER)
        val icon = icon(data)
        val ix = x + 8
        val iy = ry + (ROW_H - 2 - ICON) / 2
        if (icon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0f, 0f, ICON, ICON, 64, 64, 64, 64)
        } else {
            graphics.fill(ix, iy, ix + ICON, iy + ICON, OwTheme.ACCENT_DIM)
            graphics.centeredText(font, (data.name.firstOrNull()?.uppercaseChar() ?: '?').toString(), ix + ICON / 2, iy + (ICON - 8) / 2, OwTheme.TEXT)
        }
        val tx = ix + ICON + 8
        val right = x + width - 14
        val statusWidth = 96
        val textWidth = right - statusWidth - tx
        graphics.text(font, truncateToWidth(font, data.name, textWidth), tx, ry + 6, OwTheme.TEXT)
        when (data.state()) {
            ServerData.State.UNREACHABLE -> graphics.text(font, "Can't connect to server", tx, ry + 19, OwTheme.BAD)
            ServerData.State.PINGING, ServerData.State.INITIAL -> graphics.text(font, "Pinging...", tx, ry + 19, OwTheme.TEXT_FAINT)
            else -> {
                val lines = font.split(data.motd, textWidth)
                if (lines.isNotEmpty()) graphics.text(font, lines[0], tx, ry + 19, OwTheme.TEXT_DIM)
                if (lines.size > 1) graphics.text(font, lines[1], tx, ry + 29, OwTheme.TEXT_DIM)
            }
        }
        drawStatus(graphics, font, data, right, ry)
    }

    private fun drawStatus(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, data: ServerData, right: Int, ry: Int) {
        val state = data.state()
        if (state == ServerData.State.SUCCESSFUL || state == ServerData.State.INCOMPATIBLE) {
            val ping = data.ping
            val lit = when {
                ping < 150 -> 5
                ping < 300 -> 4
                ping < 600 -> 3
                ping < 1000 -> 2
                else -> 1
            }
            val color = if (state == ServerData.State.INCOMPATIBLE) OwTheme.WARN else if (lit >= 4) OwTheme.GOOD else if (lit >= 3) OwTheme.WARN else OwTheme.BAD
            for (b in 0 until 5) {
                val h = 3 + b * 2
                graphics.fill(right - 30 + b * 6, ry + 18 - h, right - 26 + b * 6, ry + 18, if (b < lit) color else OwTheme.TILE_BORDER)
            }
            graphics.text(font, "${ping}ms", right - 30 - font.width("${ping}ms") - 6, ry + 11, OwTheme.TEXT_DIM)
            val players = data.players
            val text = if (state == ServerData.State.INCOMPATIBLE) "Incompatible" else if (players != null) "${players.online()}/${players.max()}" else ""
            graphics.text(font, text, right - font.width(text), ry + 24, if (state == ServerData.State.INCOMPATIBLE) OwTheme.WARN else OwTheme.TEXT_DIM)
        }
    }

    private fun icon(data: ServerData): Identifier? {
        val bytes = data.iconBytes ?: return null
        val key = data.ip + bytes.size
        icons[key]?.let { return it }
        return try {
            val image = NativeImage.read(ByteArrayInputStream(bytes))
            val id = Identifier.fromNamespaceAndPath("wynnoverhaul", "servericon/${key.lowercase().filter { it.isLetterOrDigit() }}")
            Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "wynnoverhaul server icon" }, image))
            icons[key] = id
            id
        } catch (e: Exception) {
            null
        }
    }

    private fun overBar(mx: Double, my: Double): Boolean =
        indices.size * ROW_H > height && mx >= x + width - 8 && mx < x + width && my >= y && my < y + height

    private fun dragBar(my: Double) {
        val total = indices.size * ROW_H
        val barH = max(16, height * height / total)
        val t = ((my - y - barH / 2.0) / max(1, height - barH)).coerceIn(0.0, 1.0)
        scroll = (t * (total - height)).toFloat()
        clampScroll()
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (!draggingBar) return false
        dragBar(event.y())
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val was = draggingBar
        draggingBar = false
        return was
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!active || !visible || event.button() != 0 || !isMouseOver(event.x(), event.y())) return false
        if (overBar(event.x(), event.y())) {
            draggingBar = true
            dragBar(event.y())
            return true
        }
        val slot = ((event.y() - y + scroll) / ROW_H).toInt()
        if (slot !in indices.indices) return true
        val index = indices[slot]
        val now = System.currentTimeMillis()
        if (doubled || (lastClickRow == index && now - lastClickMillis < 350)) {
            screen.join(index)
            return true
        }
        lastClickRow = index
        lastClickMillis = now
        screen.select(index)
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        scroll -= (scrollY * 30).toFloat()
        clampScroll()
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}

    private companion object {
        const val ROW_H = 46
        const val ICON = 32
    }
}

class WynnOverhaulServerEditScreen(
    parent: Screen?,
    title: String,
    private val showName: Boolean,
    private val initialName: String,
    private val initialAddress: String,
    private val confirmLabel: String,
    private val onDone: (String, String) -> Unit,
) : OwScreen(Component.literal(title), parent) {
    override val panelWidth: Int get() = 420.coerceAtMost(width - 20)
    override val panelHeight: Int get() = if (showName) 190 else 150

    private var nameField: OwTextField? = null
    private var addressField: OwTextField? = null
    private var name = initialName
    private var address = initialAddress

    override fun init() {
        super.init()
        var y = contentTop
        if (showName) {
            addRenderableWidget(OwLabel(contentLeft, y, contentWidth, 12, "Server name", OwTheme.TEXT_DIM))
            val field = OwTextField(font, contentLeft, y + 14, contentWidth, 20)
            field.value = name
            field.setResponder { name = it }
            addRenderableWidget(field)
            nameField = field
            y += 44
        }
        addRenderableWidget(OwLabel(contentLeft, y, contentWidth, 12, "Server address", OwTheme.TEXT_DIM))
        val field = OwTextField(font, contentLeft, y + 14, contentWidth, 20)
        field.setMaxLength(128)
        field.value = address
        field.setResponder { address = it }
        addRenderableWidget(field)
        addressField = field
        setInitialFocus(nameField ?: field)
        val half = (contentWidth - OwTheme.GAP) / 2
        val by = contentBottom - OwTheme.ROW_H
        addRenderableWidget(OwButton(contentLeft, by, half, OwTheme.ROW_H, Component.literal(confirmLabel), accent = true, enabled = { address.isNotBlank() }) { done() })
        addRenderableWidget(OwButton(contentLeft + half + OwTheme.GAP, by, half, OwTheme.ROW_H, Component.literal("Cancel")) { onClose() })
    }

    private fun done() {
        if (address.isBlank()) return
        val back = parent
        onDone(name.ifBlank { "Minecraft Server" }, address.trim())
        if (back != null && Minecraft.getInstance().gui.screen() === this) Minecraft.getInstance().setScreenAndShow(back)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == 257 && address.isNotBlank()) {
            done()
            return true
        }
        return super.keyPressed(event)
    }
}
