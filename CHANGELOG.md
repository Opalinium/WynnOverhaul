# Changelog

All notable changes are listed here. Versions follow Semantic Versioning.

## Unreleased

### Added
- A Greatsword moveset with its own stance and strokes.
- The party panel shows health bars, colored names, dead and offline states and distances, with your own name in gold.
- Fishing rods cast a bobber at the node when a fishing gather starts and reel it in when it finishes.
- The spell bar shows the next expected spell cost and tints it when repeated casts have raised it.
- Rapid hits on one target within half a second merge into one growing damage number.
- Buff and debuff rows keep a fixed order with harmful effects marked, and effects pulse during their last five seconds.
- Low health effects: Vignette, Heartbeat, Pulsing border and Dripping blood, with threshold and strength sliders.
- Pinned waypoints: pin several activities or places, choose a main target with the Cycle Main Waypoint hotkey, and see them in a list with distances. /owpin commands and journal pinning are included.
- Search filters for items and journal activities.
- Compared tooltips show the difference from your equipped item after each stat.
- A settings search, markers on changed settings and a per-page reset.
- A Ping hotkey marks the spot you are looking at and sends it to your party in the same format Wynntils uses. Locations other players send in chat, including wynnmod pings, become waypoints.
- Chat highlights mentions of your name with an optional chime, and has a reply hotkey, unread counts for party and guild, a longer history and a SYS channel for system messages.
- Alt+click locks an inventory slot so shift-clicks, sell-all and drops skip it. Optional rarity letters mark Unique, Rare, Legendary, Fabled and Mythic items.
- Search bars have a clear button and Ctrl+F, and the last inventory and merchant search is kept.
- Merchant offers you cannot buy yet are dimmed, and hovered offers compare against your equipped gear.
- Weapon strokes, stances and walk poses play on other players within 48 blocks.
- A character viewport in the weapon animation settings plays any moveset or spell, with a speed slider and drag to rotate.
- The title screen and pause menu use the WynnOverhaul layout, with a Play Wynncraft button.
- The main menu has five scenes you can switch between: Campfire, Snowed In, Aurora, Downpour and Dusk Dunes.
- Vanilla buttons, sliders, checkboxes, lists and backgrounds are restyled in every vanilla screen.
- A tabbed options screen replaces the vanilla one, with Video, Controls, Key Binds, Audio, Chat, Accessibility, Skin and More tabs. The Sodium video settings open inside the Video tab.
- A multiplayer server list with search, ping status, icons, add, edit, delete, reorder and direct connect, plus an Authme accounts button.
- Your last level, class and equipment appear on the main menu figure, with armor and weapon models taken from the Wynncraft resource pack.
- A toast appears when you complete a raid.
- The compass has a footer with your coordinates and the Wynncraft world.
- Players in a guild war show in their own panel, read from the War scoreboard section.
- Menu buttons, panels and inputs have more depth: raised buttons that lift on hover, recessed fields, beveled panels and an engraved title band.
- Loading screens for connecting, switching worlds, loading terrain and reloading resources, with an animated backdrop, rotating tips and a fade into the game. The Mojang red screen no longer shows when loading finishes.
- Item tooltips are drawn as cards with a frame and glow in the color of the item name.
- A Forged chat style with gradient lines, a gold edge and a recessed input bar. It is the new default.

### Changed
- Less work per frame in the HUD, scoreboard parsing, item tooltips and entity scans, and the quest and wiki data load in the background after you join.
- F8 is now a contextual debug capture: it dumps an open container, captures running dialogue or scans nearby entities.
- Custom screen panels share one style with a drop shadow, a gold sheen and corner accents.
- Every moveset has its own spell casts, with their own timing, arcs and sounds. Charge and Escape are airborne leaps with a landing slam.
- The orbit camera crosshair and the powder special aim at the point your attacks will actually hit.
- Spell casts, light attack chains, tool strokes and the walk and sprint cycles were reworked. Legs follow planted foot targets, and strafe and backpedal cycles and sprint charge attacks were added.
- The war scream is hand authored, swings time to the weapon attack interval, and the legs pose for jumping, landing, crouching and climbing.
- Each stroke holds its last frame so chains flow from it, every spell strikes on input, and the uppercut is one hand-authored stroke. Sprint attacks were removed.
- Each chat channel shows only its own messages, and All shows everything. SYS is a popout panel.
- The greatsword rests on the shoulder at idle, and strokes are authored from waypoints in place of mirrored copies.
- The spine and pelvis bend, the walk and sprint were reworked, and every stroke has legwork.

### Fixed
- The augmented Item Identifier opens in the workbench layout.
- Bank deposits only jump to bank pages that exist and are unlocked.
- Dialogue choices follow scrolling correctly, long options keep their full text, and no stray 0 shows when a dialogue ends.
- The main menu figure keeps your skin instead of falling back to the default.
- Auto attack and quick spells no longer fire without a weapon in hand.
- Buffs and debuffs are read from the tab Status Effects list, so debuffs show.
- The vanilla action bar no longer shows through when it carries the vitals bar.
- Bank deposits stop retrying when the server refuses to merge a stack, and the refusal is remembered.
- The Mod Menu update badge no longer spills out of the pause menu, and settings rows no longer draw outside their panel.

## 1.5.0 - 2026-10-09

### Added
- Your hits land with weight: the weapon swing holds and recoils on contact, layered impact sounds change with your weapon and deepen on heavy hits, sparks burst from the target in the hit's element, and spell hits end in a ground shockwave. Heavier hits compared with your recent hits respond more strongly, and strength is adjustable with a toggle for each effect.
- Floating damage numbers grow with how hard the hit is and are colored by element. Four styles (Pop, Float, Slam, Fountain), a size slider and compact formatting such as 2.4k are available in the Combat tab.
- Spear and dagger attacks step your body forward into the swing and ease back out of it.
- Hits draw a short slash arc across the target, lunging attacks kick up dust from the ground, and heavy spell shockwaves crack the ground.
- Impact sounds end with a high tail and get quieter with distance.
- Weapon swings linger in the wind-up and ease out after the strike, strongest on slow weapons.
- Your weapon arm and body flinch when you take damage.
- The Dump Open Container key (F8 by default) copies the items, names and lore of the open container to the clipboard and saves the full data to a file in the game folder. With no menu open it copies the game state, including the last dialogue prompt the dialogue tracker caught and the last one it missed, for reporting what a menu or dialogue contains.
- Attacking while walking or sprinting uses a movement variant: legs stay on the stride and brace on the strike, the torso twist is reduced, and the body drives forward into the hit, more so when sprinting.
- Town NPC markers include the Trade Market.
- Weapon, spell and gathering tool animations are now defined in YAML files that can be edited or extended without rebuilding the mod: put files in the config folder's animations directory to override or add poses, stances and attack chains. Changes apply while the game runs, `/owanim reload` forces a reload, and mistakes in a file are reported without breaking the rest. Each pose also has its own blend time. The format is documented in docs/animations.md, and a short readme is written to the animations folder on first run.
- Every weapon type has full movesets, each with its own stance and a chain of strokes built to follow one another: the spear has Lancer, Quarterstaff, Halberd, Naginata, Twinblades, Axe, Great axe and Hammer, the dagger Duelist, Stiletto and Dancer, the wand Spellcaster, Staff and Conductor, the relik Channeler and Ritualist, and the bow Archer and Firearm. Pick them in the weapon animation menu.
- The movement style now shapes weapon strokes and spell casts: heavy styles drive the whole body harder and ease in slower, light and stealthy styles are quicker and more compact.
- Walking, jumping, crouching, swimming, crawling, climbing, riding, gliding, eating, blocking, hurt and death animations are defined in YAML with a small formula language, so they can be tuned or extended without rebuilding. The first-person body now hides itself while swimming, crawling, gliding, sleeping and riptide spinning, with the poses and body offsets editable in the same files.
- Identified item stats show their roll as a percentage colored from red to aqua, and the item name ends with the average roll, both read from the roll meter on the item and adjustable in the QoL tab.
- Gathering tools have their own animations: pickaxes strike overhead, axes chop diagonally, scythes reap in a low sweep, and fishing rods cast back and forward. Strokes follow the swings the server sends while you gather, each lasting a few swings so they stay readable at any gathering speed, and fishing rods play a separate reel animation when the harvest completes. They carry in a ready stance like weapons do, and can be turned off in the Animations tab.
- Loot chests open in a custom screen styled like the inventory, with the chest contents as sorted tiles beside your inventory, a Take all button, and the chest tier.
- The Item Upgrader menu opens in a custom screen with a card for each upgrade option showing its description, and the unavailable options grouped as coming later.
- The Upgrade Powders, Remove Powders and Ascend Equipment menus open in a custom screen with the input slots, the result, the status text and the action button beside your inventory, and clicking an inventory item adds it.
- The Item Identifier, item selling and item repair menus use the same custom screen, with the Corkian Augments and Repair Items buttons in the corner.
- Merchant shops open in a custom screen that lists each offer with its price, level requirement and main effect, colors prices you can't afford, and has search and sorting by price, level or name. Any merchant works, whatever it stocks.
- A first-person body option in the Camera tab renders your third-person model while in first person, hides your head so it never blocks the view, shifts your body ahead of the camera so looking down shows your chest and legs, and hides the floating vanilla hands since your real hands hold your items.
- Every spear, dagger, wand, staff, relik and gathering tool attack now moves the whole body: the lead leg plants and the hips twist into the strike at the hit moment, with thrusts lunging, sweeps turning and overheads bracing, mirrored on alternate-side swings.
- Movement leans the torso into turns with the head counter-turning to hold gaze, pitches the body to match slopes while walking, and sways the arms with idle breathing.
- Swimming uses a freestyle arm stroke with a kick that quickens and strengthens with speed, and crawling under low ceilings gets its own low crawl with reaching arms instead of reusing the swim.
- While you gather, the XP bar and Discord presence show your profession and its level instead of your combat level.
- Dropped items get a subtle gold beam and ground glow, with a toggle and an intensity slider in the Alerts tab.
- Weapons get their moveset from the skin they wear: the model id Wynncraft puts on the item picks the style that suits its shape, such as the Axe for the Galvanic Decollator, the Great axe for battleaxes and greatswords, the Hammer for hammers and maces, Twinblades, Naginata, Halberd, Stiletto, Dancer, Staff, Conductor, Ritualist and Firearm skins. Anything else keeps the style for its weapon type, and a style you assign in the weapon animation menu still wins.
- A `cinder:lod_bounds` entrypoint hands Cinder the Wynncraft map bounds (minX, minZ, maxX, maxZ) while you are on Wynncraft, so it can skip distant terrain outside the map. It follows the same toggle as hiding Voxy terrain, now labeled Hide LODs outside Wynncraft.
- The HUD hides while a cutscene plays, including the bars, hotbar, spell bar, crosshair and boss bars, while dialogue stays on screen. A toggle in the HUD tab turns it off.

### Changed
- Tracking an activity from the journal goes straight to the page it was seen on instead of walking the book page by page, moves on as soon as the server's page update has arrived instead of polling the screen for four ticks, and no longer restarts the whole book scan afterwards, only the filters it had not finished. The journal also rebuilds at most twice a second while the book is being read.
- Combat settings moved from the HUD and QoL tabs into their own Combat tab.
- Custom menu settings moved from the QoL and Advanced tabs into a new Menus tab, and the bank, loot chest, Item Upgrader, identifier, selling and repair, merchant and mount menus each have their own toggle.
- Weapon afterimages default to the lowest opacity and swing sounds to 0.45 volume.
- Depositing an item into the bank first tops up an existing stack of the same item, on any page, before using an empty slot.
- Item rarity is read from the item's tier data when it has any, and a colored symbol before the name no longer decides the tier.

### Fixed
- Tracked dungeons point at their known entrance coordinates instead of being triangulated from the beacon, so the waypoint no longer drifts when you move or climb. A dungeon beacon seen while tracking something else snaps to the entrance it points at.
- Shield summons are matched when the cast message arrives up to 0.8 seconds before the shield appears.
- Mantle, Broken Mantle and Guardian Angels are matched again after the Wynncraft resource pack renumbered their models.
- The owdump report lists nearby item displays with their model ids, plus the class buff tracker state.

## 1.4.0 - 2026-10-01

### Added
- The Character page has an Identifications sub-tab that lists your total identifications across every page, colored positive or negative as in game.
- The Totem tracker also tracks nearby mob and gathering totems from any player, labeled by owner, with timers over a minute shown as minutes and seconds. Class totems stay limited to your own.
- A DPS meter HUD element shows your damage per second, the fight total, the peak and an element split, read from the floating damage numbers above mobs.
- A Class Buffs HUD element tracks your Mantle and Broken Mantle charges, Guardian Angels, Arrow Shield and Judrajim, plus your Crow, Hound, Snake and shaman puppet summons with their timers.
- The Character Bank opens in a custom screen with your inventory and paper doll on the left and the bank in a side panel. It scans every page when opened and shows them as one scrollable list with its own search and sort. Clicking a bank item takes it out, clicking an inventory item deposits it into the first open slot on any page, and a storage dropdown switches between Character and Account, with Stash, Dump, a rescan and a Buy page button.
- The /owdump command copies the tab footer, action bar, boss bars, potion effects, the last opened container and nearby floating labels to the clipboard, for reporting what the game shows.
- The Journal shows your Content Book completion progress for quests, territorial, world and secret discoveries.

### Changed
- The Ingredients and Emeralds buttons are gone. Click the ingredient pouch or right-click the emerald pouch in the inventory to open its contents in the side panel, which fades in behind a loading placeholder.
- The inventory page stays on screen while pouches, the Journal and Character Info open and close, instead of flashing the vanilla container or the game view in between.
- The emerald pouch slot only accepts an Emerald Pouch, and a pouch sitting in the hotbar no longer blocks its slot.
- The Character page stats sit in cards with aligned label and value columns. Combat stats are grouped under their headings, and quests moved up into the header line.
- The Mount Settings screen shows each setting as a card with its options, with the active option highlighted.
- The Journal detail pane is a card with aligned requirements and a clearer rewards list.
- The Quest Book, quest detail and wiki screens use aligned rows and section headers.
- The inventory panel is narrower, since the last two hotbar slots are never shown.
- Tabs and screens fade in, loading states show placeholders instead of plain text, and the Identifications tab shows which page it is reading.

### Fixed
- The compass follows the camera direction instead of the player's facing, so it stays correct with the orbital camera.
- The emerald price prompt in the trade market is now recognized, so the chat channel no longer sends your price as a party or guild command.

## 1.3.0 - 2026-09-30

### Added
- Spell Bar HUD element: shows your class's four spells with their keybind or click combo and mana/health cost, in four selectable layouts (row list, tiles, arc, focus cross) with class-specific spell icons and a boxed key-cap style for every hotkey. It only highlights the spell you actually have queued or just cast, shows each spell's true base cost rather than an inflated repeat-cast cost, and is fully integrated with the HUD designer.
- Quick-cast keybinds fire a spell without manually clicking the combo. They turn your character to face the camera's current aim (same as a manual cast), play cleanly alongside auto-attack instead of fighting it, and queue up to three casts in a row.
- A Totem tracker HUD element times your placed totems.
- A blocker warns before you open a loot chest that could contain an unlocked mythic.
- A Pouch panel in the inventory adds emerald pouch support and a command shortcut dropdown.
- The Character page shows the full Combat Information stat block (health, effective health, elemental defences, damage, Convergence).
- A Party buff tracker HUD element.
- A Powder special status HUD element.
- The souls camera zoom is now smooth.
- The HUD designer previews every element, including mount pickup and spell cast.

### Fixed
- Weapon animations no longer pop when one swing or spell interrupts another mid-blend.
- Fixed self-intersecting arms and head in several spell animation poses.
- Spell-cast animations trigger once per new cast instead of repeating with the action bar.
- The ultimate HUD element's bar and ready state render more reliably.

### Changed
- Weapon trail ghosts interpolate between samples for smoother trails.
- The attack-speed animation ramp is smoother, and the Charge, Dash and Haul spell poses were retuned.
- Minor visual polish to the HUD designer's drag state and style chips.

## 1.2.1 - 2026-09-26

### Fixed
- The Content Book search and refresh now work the way the Content Book itself does. They read the book's own Filter button to switch to the right category (for example Quest), rewind to the first page, and wait for every slot of a page to arrive before reading it. Tracking a quest or activity no longer fails after the book was left on another category or page, and a refresh reads every category.

## 1.2.0 - 2026-09-26

### Added
- Dungeon completions show a toast with the dungeon name, XP, emeralds and item count. The chat lines are hidden while the toast is on.
- Completing a daily objective shows a toast, and a Claim objective button appears in the inventory next to Claim weekly. It runs `/daily`.
- The equipped item comparison tooltip now works in any container, not only the custom inventory.
- The level-up toast now includes the Ability Point and stat lines (for example +1 Ability Point, +5 Maximum HP) instead of leaving them in chat.

### Fixed
- Resetting your ability tree no longer gets taken over by the custom Character screen. Only the real Character Info menu is replaced now.
- Right-clicking or left-clicking a powder onto an item in the custom inventory now applies it.
- The Content Book refresh reads every view from the first page and no longer drops activity types it did not see. Tracking an activity during a refresh resumes the refresh afterwards instead of leaving the book incomplete.
- The Gargoyle Fortress cave now has wiki data, and a missing wiki page is logged once instead of on every lookup.

### Changed
- The journal, inventory tab, mount feeder overlay and item tooltips do far less work each frame, so the custom screens stay smoother with a large Content Book.
- A failed Character or Journal open no longer keeps the old screen in memory.

## 1.1.0 - 2026-09-25

### Added
- Ultimates are now their own HUD element that you can move, scale and lock in the HUD designer. The charged icon stays on screen until the server clears it.
- Global style presets: Default, Glass, Elden Ring, Minimal and Tactical set the bar, hotbar, chat and panel styles together.
- Per-bar style overrides, set from a chip on each bar in the HUD designer, so one bar can differ from the rest.
- Layout presets: Default, Bottom bars, Top stack and Souls, plus three custom slots you fill with Save layout. Applying one overrides locks, and Undo swap restores your previous arrangement. Every built-in layout keeps the spell combo and ultimates near the crosshair.
- Dropdown menus replace cycling buttons for the style pickers, movement animation style, rarity and sound pickers, toast style, and the inventory and journal sort.
- The HUD designer has a toolbar for layouts and a collapsible help panel. Both fade while you drag an element underneath them.

### Fixed
- Quest waypoints no longer jump to a stale location when a quest stage has no wiki coordinates. The coordinates in the live task text are used instead.
- Hovering an element or tooltip no longer triggers through an open dropdown.
