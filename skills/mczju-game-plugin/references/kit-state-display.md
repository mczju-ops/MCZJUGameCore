# Kit clocks, hands and sidebar

Read only the relevant sections. All Bukkit inventory, clock display and scoreboard operations belong on the server thread; from a `GameThread`, use `call/run` after checking round ownership.

## Clocks

| Mechanism | Use |
| --- | --- |
| `game.kit.clock.ActionClock` | A player's persistent base-time bank plus extra time refreshed each turn |
| `game.kit.clock.PromptTimer` | BossBar reminder attached to a pending question |
| `game.timer.TurnTimer` | Existing generic phase/turn timer with selectable display modes |

Create `ActionClock(plugin, nullablePlayer, mainSeconds, extraSeconds)`. It consumes extra time first, then base time; `refreshExtra(n)` replaces extra time without restoring spent base time. `start/pause/resume/stop` own its scheduler/display, `total()` gives remaining seconds, and `setOnExpire(Runnable)` installs the timeout action. Keep an action clock per seat when base time carries across turns; recreating it each turn replenishes the bank. Null owners support bots without a player display. Stop clocks on all terminal paths, and ensure expiry and prompt answers cannot both resolve one action.

`PromptTimer(plugin, gameThread)` is attached using `MenuInteraction.withPromptTimer(timer)`. Optionally `bindCurrentTurn(Supplier<? extends Seat>)` skips displaying a second timer for the current actor. It skips bots/offline players, cancels on pending completion, and supports `cancelAll`; `interaction.cancelPromptTimers()` delegates to that cleanup. A prompt display is not the action-time authority. See [kit-prompts.md](kit-prompts.md) for timeout/result semantics.

## Inventory as hand

Package `game.kit.hand`:

- Implement `HandItemCodec<T>`: `render(T)`, nullable `read(ItemStack)`, `key(T)`, and `label(T)`; override `render(T, extraLore)` when needed (the default ignores extra lore). `read` must safely reject null, unrelated and malformed stacks. Use plugin-owned persistent identifiers, not only display names.
- `key` describes logical grouping, while `render/read` preserve individual attributes. Two variants that can form one group need the same key; for example, red and ordinary fives can share a key while retaining their variant data.
- Build `HandLayout` with hand slots and reserved controls. `HandLayout.builder().autoSlots(14, 0).reserve(26).build()` is a valid example; inspect the builder for custom layouts. Main slots are 0–35, armor 36–39, offhand 40. Offhand is locked by default; hand scanning does not include it.

```java
Hand<Card> hand = new Hand<>(codec, layout, cardComparator);
// Execute on the server thread with an owned, active seat:
hand.applyLayout(seat);
hand.set(seat, initialCards);
```

`Hand` exposes scan/count/has/first, set/give/take/clear, overflow access/flush, sort, and forget. Humans use inventory slots; bots use memory. Overflow is runtime state, not a dropped-world item or persistent player data. `forget(seat)` discards bot/overflow records; it does not clear a human inventory. `clear(seat)` clears hand slots and overflow, leaving reserved slots/offhand intact. Follow [profiles.md](profiles.md): clear only at an owned game-inventory phase before core restoration, never after exit/end through cached seats. Never call profile APIs to coordinate this manually. No comparator means sorting does nothing.

Register `new HandGuardListener(layout, hand::owns, inThisGame)` in the child plugin. Its `Predicate<Player>` must resolve the exact owning game instance and allowed role/phase; matching a class or world is insufficient. The listener guards dropping, swapping offhand, inventory clicks/drags, interactions and placing owned items. The game still owns legal plays, control buttons, turn checks and isolation between rooms. Do not globally freeze all players' inventories.

## Sidebar

`game.kit.hud.Sidebar(key, miniMessageTitle)` creates one scoreboard for a round. On the server thread, use `attach(Player)`, `update(List<Component>)`, and `detach(Player)`. Limit to 15 lines; duplicate serialized line text shares a score entry, so make displayed lines distinct. Schedule refreshes in the child plugin (typically once per second); the class has no automatic refresh task.

`detach` switches to the server's main scoreboard, not a saved previous custom scoreboard. Account for other scoreboard owners and detach before another round takes ownership. It does not replace the managed Tab-name API: prefixes/suffixes still go through MGC's `TabManager`. Cancel refresh tasks and detach only this round's viewers on terminal paths.
