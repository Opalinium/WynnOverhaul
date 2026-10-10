<h1 align="center">WynnOverhaul</h1>

<p align="center">
  A client-side Fabric mod for Wynncraft with a custom HUD, weapon animations, an entity tracker and a set of quality-of-life tools.
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.2-62b47a">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-dbd0b4">
  <img alt="Environment" src="https://img.shields.io/badge/Environment-Client-blue">
  <img alt="License" src="https://img.shields.io/badge/License-Source--Available%20NC-lightgrey">
  <a href="https://modrinth.com/mod/wynnoverhaul"><img alt="Modrinth" src="https://img.shields.io/badge/Modrinth-wynnoverhaul-1bd96a?logo=modrinth&logoColor=white"></a>
  <a href="https://ko-fi.com/opalinium"><img alt="Ko-fi" src="https://img.shields.io/badge/Ko--fi-opalinium-ff5e5b?logo=ko-fi&logoColor=white"></a>
</p>

---

## Features

### Combat
- **Hold-to-attack** that follows your weapon's attack speed (from the item lore or the attack speed attribute) with slightly randomized timing. Bows hold right-click for you.
- **Spell combo guard** pauses auto-attack and swing animations while you cast (R-L-R, or L-R-L with a bow), so it never interrupts a spell.
- **Souls camera**, a free orbit camera around your character that you can toggle and recenter.
- **Hit feedback** with weighty swing holds and recoil, layered impact sounds, slash arcs, sparks, and floating damage numbers that scale with the hit and are colored by element.

### Animations
- Full movesets for every weapon type, each with its own stance, idle, walk and sprint suite and a chain of strokes: Twinblades, Axe, Great axe, Hammer, Naginata and more, plus spell casts, sound effects and weapon trails.
- The moveset is picked from the skin model on the weapon, so a battleaxe swings like an axe and a mace like a hammer. Per-item overrides and an in-game preview are still there.
- Whole-body attacks: the lead leg plants, the hips twist into the strike, and attacks have walking and sprinting variants.
- A locomotion layer that bends limbs as characters move, with walk, swim, crawl, climb, ride, glide, eat, block, hurt and death animations, for you and other players.
- Gathering animations for pickaxes, axes, scythes and fishing rods that follow the node.
- A first-person body option, and animations defined in YAML files you can edit or extend.

### HUD
- Health, mana, resource, XP, sprint, mount energy, guild and region bars.
- Compass, ability cooldowns, potion effects, a spell bar with combo indicator, class buffs for shields, summons and puppets, quest log and dialogue.
- The HUD hides during cutscenes, over the debug screen and while the player list is open.
- Six hotbar styles: Classic, Glass, Tiles, Arc, Radial and Cross.
- Chat restyle with channel buttons and a direct message panel.
- Quest, level-up, discovery and location toasts.
- Every element can be moved and resized in the HUD designer.

### Entity tracker
- Rules match by name (contains, equals or regex) or base type, each with its own colour, range and through-walls setting.
- Track entities, loot chests and gathering nodes.
- ESP markers, a HUD list and an optional ping when a new match appears.
- Discovered chests and nodes are saved per world or server.

### Quests and the Content Book
- A searchable, sortable replacement for the Content Book, built from the live container.
- Tracking and untracking clicks the real slot, so it always matches the game.
- Tracking goes straight to the page an activity was seen on, using the container update packets to move through the book quickly.
- Quest beacon pointer that follows Wynncraft's marker entity, with wiki coordinates as a fallback, and dungeon waypoints that point at the dungeon entrances.
- A quest reference screen.

### Lootruns
- Beacon markers, path recording, saved paths, a HUD and particle cleanup.

### Mounts
- Feeding optimizer with its own HUD, tooltip parsing, pickup tracking and a settings screen.

### Inventory and items
- Custom inventory screen with item sorting.
- Custom screens for the bank, loot chests, the Item Upgrader, powders, ascension, the identifier, selling, repair and merchants, each with its own toggle.
- Identified stats show their roll as a percentage from red to aqua.
- Loot beams over dropped items.
- Tooltip that compares an item to what you have equipped.
- Price checks through wynnventory.
- Rare item alerts, shift-drag to move items, and a hotbar overscroll lock.
- Prefixes on party and friend nametags.

### Discord
- Rich Presence through the local Discord client.

## Requirements

| Dependency | Version | |
| --- | --- | --- |
| [Minecraft](https://www.minecraft.net/) | 26.2 | required |
| [Fabric Loader](https://fabricmc.net/use/) | 0.18.4 or newer | required |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.158.0+26.2 | required |
| [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) | 1.13.13+kotlin.2.4.10 or newer | required |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 20.0.1 | optional, adds a config screen entry |
| [Voxy](https://modrinth.com/mod/voxy) | 0.2.19-beta | optional, hides LOD terrain outside the Wynncraft map |
| [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) | any | optional, receives the tracked quest and discovery waypoints |
| Cinder | any | optional, receives the Wynncraft map bounds to skip LODs outside the map |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Put Fabric API, Fabric Language Kotlin and the WynnOverhaul jar in your `mods` folder.
3. Launch the game and join Wynncraft.

## Usage

Open the settings hub with the **Open WynnOverhaul Config** keybind, or through Mod Menu if you have it. From there you can configure every feature and open the HUD designer.

### Keybinds

All keybinds can be changed under Options > Controls.

| Keybind | What it does |
| --- | --- |
| Toggle Hold-to-Attack | Turns auto-attack on or off |
| Open WynnOverhaul Config | Opens the settings hub |
| Toggle Entity Tracker | Turns the tracker on or off |
| Toggle Souls Camera | Switches the orbit camera on or off |
| Recenter Souls Camera | Resets the camera behind you |
| Register Weapon for Animations | Assigns an animation style to the weapon you're holding |

### Configuration

Settings are saved to `config/wynnoverhaul.json`. Unknown keys are ignored and missing keys use their defaults, so a config from an older version keeps working.

## Building from source

Requires JDK 25.

```
git clone https://github.com/Opalinium/WynnOverhaul.git
cd WynnOverhaul
./gradlew build
```

The jar is written to `build/libs/`. The project uses Fabric Loom, Kotlin and Shadow. Minecraft 26.2 is unobfuscated, so there are no mappings to set up.

## Network access

WynnOverhaul connects to these services and nothing else. The Wynncraft wiki reference data is bundled with the mod, so the wiki itself is never contacted.

| Service | Used for |
| --- | --- |
| `api.wynncraft.com` | guild territory |
| `wynnventory.com` | market prices |
| `api.mojang.com` | checking that a direct message sender is a real player |

## Support

WynnOverhaul is free and stays free. If you like it, you can back its development on [Ko-fi](https://ko-fi.com/opalinium). It's optional and doesn't unlock anything.

## License

WynnOverhaul is source-available under a custom non-commercial license. You can share and modify it, but modified versions must stay open source under the same terms, and it can't be sold, resold or included in anything paid. See [LICENSE.txt](LICENSE.txt) for the full terms.

## Disclaimer

WynnOverhaul is an unofficial, independent project. It is not affiliated with or endorsed by Wynncraft, Minecraft, Mojang, Microsoft, Fabric, Discord or any other company or service it mentions.
