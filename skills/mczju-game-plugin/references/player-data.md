# Player access and persistence

## PlayerExt and managers

Wrap Bukkit players with `new PlayerExt(player)` when an MGC API expects framework behavior. `PlayerExt` delegates to managers and provides conveniences such as game/party lookup, display name, reset state, data lookup, and safe item giving. Do not treat it as an independent durable player model.

Access framework services through `MCZJUGameCore` getters, including game, player, room, party, item, menu, player-data, profile, and leaderboard managers. Note the historical accessor spelling `getPartymanager()` in the inspected source; verify the target version rather than correcting calls speculatively.

## Persistent player data

Extend `JsonPlayerData` for per-player JSON persistence:

```java
public final class ExampleData extends JsonPlayerData {
    public int wins = 0;
    private transientRuntimeType runtimeOnly;
}
```

In the inspected implementation/docs, public fields are persisted while private fields are excluded. Use types supported by Gson/MGC adapters; do not assume Bukkit objects such as `ItemStack` serialize correctly.

Register before lookup:

```java
MCZJUGameCore.getPlayerDataManager()
        .registerPlayerData("example:data", ExampleData.class);
```

Retrieve through `PlayerExt.getData(ExampleData.class)` or the data manager. After mutation, always call `setModified(true)`. Avoid async Bukkit access while preparing async persistence.
