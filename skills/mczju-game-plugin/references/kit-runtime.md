# Kit seats, logic threads and lifecycle

Package prefix: `com.github.mczjuops.mczjugamecore.game.kit`. Read for sequential prompts or human/bot state; kit does not manage MGC membership.

## Seats

Implement `seat.Seat` in the game's per-seat state: `name()`, `isBot()`, `isOnline()`, and nullable `ext()` (`PlayerExt`). Bots return null from `ext()`; default `player()` derives the Bukkit player, and default `isActive()` accepts bots or online humans. Override activity when elimination or another rule requires it. Keep bots as valid seats without inventing Bukkit players.

`SeatOrder<P extends Seat>` snapshots a collection and exposes `all`, `size`, `isEmpty`, `indexOf`, wrapping `at`, `next`, `previous`, circular `distance`, and `after`. These describe seating, not turn eligibility or winning priority; filter inactive seats and implement priority in the game.

## GameThread

`thread.GameThread(plugin, name)` runs `start(Runnable)` on a virtual thread. Start one logic runner per round; do not start it repeatedly to advance turns. `checkAlive()` and interrupted waits throw `GameAbortedException`; the runner catches that exception and logs other failures. It does not end/abort the MGC game or clean up its resources automatically.

- `call(Supplier<T>)` schedules work on the server thread and blocks for its result; `run(Runnable)` is its void counterpart. Use these for Bukkit world/player/inventory/scoreboard operations from logic code. They reject server-thread invocation and require a running game thread.
- `runLater(Runnable, ticks)` schedules without waiting; it checks the runner is still active when executing. Still recheck exact game-instance membership/phase in the callback.
- `stop()` marks the runner stopped and interrupts its wait. It does not join the runner, close its menus, or cancel all already queued Bukkit operations. An already scheduled `call` body can still execute; guard its effects on the server thread.
- `runOnMainSafely(plugin, task)` runs immediately on the server thread or schedules when enabled; it is not a lifecycle/membership guard.

Keep logical game state owned by one thread where possible. Publish immutable snapshots or use suitable synchronization for values accessed from both logic and server callbacks (for example, current-turn suppliers). A virtual thread does not make Bukkit APIs safe for async access.

## Minimal composition

Imports come from `kit.thread`, `kit.clock`, and `kit.prompt`. Here `plugin` is the child plugin, `codec` is its `HandItemCodec<Card>`, and the `playRound` body must use the thread bridge for Bukkit operations.

```java
GameThread logic = new GameThread(plugin, "example_round");
PromptTimer promptTimer = new PromptTimer(plugin, logic);
MenuInteraction<Card> interaction = new MenuInteraction<>(plugin, logic, codec)
        .withPromptTimer(promptTimer);
// Optional: interaction.setBotAnswerer(gameSpecificBot);
// Optional: promptTimer.bindCurrentTurn(game::currentSeat);
logic.start(() -> playRound(logic, interaction));
```

There is no `withNotifier` method. Finish or abort through MGC's game manager on the server thread, never by invoking lifecycle hooks. Unexpected runner failure must have an explicit game-level abort path; logging alone leaves MGC membership and room state intact.

## Terminal paths

Make end/cancel/abort/init-failure/child-plugin-disable cleanup idempotent. Stop the runner, cancel tracked `Pending` operations and close only owned prompt menus, call `interaction.cancelPromptTimers()`, stop owned action clocks, cancel HUD refresh tasks, detach sidebars, and discard seat/hand runtime records as applicable. Do this directly on the server thread or through the scheduler as appropriate; do not call `logic.run(...)` after `logic.stop()`.

`cancelPromptTimers()` only clears prompt display timers; it does not cancel pending answers. Track low-level `Pending` handles when a phase needs explicit cancellation. Interruption can bypass normal prompt completion, so cleanup must also cover that path. Avoid closing an unrelated inventory after a player joins another game. Profile restoration remains owned by MGC; do not overwrite a restored lobby inventory during late cleanup.
