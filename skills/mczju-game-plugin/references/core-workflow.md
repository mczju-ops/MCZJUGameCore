# Core game and room workflow

## Contents

- Project wiring
- Choosing a base game class
- Lifecycle and strategies
- Rooms
- Registration
- Common implementation pattern

## Project wiring

Configure the child plugin to load after MGC:

```yaml
depend: [MCZJUGameCore]
```

Use the target project's resolved versions. A Maven dependency normally uses coordinates `com.github.mczju-ops:MCZJUGameCore` with `provided` scope, alongside Paper API with `provided` scope and the required JitPack/Paper repositories.

## Choosing a base game class

- `AbstractGame`: general round-based game. Implement metadata, initialization, start/cancel/abort/end hooks, and a wait strategy. Review death and quit defaults; override them when the game rules differ.
- `SinglePlayerGame`: round-based, exactly one player, automatic immediate start. Use `getPlayer()` where appropriate.
- `OpenSessionGame`: shared long-running session without a normal round end. Implement at least player join and quit behavior plus metadata/initialization required by its actual version. It still needs a room, normally one public room.
- `TurnBasedTwoPlayerGame`: inspect its source before use; it is more specialized and its contract may evolve.
- `MidGameJoinable`: implement only when a running round can safely accept a player. `onPlayerMidJoin(PlayerExt)` decides whether the join is accepted; initialize that player's runtime state before returning success or immediately afterward according to the resolved framework implementation.

## Lifecycle and strategies

Framework-controlled flow is broadly:

```text
register class -> allocate leisure room -> construct game -> onGameInit
-> WAITING -> wait strategy admits players -> onGameStart -> RUNNING
-> onGameEnd / onGameAbort / onGameCancel -> manager cleanup -> room READY
```

Hooks declare behavior; managers invoke them. To finish normally, call:

```java
MCZJUGameCore.getGameManager().endGame(this);
```

Use the corresponding manager methods for cancellation or abort. Prefer `forceAbortGame` only for defensive administrative cleanup after understanding its exception behavior.

Current general hooks and contracts:

- `getId()`: stable unique ID.
- `getGameMeta()`: menu display name, icon, author, and description; text uses MiniMessage conventions.
- `getGameWaitStrategy()`: admission/start rules. `DefaultGameWaitStrategy(game, maxPlayers, minPlayers)` allows manual start at the minimum and auto-start at capacity in the documented version.
- `onGameInit()`: prepare/validate the allocated room; return success.
- `onGameStart()`: begin gameplay and create plugin-owned tasks/entities.
- `onGameCancel()`: undo preparation after init failure or all waiting players leave.
- `onGameAbort()`: clean runtime state after abnormal termination.
- `onGameEnd()`: apply normal results and clean runtime state.
- `getPlayerDeathStrategy()`: default prevents normal death and restores the player; override for elimination, respawn, or loss rules.
- `getPlayerQuitStrategy()`: default can abort the entire round; override when remaining players should continue.

Make cleanup shared and idempotent when end and abort remove the same tasks/entities. Do not assume hook/manager cleanup ordering without checking the resolved implementation; for example, a manager may remove players before or after a hook depending on transition type.

## Rooms

Define one room class for the game type. Each room instance holds map-specific settings; multiple instances allow concurrent rounds.

- Extend `JsonGameRoom` for editable JSON-backed room configuration.
- Add public configuration fields for values meant to serialize/edit. Use `@FieldDescription` to explain fields in the room editor.
- Validate nullable/unset fields in `onGameInit()` before teleporting players or modifying the world.
- Use `@PlayerSelectable` on the room class only when players should choose a named room rather than accept automatic allocation.
- Keep runtime-only state out of persisted public fields.
- Do not set `GameRoomState` directly from child-plugin gameplay code.

Room instances are loaded during game registration. Administrative MGC commands create/edit/save room records; confirm exact command syntax against the installed MGC version.

## Registration

Register from the child plugin's `onEnable()` after MGC has loaded:

```java
MCZJUGameCore.getGameManager()
        .registerGame(ExampleGame.class, ExampleGameRoom.class);
```

Reflection constructs both types, so supply accessible no-argument constructors. Registration loads the game's rooms. If player data, items, menus, or leaderboards are also required, register them in a deterministic order before gameplay can begin.

## Common implementation pattern

Keep the game object as the lifecycle coordinator:

```java
public final class ExampleGame extends AbstractGame {
    private BukkitTask timer;

    public ExampleGame() {}

    @Override public String getId() { return "example:game"; }

    @Override public GameMeta getGameMeta() {
        return GameMeta.builder()
                .displayName("<gold>Example")
                .icon(Material.CLOCK)
                .author("<green>MCZJU")
                .description(List.of("<gray>Example game"))
                .build();
    }

    @Override public GameWaitStrategy getGameWaitStrategy() {
        return new DefaultGameWaitStrategy(this, 4, 2);
    }

    @Override protected boolean onGameInit() {
        ExampleRoom room = (ExampleRoom) getGameRoom();
        return room.spawn != null;
    }

    @Override protected void onGameStart() {
        // Delegate rules to services/listeners; retain handles needed for cleanup.
    }

    @Override protected void onGameCancel() { cleanup(); }
    @Override protected void onGameAbort() { cleanup(); }
    @Override protected void onGameEnd() { cleanup(); }

    private void cleanup() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
```

Adapt signatures and imports to the target's resolved MGC version rather than forcing this skeleton unchanged.
