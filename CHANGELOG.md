# Changelog

All notable changes are listed here. Versions follow Semantic Versioning.

## Unreleased

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
