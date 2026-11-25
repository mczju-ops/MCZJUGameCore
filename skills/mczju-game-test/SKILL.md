---
name: mczju-game-test
description: Install a local Minecraft test environment and run live Paper gameplay tests for minigames built on MCZJUGameCore. Use when asked to install MCC or its testing dependencies, verify an MGC child plugin in Minecraft, or run, author, or debug a child project's gameplay scenarios.
---

# Test an MGC minigame in Minecraft

1. Identify the target child project and the gameplay affected by the change. Read its build, `plugin.yml`, room settings and relevant gameplay code. Determine which players, game ID, room, map and dependencies the test needs.
2. For setup requests, detect the host and run `scripts/setup.sh` on Linux, WSL or macOS, or `scripts/setup.ps1` on native Windows. These install missing tools in a user-local cache. Read the reported Python path for later commands. Windows is adapted and pending runtime testing. See [setup and operation](references/setup.md).
3. For gameplay changes, prefer the target project's documented test entrypoint or matching `mc-tests/*.json` cases. Read their setup and assertions. When none fit, write a temporary scenario covering the changed behavior and run it with `--scenario`. Use the actual gameplay entrypoints and inputs. Derive expected outcomes from the plugin's rules. See [scenario format](references/scenarios.md).
4. Start a disposable local Paper instance through `scripts/mgc_test.py run --project <child-project>`. The runner packages the target source, launches MCC players, executes the scenarios, captures evidence and shuts down its processes. Use explicit `--server` and test accounts only when the user asks to use an existing server. Record EULA acceptance with `--accept-eula` only after the user explicitly accepts it.
5. Check `report.json` and the corresponding logs. Investigate setup and scenario errors, rerun after correcting them, and report the outcome for each case with its evidence. Identify any behavior that still needs a graphical Minecraft client or an unavailable dependency.

The included [Wordle cases](assets/wordle) use the real `mc-wool-wordle` plugin and illustrate two-player turn detection, legitimate block placement, invalid and valid submissions, win/loss, and interruption. The probe is bundled as a separate Paper plugin for test servers. Use it when the player-visible MCC data cannot establish the expected outcome.
