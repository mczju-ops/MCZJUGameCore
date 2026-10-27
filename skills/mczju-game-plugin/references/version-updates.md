# MGC and skill updates

## Check versions and update the skill

Check for updates only when the user asks to update MGC or this skill, check for updates, or determine whether either is up to date. Ordinary development and use of bundled API examples do not trigger a release check; verify API compatibility against the target project's resolved dependency and source instead.

When requested, use the official [GitHub Releases](https://github.com/mczju-ops/MCZJUGameCore/releases) to check whether a release with a newer version number exists before performing the update. Report the local version, the compared release tag, and whether an update is available. If GitHub cannot be reached or the local version cannot be identified, report that the check is inconclusive.

### Check whether MGC is current

1. Identify the child project's resolved `com.github.mczju-ops:MCZJUGameCore` dependency, including versions supplied by properties or dependency management. For Maven, use `mvn dependency:tree -Dincludes=com.github.mczju-ops:MCZJUGameCore` when needed. Separately check the server's installed MGC version using `/version MCZJUGameCore` or the packaged JAR's `plugin.yml`; compilation and runtime versions can differ. When working on MGC itself, read its project version from `pom.xml`.
2. Read the release tags and notes on GitHub. Compare numeric version components, ignoring an optional leading `v`: for example, `1.0.10` is newer than `1.0.9`. Do not compare version strings lexicographically or infer the newest version solely from publication dates. Use stable releases by default; consider prereleases only when the target intentionally uses them. Branch names, commit hashes, snapshots, and custom builds require checking their source revision and cannot be ranked by version number alone.
3. If a newer applicable release exists, report it and read its compatibility and migration notes. Checking does not itself update the project or server. When an MGC update is requested, align the dependency and server JAR with the chosen compatible release, rebuild the child plugin, and verify affected behavior on Paper.

### Check whether the skill is current

This skill is distributed in the MGC repository under `skills/mczju-game-plugin/` and currently has no independent version field. Track the release tag and, when available, commit from which the installed skill was copied; do not assume its version equals the project's dependency or server version.

1. Compare the installed skill's recorded source release with GitHub Releases. A higher release version means a newer release should be checked for skill updates.
2. Inspect `skills/mczju-game-plugin/` at that release tag and compare the complete directory with the installed copy, including `SKILL.md`, `references/`, and `agents/`. A newer MGC release may leave the skill unchanged; file comparison confirms whether the skill itself needs updating. If the tag has no skill directory, report that it does not supply this skill.
3. If the installed source tag is unknown, compare its files with the chosen release's skill directory before claiming it is current. A copy from the default branch may include unreleased APIs; identify it by commit and verify against the project's actual MGC dependency.

### Update the installed skill

1. Select a release from GitHub Releases and read its notes. Obtain the repository's **Source code (zip)** or **Source code (tar.gz)** for that tag, or check out the tag in a separate checkout. The plugin JAR is not the skill package. Use the skill directory from the selected tag rather than silently downloading the default branch.
2. Locate the installed `mczju-game-plugin` directory from the active skill entry or the user's skill installation configuration. For a standalone Codex installation, it is commonly `${CODEX_HOME:-$HOME/.codex}/skills/mczju-game-plugin`; do not mistake the repository's source copy for the active installed copy.
3. Preserve local customizations in a backup, then replace the installed directory with the complete `skills/mczju-game-plugin/` directory from the selected release. Copy all included resources together and remove obsolete upstream files so old references do not remain mixed with new instructions. Record the source release tag and commit in installation notes for future checks.
4. Reload skills or start a new session in the host application, then confirm the active skill path and compare its files with the selected release. Recheck the target's MGC API compatibility: updating the skill does not update the Maven dependency or server plugin, and examples for a newer API may require adaptation.
