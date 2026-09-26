# Changelog

All notable changes are listed here. Versions follow Semantic Versioning.

## Unreleased

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
