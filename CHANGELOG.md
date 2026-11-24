## Stash Radar 1.1.0

### New
- **Search by name:** `/find name sword` finds every item with that text in its name, including renamed items and boxes ("Ore Box"). One click in the search screen.
- **Search by tag:** `/find #logs` finds a whole group of items (Minecraft 1.13+, so not on 1.12.2).
- **Glow through walls:** far-away results also get a glowing outline that shows through walls. Only the searching player sees it, and it works on vanilla clients too. It goes away when you get close, so the chest still opens normally.
- **Item frames:** items shown in item frames and glow item frames are found too.
- **Ender chest:** the answer also says how many you have in your own ender chest.
- **Copper chests** (Minecraft 1.21.9+) are searched by default. Older configs get them added automatically if they search chests.
- **Recent searches** in the search screen (empty box). Right-click one to remove it.
- **Repeat last search** key (not bound at first; set it in Controls).
- **Settings screen:** Mods → Stash Radar → Config, or **Settings** in the search screen (not on 1.12.2).
- `/find help` and `/stashradar help`.

### Improved
- The chat answer lists the 3 nearest containers with their coordinates and distance.
- If you ask for more than the server's max range, it tells you which range it used. "Nothing found" suggests a bigger range.
- Denser, clearer particle outline. When there are many results, the outline gets lighter so the connection stays light.
- The search screen tells you when the server doesn't have Stash Radar (or doesn't let you use `/find`).
- Tooltips on the search screen buttons, and every setting explained in the settings screen.
- Tab-complete for `/find` now also suggests tags.

### Fixed
- Minecraft 1.21.11 and 26.x: the mod's resource pack info gave an error at startup (`pack.mcmeta`), so its texts could be missing.
- Forge 1.21.11: used an old, deprecated way to register the key.
- The cooldown list kept every player who ever searched until the server restarted (a small memory leak).
- Asking for a range above the server's limit was cut down silently.

## Stash Radar 0.1.0: first release

"Where did I put my diamonds?" Search nearby containers for an item and see which ones have it.

- **Search screen** (key **K**): type part of an item name, pick it from the suggestions with icons, choose a range.
- **`/find <item> [range]`**: works for every player, vanilla clients included, with tab-complete.
- **Server-checked:** the server reads what's really inside chests, trapped chests, barrels and shulker boxes in loaded chunks. Nothing comes from the client, so it can't be used to peek.
- Looks one level into **shulker box items** (and bundles on newer versions).
- **Per-player outlines:** only the searcher sees the green particle boxes, plus a chat summary with the count, the nearest distance and direction.
- **Config** (`config/stashradar.json`): range, max range, cooldown, permission level, which containers (modded ones too), highlight time and color, outline limit. `/stashradar reload` for operators.
- Requires **LogicLib** 0.1.0 or newer.
