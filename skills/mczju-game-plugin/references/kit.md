# Reusable game mechanisms (`game.kit`)

Available from MGC 1.4.0 (PR #134). Verify the resolved dependency contains `com.github.mczjuops.mczjugamecore.game.kit` before using it; older games can keep `TurnTimer` and `PlayerTargetSelector`. Kit is optional and does not replace `AbstractGame`, room registration, membership/profile managers, or lifecycle transitions.

Keep reusable mechanisms in kit and game-specific rules in the child plugin. Winning, scoring, valid moves, claim priority, bot strategy, and what cancellation means remain game rules. Do not migrate an existing callback-based game to a logic thread merely to use a sidebar or clock.

## Choose what to read

| Need | Read |
| --- | --- |
| Human/bot seats, sequential logic, cross-thread calls and cleanup | [kit-runtime.md](kit-runtime.md) |
| Ask for targets/items/options/confirmation/numbers; concurrent responses; GUI | [kit-prompts.md](kit-prompts.md) |
| Base + extra action time, prompt display, inventory hands, sidebar | [kit-state-display.md](kit-state-display.md) |

When composing blocking prompts with inventory/HUD operations, read the runtime contract and the relevant feature file. Do not load unrelated modules. The Chinese developer guide is `docs/dev-kit.md` in the MGC source repository; verify exact overloads and behavior against `game/kit/` source rather than treating examples as a complete game implementation.

## Integration checks

- Create per-round state; register child-plugin listeners explicitly. Kit is not automatically attached to an MGC game.
- GUI choices and delayed callbacks are inputs, not proof of current membership or legality. Validate the exact running game instance, participant role and target before applying results on the server thread.
- Exercise human and bot seats, timeout versus Esc cancellation, disabled options, simultaneous answers, disconnects, abort/end/disable, and concurrent rooms on Paper as applicable. Report actual checks separately from pending server verification.
