# Install and run Minecraft gameplay tests

When asked to set up the environment, execute the installer for the current host. The Unix entrypoint requires `sh`, `curl` and `tar` when Python needs bootstrapping. Native Windows uses PowerShell and Windows `tar.exe`.

```sh
./skills/mczju-game-test/scripts/setup.sh
```

```powershell
& .\skills\mczju-game-test\scripts\setup.ps1
```

The installer reuses compatible Python 3.12+, JDK 25 and Maven, or installs portable copies. It installs the checksum-verified MCC release `20260929-519`, Paper 26.2 build 121 and Paper's matching Mojang bootstrap JAR. Downloads and tools use a user-local cache. Its location defaults to `$XDG_CACHE_HOME/mgc-game-test` (or `~/.cache/mgc-game-test`), `~/Library/Caches/mgc-game-test` on macOS, and `%LOCALAPPDATA%\mgc-game-test` on Windows. Set `MGC_TEST_CACHE` to relocate the installer and runner together. For Windows the implementation is adapted and the runtime status is **pending testing**.

The installer prints the Python executable to use. Resolve the path to `mgc_test.py` inside the installed skill if working in a child repository.

```sh
python3 /path/to/mczju-game-test/scripts/mgc_test.py run --project /path/to/minigame --case play-round
```

To run the bundled cases, point `--project` at the `mc-wool-wordle` checkout and pass
`--scenario /path/to/mczju-game-test/assets/wordle/wordle-win.json` or
`--scenario /path/to/mczju-game-test/assets/wordle/wordle-interrupted.json`.

The first local run requires acceptance of the [Minecraft EULA](https://aka.ms/MinecraftEULA). After explicit user acceptance, include `--accept-eula` on `setup` or `run`. Acceptance is stored in the chosen local cache.

The default build packages a Maven child project under JDK 25, identifies the freshly built plugin JAR and resolves the MGC dependency from the Maven classpath. Use `--artifact` when the package has multiple outputs. Projects with other build systems may use their normal build, then call the runner with `--plugin-jar` and a matching `--mgc-jar`. Repeat `--dependency` for other provided plugin JARs. Use `--maven-arg=-o` when the Maven dependencies are already cached, and `--maven-arg=-Dkotlin.compiler.daemon=false` if the sandbox cannot write Kotlin's daemon directory.

Optional local overrides include `--mcc`, `--paper`, and `--probe-jar`. When changing the Paper version, first verify that the target plugin, MGC, probe and selected MCC build agree on Minecraft and Paper compatibility.

Local Paper has an independent world and plugin directory per case. The runner uses its own stdin console and two or more separate MCC processes as required by the case. For each run, inspect `report.json`, `build.log` if Maven ran, and the individual `paper.log`, `mcc-*.log` and `probe.jsonl`. Exit codes are 0 for all passed, 1 for at least one gameplay failure, and 2 for blocked validation.

## Existing server

Use `--server host:port --accounts /private/path/accounts.json` only on explicit request. The scenario supplies `existingReady` checks for the installed plugin version, test room or an equivalent readiness signal, and `existingSteps` for the player actions and assertions. Use accounts and rooms authorized for testing. The runner controls the MCC clients it starts. Server preparation, plugin deployment and lifecycle are separate operations.

```json
{
  "AliceTest": {"mode": "microsoft", "login": "test-account@example.com"},
  "BobTest": {"mode": "offline", "login": "BobTest"}
}
```

Account keys match case aliases. Microsoft login may need device authorization. Open the per-player private MCC log when prompted and complete device sign-in. Keep authentication logs, caches and test account files private. Use offline accounts only on an explicitly designated offline test server.
