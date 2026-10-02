# Changelog

All notable changes are listed here. Versions follow Semantic Versioning.

## Unreleased

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
