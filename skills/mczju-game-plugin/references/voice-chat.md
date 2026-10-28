# Optional voice-chat integration

## Local server test prerequisites

`softdepend: [voicechat]` controls optional integration/load order; it does not download a runtime plugin. Before testing actual groups or audio locally, download and install the compatible Simple Voice Chat **Bukkit/Spigot/Paper server plugin** in the test server's `plugins/` directory alongside MGC and the child plugin. Each human tester also needs the compatible Simple Voice Chat client mod. Use the [official installation guide](https://modrepo.de/minecraft/voicechat/wiki/installation) to select the matching platform and Minecraft version; a provided Maven API dependency is not the server plugin.

Restart the test server, confirm `voicechat` and its voice service have started, and establish at least two real client voice connections. Follow the [official server setup](https://modrepo.de/minecraft/voicechat/wiki/server_setup): allow the configured UDP port through relevant firewalls/NAT and explicitly publish UDP when using Docker. Merely joining Minecraft is not a successful voice connection.

Test group creation, team isolation, modes and cleanup with those connected clients. Separately test the missing-plugin and disconnected-client fallback. A run without the soft dependency can validate graceful degradation only; it cannot establish that voice grouping/audio works. In test instructions and the completion report, state what the local server must download/install, the client prerequisite, and which checks actually ran. Other MGC soft dependencies are needed only when their corresponding integration is under test; do not require every optional plugin for a voice-only test.

## Optional voice-chat groups

Default to per-team voice groups only when a game explicitly has exactly two opposing teams. Multi-team games, free-for-all games, cooperative games, and unclear team structures use no groups unless requested. After implementing the plugin, always report whether groups are created, including an explicit statement when none are created.

MGC soft-depends on Simple Voice Chat (`voicechat`). Use `utils.VoiceGroupUtil` without importing voice-chat API types. All group mutations must run on the server thread:

```java
var groupId = VoiceGroupUtil.createGroup("example:red", playerExtList);
var customGroupId = VoiceGroupUtil.createGroup("example:blue", playerExtList,
        VoiceGroupUtil.Options.defaults()
                .withPassword("team-password")
                .withHidden(true)
                .withPersistent(true)
                .withType(VoiceGroupUtil.GroupType.ISOLATED));
groupId.ifPresent(VoiceGroupUtil::removeGroup);
customGroupId.ifPresent(VoiceGroupUtil::removeGroup);
```

`playerExtList` is a `Collection<PlayerExt>` (usually `List<PlayerExt>`); there is no dedicated PlayerExtList class. Creation returns `Optional<UUID>`, empty when the integration is unavailable/not ready or no online voice-connected players can join. Offline/unconnected players are skipped, duplicate UUIDs join once, and late connections are not automatically added. Defaults are no password, visible, non-persistent, OPEN; each invocation creates a fresh UUID even with the same name. Joining replaces the player's old group; cleanup does not restore it. NORMAL hears nearby non-group players, OPEN also lets nearby players hear the group, ISOLATED hears only group members.

`Options` is immutable; use its returned `withPassword`, `withHidden`, `withPersistent`, and `withType` values. Persistent means keeping an empty group, not saving across restarts. Hidden only hides the client list; passwords restrict manual joining, not utility-assigned membership. Blank names throw IllegalArgumentException, null arguments/members throw NullPointerException, off-thread calls, names rejected by voicechat, or creation/join cancellation by another plugin throw IllegalStateException; failed creation attempts cleanup before rethrowing.

Store returned UUIDs and call `removeGroup` on end, cancellation, abort, and child-plugin disable; clear stored IDs afterward. Removal only touches utility-created groups and disconnects their current members, including manual late joiners. It returns true on deletion or prior automatic disappearance, false when unavailable, unowned, or deletion fails. Non-persistent groups disappear when empty; MGC shutdown attempts cleanup of all remaining utility-created groups. Verify absence of voicechat, unavailable connections, group modes/options, replacement of old memberships, and cleanup on a real Paper server. The voicechat API dependency is optional/provided and must not be shaded into MGC or child plugins.

`/party voice` (also `/p voice`, existing `mgc.party` permission) lets only the party leader move the current leader and members into one new default voice group. Use `Party.getAllPlayer()` because `getMembers()` excludes the leader. The command reports unavailable voice service, no connected players, and creation failures, and broadcasts success to the party. Names use `Party-` plus the leader's Minecraft name. Each invocation creates a new group; offline/unconnected members are skipped. This is a snapshot action: new members/connections need another invocation by the leader; leaving/disbanding the party does not automatically leave voice chat. Verify leader success and non-leader rejection, help, aliases, console/non-party rejection, and voice-service/connection failures on Paper.
