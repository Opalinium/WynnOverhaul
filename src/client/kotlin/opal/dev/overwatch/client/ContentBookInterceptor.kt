package opal.dev.overwatch.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

class ContentBookInterceptor : ClientModInitializer {

    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()
        ScreenEvents.BEFORE_INIT.register { client, screen, _, _ ->
            if (!OverwatchConfig.current.contentBookOverrideEnabled) return@register
            if (screen !is AbstractContainerScreen<*>) return@register
            if (!screen.title.string.contains(CONTENT_BOOK_TITLE_MARKER)) return@register

            val menu = screen.menu
            openMenu = menu
            val toggle = pendingTrackToggle
            pendingTrackToggle = null
            val refreshTarget = pendingRefreshScreen
            pendingRefreshScreen = null

            when {
                toggle != null -> startTrackToggle(client, menu, toggle.first, toggle.second)
                refreshTarget != null -> {
                    client.setScreenAndShow(refreshTarget)
                    enumerate(client, menu, refreshTarget)
                }
                else -> startOpen(client, menu)
            }
        }
    }

    companion object {
        const val CONTENT_BOOK_TITLE_MARKER = "󏿮"

        var pendingTrackToggle: Pair<ActivityType, String>? = null

        private var pendingRefreshScreen: OverwatchContentBookScreen? = null
        private var openMenu: AbstractContainerMenu? = null
        private var pendingToggleKey: Pair<ActivityType, String>? = null

        fun toggleTracking(client: Minecraft, screen: OverwatchContentBookScreen, activity: ActivityInfo): String? {
            val key = activity.type to activity.name
            if (pendingToggleKey != null) {
                return if (pendingToggleKey == key) null else "Still working on the last tracking change"
            }
            val player = client.player ?: return "No player"
            val menu = openMenu
            if (menu != null && player.containerMenu === menu) {
                pendingToggleKey = key
                if (ContentBookQuery.isEnumerating) ContentBookCache.commitIfFirstEver(ContentBookQuery.currentResults())
                ContentBookQuery.cancel()
                ContentBookQuery.startTrackToggle(
                    menu = menu,
                    type = activity.type,
                    name = activity.name,
                    onFound = { pendingToggleKey = null; screen.applyTrackToggle(activity) },
                    onFailed = { pendingToggleKey = null; screen.showMessage("Couldn't find ${activity.name} in the book") },
                )
                return null
            }
            val hand = contentBookHand(player) ?: return "Hold the Content Book item to change tracking"
            pendingToggleKey = key
            pendingTrackToggle = key
            client.gameMode?.useItem(player, hand)
            return null
        }

        fun requestRefresh(client: Minecraft, screen: OverwatchContentBookScreen) {
            val player = client.player ?: return
            val menu = openMenu
            if (menu != null && player.containerMenu === menu) {
                enumerate(client, menu, screen)
                return
            }
            val hand = contentBookHand(player)
            if (hand == null) {
                screen.showMessage("Hold the Content Book item to refresh")
                return
            }
            pendingRefreshScreen = screen
            client.gameMode?.useItem(player, hand)
        }

        private fun startOpen(client: Minecraft, menu: AbstractContainerMenu) {
            val cached = ContentBookCache.snapshot
            if (cached != null) {
                val screen = OverwatchContentBookScreen(cached)
                if (ContentBookCache.needsRefresh()) {
                    client.setScreenAndShow(screen)
                    enumerate(client, menu, screen)
                } else {
                    client.player?.closeContainer()
                    openMenu = null
                    client.setScreenAndShow(screen)
                }
            } else {
                client.setScreenAndShow(OverwatchContentBookLoadingScreen())
                enumerate(client, menu)
            }
        }

        private fun startTrackToggle(client: Minecraft, menu: AbstractContainerMenu, type: ActivityType, name: String) {
            val cached = ContentBookCache.snapshot
            if (cached != null && ContentBookCache.find(type, name)) {
                val screen = OverwatchContentBookScreen(cached)
                client.setScreenAndShow(screen)
                ContentBookQuery.startTrackToggle(
                    menu = menu,
                    type = type,
                    name = name,
                    onFound = {
                        pendingToggleKey = null
                        val updated = ContentBookCache.applyTrackToggle(type, name) ?: cached
                        screen.updateActivities(updated)
                        restoreOrClose(client, screen)
                    },
                    onFailed = {
                        pendingToggleKey = null
                        restoreOrClose(client, screen)
                    },
                )
            } else {
                pendingToggleKey = null
                client.setScreenAndShow(OverwatchContentBookLoadingScreen())
                enumerate(client, menu)
            }
        }

        private fun restoreOrClose(client: Minecraft, restoreTo: OverwatchContentBookScreen?) {
            val before = client.gui.screen()
            client.player?.closeContainer()
            openMenu = null
            val target = if (before != null && before !is OverwatchContentBookLoadingScreen) before else restoreTo
            if (target != null) client.setScreenAndShow(target) else client.gui.setScreen(null)
        }

        private fun enumerate(client: Minecraft, menu: AbstractContainerMenu, initial: OverwatchContentBookScreen? = null) {
            var shown: OverwatchContentBookScreen? = initial
            val silent = initial != null
            ContentBookQuery.start(
                menu = menu,
                seed = initial?.currentActivities() ?: emptyList(),
                onProgress = { activities ->
                    val current = shown
                    if (current == null) {
                        val screen = OverwatchContentBookScreen(activities)
                        shown = screen
                        client.setScreenAndShow(screen)
                    } else if (!silent) {
                        current.updateActivities(activities)
                    }
                },
                onComplete = { activities ->
                    ContentBookCache.commit(activities)
                    val current = shown
                    current?.updateActivities(activities)
                    restoreOrClose(client, current ?: OverwatchContentBookScreen(activities))
                },
                onFailed = { restoreOrClose(client, shown) },
            )
        }

        private fun contentBookHand(player: Player): InteractionHand? = when {
            isContentBook(player.mainHandItem) -> InteractionHand.MAIN_HAND
            isContentBook(player.offhandItem) -> InteractionHand.OFF_HAND
            else -> null
        }

        private fun isContentBook(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            val name = stack.hoverName.string
            if (name.contains(CONTENT_BOOK_TITLE_MARKER)) return true
            val letters = name.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
            return letters.contains("Content Book", ignoreCase = true)
        }
    }
}
