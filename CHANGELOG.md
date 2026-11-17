## Stash Radar 0.1.0: first release

"Where did I put my diamonds?" Search nearby containers for an item and see which ones have it.

- **Search screen** (key **K**): type part of an item name, pick it from the suggestions with icons, choose a range.
- **`/find <item> [range]`**: works for every player, vanilla clients included, with tab-complete.
- **Server-checked:** the server reads what's really inside chests, trapped chests, barrels and shulker boxes in loaded chunks. Nothing comes from the client, so it can't be used to peek.
- Looks one level into **shulker box items** (and bundles on newer versions).
- **Per-player outlines:** only the searcher sees the green particle boxes, plus a chat summary with the count, the nearest distance and direction.
- **Config** (`config/stashradar.json`): range, max range, cooldown, permission level, which containers (modded ones too), highlight time and color, outline limit. `/stashradar reload` for operators.
- Requires **LogicLib** 0.1.0 or newer.
