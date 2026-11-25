# Author gameplay cases

The default runner discovers top-level JSON files in the child project's `mc-tests/`. Use `--case <filename-stem>` to select cases discovered there or repeat `--scenario <path>` for explicit case files. Keep fixtures in a subdirectory or use the project's existing gameplay test entrypoint if it already manages an equivalent environment.

```json
{
  "schema": 1,
  "game": "my_game",
  "room": "local_test",
  "players": ["AliceTest", "BobTest"],
  "probe": true,
  "files": {
    "plugins/MCZJUGameCore/rooms/${game}/${room}.json": "fixtures/room.json"
  },
  "prepare": ["forceload add 0 0"],
  "steps": [
    {"id": "join-a", "op": "mcc", "player": "AliceTest", "tool": "mcc_send_chat",
     "args": {"text": "/mgc join ${game}"}},
    {"id": "waiting", "op": "snapshot", "args": ["room", "${game}", "${room}"],
     "expect": {"state": "WAITING", "players": ["AliceTest"]}}
  ]
}
```

Use `game` and `room` IDs from the target plugin. Tests can use 1–32 players. Optional `world` names a directory relative to the case file to copy into the server's `world/`. `files` copies fixtures below the disposable server's `plugins/` directory, and `prepare` sends setup commands to its console before the players connect. Use console commands for fixtures, and MCC commands and interactions for behavior under test.

| Operation | Fields and behavior |
| --- | --- |
| `mcc` | `player`, `tool`, optional `args`. Call a tool from the pinned MCC's `tools/list`. If an action may fail as part of the test, use `allowActionFailure` and separately assert the observed outcome. |
| `expect_mcc` | `player`, `tool`, optional `args`, `expect`. Poll a read-only MCC tool until the result matches. |
| `chat` | `player`, `contains`. Wait for text in that client's chat history. |
| `paper_log` | `contains`. Wait for a substring on a Paper log line, including output from the child plugin or a scenario-specific listener. |
| `bind_turn` | Two `players` and a unique chat substring in `contains`. Bind the player who received it and the other player as `${first}` and `${second}`. Optional `first` and `second` change the variable names. |
| `disconnect`, `reconnect` | `player`. Stop or start that player's MCC session. |
| `snapshot` | `args` and `expect`. Supported subjects are `["room", game, room]`, `["player", name]` and `["block", world, x, y, z]`. |
| `mark` | `name`. Record the probe's current event sequence. |
| `event` | `expect`, optional `after` mark and exact `count` (default 1). Entries support `game_start`, `game_join`, `game_result`, `join`, `quit`, `profile`, `block_place`, `interact` and `inventory_click`. For absence or duplicate assertions, first wait for an observable end condition such as the room returning to `READY`. |

Objects in `expect` match by subset. Lists compare exactly. Polling operations accept `timeout` in seconds, default 10. Tool names and input schemas come from the running MCC release. Use read-only tools for polling and use explicit observations to confirm the effect of a click or other action. Result evidence is stored per step in the report.

Use separate scenarios for meaningful variants such as completion, interruption and reconnect. Add assertions for the child game's own mechanics, such as an invalid placement, a turn transition, the resulting world state or the announced winner. Test cases may query the child plugin's public commands and visible outputs. Rendered visuals, actual sound playback and behaviors that the headless client cannot reproduce require a graphical client.

For an existing server, define `existingReady` and `existingSteps` with player-side operations and observations. Pin the installed version in a readiness check if it is available via a player command. Local probe and console steps apply in the disposable server mode.
