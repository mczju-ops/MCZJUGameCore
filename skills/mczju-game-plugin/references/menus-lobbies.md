# Menus, categories and lobbies

## Menus

Extend `Menu`, call `super(player, args)`, and implement `setup()`, title, rows (1-6), and permission. Populate slots with `setSlot(slot, display)` or `setSlot(slot, display, action)`. Use `refresh()` after state changes; override `handleClose()` only when cleanup is required.

Open directly with `new ExampleMenu(player).open()`. If MGC's `/menu` command must construct it, register it using the actual `MenuFacade` API in the target version. The documentation shows a static `MenuFacade.registerMenu(...)`, while inspected sources may expose the facade through `MCZJUGameCore.getMenuFacade()`; resolve this discrepancy from the dependency source before coding.

Use `AlertMenu` for confirmation of impactful actions, but keep the confirmed action scoped and revalidate state inside its callback.

### Main menu game categories

MGC reads `game-categories` from its `config.yml`: each stable category ID maps to a MiniMessage `name`, a list of MiniMessage `lore` lines, and an optional Bukkit Material `icon` (e.g. `GRASS_BLOCK` or `DIAMOND_SWORD`). Icons are used in the main menu, category assignment menu, and category lobby settings. Missing icons default to `CHEST`; invalid, air, or non-item materials log a warning and fall back to `CHEST`. With zero or one category the main menu displays all games; with multiple categories it displays categories first, then the selected category's games. Category lists and per-category game lists use the existing 27-slot layout without pagination. Without categories, the full game list also retains the 27-game limit.

Administrators with `mgc.dev` use `/mgcop category`: choose a registered game in a chest menu (45 games per page), then choose an existing category in a non-paginated chest menu. Successful saves send feedback and return to the original game-list page. Assignments persist by game ID in MGC's `game-categories.yml`; child plugins need no `GameMeta` or registration changes. Unassigned games and assignments to deleted categories resolve to the first configured category. Keep each category within 27 games, including fallback games.

`MCZJUGameCore.getConfigManager().getGameCategories()` returns the ordered, read-only category map (`GameCategory(name, lore, icon)`, with `icon()` returning `Material`; the two-argument constructor keeps the default chest icon). `getGameCategory(gameId)` returns the effective category ID or null when no categories exist. `setGameCategory(gameId, categoryId)` must run on the server thread and returns false if the game/category is invalid or persistence fails; failed saves keep the previous assignment.

### Category lobbies

`/mgcop lobby` configures the main lobby, all configured category lobbies, and all registered game lobbies in one chest menu (45 entries per page). Category entries reuse their MiniMessage name/lore; left click selects a location, right click removes it. Permissions and category existence are rechecked before saving selected locations.

`/lobby <category_id>` teleports to that category's lobby. Game IDs take priority when names collide; `/lobby category:<category_id>` explicitly selects a category, and `category:` is reserved for category destinations. Suggestions include game and category IDs. The no-argument form targets the main lobby and keeps `mgc.lobby` permission. All forms allow players outside games or in `WAITING` only; `STATING`, `RUNNING`, and `END` are rejected. Waiting players retain game membership and their current profile when teleported. Successful teleports send feedback.

Use `MCZJUGameCore.getLobbyManager().getCategoryLobby(categoryId)` (cloned location or null), `setCategoryLobby(categoryId, location)` (existing category, loaded world, server thread; boolean save result), and `removeCategoryLobby(categoryId)` (boolean indicating successful removal and save). Failed saves retain previous category locations and do not report success in the menu. Category IDs are case-sensitive as configured. Category locations persist separately in `category-lobbies.json`; existing main/game locations stay in `lobbies.json`. Deleted categories are not selectable or teleportable; retained locations become usable if the same ID is configured again. Category lobbies do not replace individual game lobby locations.

### Lobby persistence and world availability

Lobby files retain world names and coordinates without resolving worlds at startup. `getLobby`, `getMainLobby`, `getGameLobby`, and `getCategoryLobby` return a fresh location or null when the destination world is unloaded; records survive and become usable when the world loads. `hasLobby` and `getConfiguredLobbyIds` describe configured records, including unloaded worlds.

If either lobby JSON file fails to load (including empty files, malformed JSON, or invalid locations), writes to that file are blocked. The other file remains independently writable. Main/game `setLobby` retains its void signature and throws `IllegalStateException` on persistence failure; removal and category setters return false on failure. Failed writes retain previous in-memory settings and menus report failure. Saves use a completed temporary file followed by an atomic replacement where supported, with a normal replacement fallback. Legacy `config.yml` `lobby-spawn` migrates only when `lobbies.json` does not exist, never for an existing empty object or unreadable file.

Lobby setters/removers persist immediately; shutdown does not save lobby snapshots. Public `save()` skips unchanged data, so manual file edits made while the server runs survive a restart. Before an actual mutation is written, disk contents must match the last loaded/saved contents; external modification, creation, or deletion causes the operation to fail without overwriting the file. Startup logs report the absolute storage path and loaded record count. Missing files are not automatically created as empty configurations.
