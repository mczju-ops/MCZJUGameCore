# Wiki links and core configuration reload

### Optional game Wiki links

Core provides `/mgcop wiki` for administrators to configure one optional HTTP/HTTPS URL per registered game ID (left click to edit in a dialog, right click to remove). Child plugins do not need room parameters or their own Wiki command. Players use `/mgc wiki` for the MainMenu Wiki mode or `/mgc wiki <gameId>` for a Wiki dialog with an Open Wiki button using client OPEN_URL (no chat interaction required); unconfigured games display a clear message. Category navigation preserves Wiki mode, and game clicks do not join/teleport.

`MCZJUGameCore.getGameWikiManager()` exposes `getLink(gameId)` (nullable), `setLink(gameId, url)` (null/blank removes, false on persistence failure, invalid URL throws IllegalArgumentException), and `sendLink(player, gameId)` (schedules the Wiki dialog for the next tick; false for missing games/links). Mutations require caller authorization and the server thread. Configuration is stored in `game-wikis.json`. Existing `mgc.mgc`/`mgc.dev` permissions apply.

Core automatically reminds each successfully joined player in chat when the game has a Wiki and that player's game-specific reminder has not been dismissed. Includes party members and mid-game joins; next-tick membership validation skips failed joins. The chat OPEN_URL button opens the Wiki directly; the one-use opt-out callback (valid for one day) persists game IDs in the player's `mczjugamecore:hidden_wiki_reminders` PDC string list and points to `/mgc wiki`. Dismissing one game's reminders never blocks manual Wiki access or another game's reminders. `GameWikiManager.remindOnJoin(player, gameId)` is the server-thread reminder API; the default player manager invokes it automatically.


### Configuration reload commands

| Command | Purpose |
| --- | --- |
| `/mgcop reload` | Reload all core configuration and all games' rooms |
| `/mgcop reload config` | Reload core config.yml, including category definitions |
| `/mgcop reload categories` | Reload game-category assignments from game-categories.yml |
| `/mgcop reload wiki` | Reload Wiki links from game-wikis.json |
| `/mgcop reload lobby` | Reload lobby locations from lobbies.json and category-lobbies.json |
| `/mgcop reload game <gameId>` | Reload only the selected game's rooms, excluding child-plugin config.yml |
