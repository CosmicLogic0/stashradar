# Stash Radar

**Where did I put my diamonds?** Press **K**, type an item, and Stash Radar outlines every nearby chest, barrel and shulker box that has it.

- **Forge + NeoForge** (multiloader, same layout as LogicLib). Minecraft **1.21.1** first; the other versions follow once LogicLib is ported to them.
- **Server-checked:** the search runs on the server and reads the real container contents in loaded chunks (it never loads chunks). The client only sends `/find <item> <range>`, so it can't be used to peek at anything.
- **Search screen** (default key **K**): type part of a name, pick from the suggestions with item icons, choose the range.
- **Works for players without the mod:** `/find <item> [range]` with vanilla tab-complete; outlines are particles sent only to the searching player.
- Looks one level into shulker box and bundle items. Configurable range, max range, cooldown, permission level, container list (modded containers too), highlight time/color and outline limit: `config/stashradar.json`.
- Needs **LogicLib**.

The Paper plugin is in [stash-radar-paper](https://github.com/CosmicLogic0/stash-radar-paper).

## Commands
| Command | Who |
|---|---|
| `/find <item> [range]` | everyone (`permissionLevel` in the config) |
| `/stashradar reload` | operators |
| `/stashradar selftest` | server console only (used by CI) |

## Building
Java 21. Put LogicLib's released jars in `libs/` (CI downloads them from LogicLib's `dist` branch), then `gradlew build`.

## Testing (automatic, every push)
`.github/workflows/build.yml` (copied from LogicLib):
1. builds both jars,
2. starts a real **dedicated server** on Forge and NeoForge with LogicLib + Stash Radar and runs `stashradar selftest`, which places real containers next to spawn, fills them, runs the real search and checks the answers,
3. starts the real **game client** headless on both loaders and joins a world,
4. only then prints the tested jars.
