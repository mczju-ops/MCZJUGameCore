# Timers and target selection

## Turn timers and target selection

Turn-based and card games commonly need a per-turn countdown and a way to pick another player. MGC provides `game.timer.TurnTimer` and `game.selection.PlayerTargetSelector`.

### TurnTimer

Use `TurnTimer` when a player (or the whole room) must act within a time limit and the timeout should auto-skip or hand off. It wraps `game.timer.CountdownEngine` (a pure, unit-testable state machine) with Bukkit scheduling and display.

```java
TurnTimer timer = TurnTimer.builder(20)
        .displayTo(currentPlayer, TurnTimer.DisplayMode.BOSS_BAR)
        .onTick((left, t) -> { })   // optional per-second callback
        .onComplete(() -> { /* timeout: auto-skip */ })
        .onCancel(() -> { /* cancelled early, e.g. the player acted */ })
        .buildAndStart();
```

- Supports `start / pause / resume / restart / reset / cancel`; `pause()` keeps the remaining time and `resume()` continues from it.
- Display modes: `NONE`, `ACTION_BAR`, `BOSS_BAR`, `XP`; register viewers with `displayTo`/`displayToAll`.
- The same component serves per-player turn timers and whole-room phase timers (add all players as viewers).
- Cancel every owned `TurnTimer` on end/abort/disable. `cancel()` fires `onCancel`; `reset()` restores display state without firing callbacks; the complete/cancel callbacks fire at most once.

### PlayerTargetSelector

Use `PlayerTargetSelector` to let a player pick a target from a room's players through a chest menu of player heads.

```java
PlayerTargetSelector.builder()
        .source(currentPlayer)
        .candidates(game.getPlayers())
        .filter(p -> !p.equals(currentPlayer))
        .title("<gold>选择攻击目标")
        .onSelect(target -> { /* apply the choice */ })
        .onCancel(() -> { /* closed without choosing */ })
        .build()
        .open();
```

- Candidates are filtered to online players; `excludeSource(true)` (default) removes the chooser; `filter(...)` applies additional rules.
- `onSelect` receives the chosen `PlayerExt`; by default `onCancel` fires when the menu closes without a choice or when no candidate remains.
- `requireSelection(true)` makes the choice mandatory: closing the menu reopens it on the next tick instead of cancelling.
- When skipping is allowed (the default), a barrier item labeled 跳过/取消 is placed in the bottom-right slot; clicking it closes the menu and fires `onCancel`. The layout reserves that extra slot automatically.
- `timeoutSeconds(n)` (default 0 = off) makes timeout produce a result instead of nothing: the selector randomly picks an online candidate and fires `onSelect`. Games that run their own turn clock can instead call `selectRandom()` directly and keep one timer.
- `open()` with no candidates calls `onCancel` immediately; callback guards prevent double invocation.
- The selector does not listen for disconnects. Games whose quit strategy clears the room on leave do not need per-selector disconnect handling; a candidate or chooser who is already offline is simply ignored.
- The menu bypasses `Menu`'s permission gate on purpose: an empty permission string returns false for non-OP players, so a permission-checked menu would only ever open for operators.
