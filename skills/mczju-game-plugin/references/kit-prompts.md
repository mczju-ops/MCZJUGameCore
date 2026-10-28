# Kit prompts and menus

Packages: `game.kit.prompt` and `game.kit.menu`. Read [kit-runtime.md](kit-runtime.md) for thread ownership. Call blocking prompt APIs from game logic, never the Bukkit server thread.

## One answer

`Interaction<T>` provides `askTarget`, `askItem`, `askChoice`, `askConfirm`, and `askNumber`, each returning `Pending<R>`. Their `askXxxSync` counterparts wait and apply timeout/cancellation policy through `Prompts`. `MenuInteraction<T>(plugin, gameThread, codec)` supplies the GUI implementation. Prefer a live `GameThread` to marshal GUI work; passing null does not make blocking waits safe on the server thread.

```java
String action = interaction.askChoiceSync(seat, List.of(
        new ChoiceOption("跳过", "pass"),
        new ChoiceOption("响应", "respond", "执行响应", Material.LIME_CONCRETE, true, 10)),
        AskOptions.standard("选择行动", 5)
                .onTimeout(AskOptions.TimeoutFallback.FIRST)
                .build());
// null means cancellation/no usable result unless onCancel throws.
// Revalidate the round, seat and action before applying game effects.
```

`AskOptions` also controls yes/no labels, hint, notes, `groupMode(BY_KEY/NONE)`, `showPromptTimer`, and `onCancel(Runnable)`. `BY_KEY` combines equivalent items using the codec key. Explicitly choose the fallback for the rule; do not let a default random response decide a claim that should pass.

## Actual result boundaries

| Prompt | No answer / timeout | Esc cancellation when onCancel does not throw |
| --- | --- | --- |
| Target/item | RANDOM chooses a candidate, FIRST chooses first; other policies return null; empty pool returns null | null |
| Choice | RANDOM/FIRST over enabled options only; other policies or no enabled options return null | null |
| Confirm | false regardless of timeout policy | false |
| Number | max only for MAX, otherwise min (including REJECT/RANDOM/FIRST) | min |

`onCancel` runs on the waiting logic thread and may throw a game-specific cancellation exception. It is distinct from timeout; define whether it means pass, retry, or roll back in the game. `REJECT` is not a universal nullable result for the primitive-returning confirm/number APIs.

`setBotAnswerer(BotAnswerer)` supplies `<R> R answer(AskRequest<R>)`. Inspect `kind()` (TARGET, ITEM, CHOICE, CONFIRM, NUMBER) and return the correct result type from the request's candidates/pool/choices/range. Choice answers are option values, not labels. Bots return an already completed pending without GUI; null (or a failed bot strategy) is left to the high-level fallback. Kit does not validate bot strategy legality for the game.

## Concurrent answers

```java
Map<MySeat, String> answers = MultiAsk.askAll(responders,
        seat -> interaction.askChoice(seat, choicesFor(seat), options), 5);
```

Use low-level `askXxx`, not blocking `askXxxSync` in this factory. `MultiAsk` opens all questions before waiting, shares one collection deadline, returns only non-null answers, and closes the pending menus afterward. It does not run `Prompts`' high-level fallback/onCancel logic or decide priorities. Missing seats are handled by the game's rule (for example, pass). Keep per-prompt timeouts consistent with the collection window. Cleanup on interruption still belongs to the game.

## Pending and GUI boundaries

`Pending` provides `complete`, `cancelNow`, `isDone`, `isCancelled`, `owner`, `withTimeout`, `onTimeout`, `onComplete`, `await/awaitMillis`, and `forceClose`. Complete/cancel on the server thread; waits belong off-thread. `withTimeout` arranges task expiry, while awaiting with a deadline alone is not a substitute for disposing of the pending operation. `onComplete` is a single callback slot, not a listener list: `PromptTimer` uses it, so do not overwrite it when composing cleanup.

`KitMenu` bypasses the normal menu permission gate for in-game choices. `ChoiceMenu` sorts by `ChoiceOption.order`; disabled options remain visible and cannot be clicked. `ConfirmMenu`, `NumberMenu`, `ItemPickMenu<T>` and `SeatPickMenu` render the other prompt types. Closing cancels the pending; timeout belongs to `Pending`, not a second menu clock. Normal players must be able to use these menus, but the game must still enforce participant/phase/ownership rules. Do not substitute the administrative `AlertMenu` contract for a gameplay confirmation prompt.
