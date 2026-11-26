package com.github.mczjuops.mgctest;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.event.GameResultEvent;
import com.github.mczjuops.mczjugamecore.event.GameStartEvent;
import com.github.mczjuops.mczjugamecore.event.PlayerGameJoinEvent;
import com.github.mczjuops.mczjugamecore.event.PlayerProfileLoadedEvent;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Records observable Paper and MGC state for a disposable local test server. */
public final class TestProbe extends JavaPlugin implements Listener {
    private final Gson gson = new Gson();
    private long sequence;

    /** Registers the read-only console command and event listeners. */
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        emit("ready", Map.of());
    }

    /**
     * Reads a room, player or block snapshot on the server thread.
     *
     * @param sender command sender, which must be the local server console
     * @param command registered command
     * @param label name used to dispatch the command
     * @param args snapshot request identifier, target kind and target coordinates or identifiers
     * @return whether the command was handled
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (sender != Bukkit.getConsoleSender()) {
            return true;
        }
        if (args.length < 4 || !args[0].equals("snapshot")) {
            return false;
        }
        Map<String, Object> snapshot;
        try {
            snapshot = switch (args[2]) {
                case "room" -> roomSnapshot(args);
                case "player" -> playerSnapshot(args);
                case "block" -> blockSnapshot(args);
                default -> throw new IllegalArgumentException("Unknown snapshot type");
            };
        } catch (IllegalArgumentException exception) {
            snapshot = new LinkedHashMap<>();
            snapshot.put("error", exception.getMessage());
        }
        snapshot.put("request", args[1]);
        emit("snapshot", snapshot);
        return true;
    }

    private Map<String, Object> roomSnapshot(String[] args) {
        requireLength(args, 5);
        var manager = MCZJUGameCore.getGameManager();
        var room = MCZJUGameCore.getGameRoomManager().getGameRoom(args[3], args[4]);
        Map<String, Object> fields = new LinkedHashMap<>(gameFields(manager.getGame(args[3], args[4])));
        fields.put("game", args[3]);
        fields.put("room", args[4]);
        fields.put("registered", room != null);
        if (room != null) {
            fields.put("roomState", room.getState().name());
        }
        return fields;
    }

    private Map<String, Object> playerSnapshot(String[] args) {
        requireLength(args, 4);
        Player player = Bukkit.getPlayerExact(args[3]);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("player", args[3]);
        fields.put("online", player != null);
        if (player != null) {
            PlayerExt extension = new PlayerExt(player);
            fields.putAll(gameFields(extension.getGame()));
            fields.put("mode", player.getGameMode().name());
            fields.put("hand", player.getInventory().getItemInMainHand().getType().name());
            if (extension.getParty() != null) {
                fields.put("party", extension.getParty().getAllPlayer().stream().map(PlayerExt::getName).sorted().toList());
            }
        }
        return fields;
    }

    private Map<String, Object> blockSnapshot(String[] args) {
        requireLength(args, 7);
        World world = Bukkit.getWorld(args[3]);
        if (world == null) {
            throw new IllegalArgumentException("World is unavailable");
        }
        int x = Integer.parseInt(args[4]);
        int y = Integer.parseInt(args[5]);
        int z = Integer.parseInt(args[6]);
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            throw new IllegalArgumentException("Block is outside a loaded chunk or world height");
        }
        Block block = world.getBlockAt(x, y, z);
        return new LinkedHashMap<>(blockFields(block));
    }

    private void requireLength(String[] args, int count) {
        if (args.length != count) {
            throw new IllegalArgumentException("Wrong number of snapshot arguments");
        }
    }

    private Map<String, Object> gameFields(AbstractGame game) {
        if (game == null) {
            return Map.of("active", false);
        }
        return Map.of("active", true, "game", game.getId(), "room", game.getGameRoom().getRoomName(),
                "state", game.getState().name(), "players",
                game.getPlayers().stream().map(PlayerExt::getName).sorted().toList());
    }

    private Map<String, Object> blockFields(Block block) {
        return Map.of("world", block.getWorld().getName(), "x", block.getX(), "y", block.getY(),
                "z", block.getZ(), "material", block.getType().name());
    }

    /**
     * Records a player joining the Paper server.
     * @param event completed Paper login
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        emit("join", Map.of("player", event.getPlayer().getName()));
    }

    /**
     * Records a player leaving the Paper server.
     * @param event Paper player disconnect
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        emit("quit", Map.of("player", event.getPlayer().getName()));
    }

    /**
     * Records that MGC finished loading a player profile.
     * @param event player profile loaded by MGC
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfile(PlayerProfileLoadedEvent event) {
        emit("profile", Map.of("player", event.getPlayer().getName()));
    }

    /**
     * Records an active game and its roster after starting.
     * @param event successful MGC game start
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(GameStartEvent event) {
        emit("game_start", gameFields(event.getGame()));
    }

    /**
     * Records a player joining an already running game.
     * @param event successful admission to a running MGC game
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameJoin(PlayerGameJoinEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>(gameFields(event.getGame()));
        fields.put("player", event.getPlayer().getName());
        emit("game_join", fields);
    }

    /**
     * Records a final outcome reported to MGC.
     * @param event final outcome as reported to MGC
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResult(GameResultEvent event) {
        var player = Bukkit.getOfflinePlayer(event.getPlayerId());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("game", event.getGameId());
        fields.put("uuid", event.getPlayerId().toString());
        fields.put("player", player.getName());
        fields.put("outcome", event.getOutcome().name());
        emit("game_result", fields);
    }

    /**
     * Records a block placement attempt and whether it was cancelled.
     * @param event attempted block placement, including cancelled placements
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>(blockFields(event.getBlockPlaced()));
        fields.put("player", event.getPlayer().getName());
        fields.put("cancelled", event.isCancelled());
        emit("block_place", fields);
    }

    /**
     * Records the item and block involved in a player interaction.
     * @param event Paper player interaction with an item or block
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("player", event.getPlayer().getName());
        fields.put("action", event.getAction().name());
        fields.put("useItem", event.useItemInHand().name());
        if (event.getItem() != null) {
            fields.put("item", event.getItem().getType().name());
        }
        if (event.getClickedBlock() != null) {
            fields.putAll(blockFields(event.getClickedBlock()));
        }
        emit("interact", fields);
    }

    /**
     * Records an inventory click and its raw slot.
     * @param event Paper inventory click including the raw slot
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        emit("inventory_click", Map.of("player", event.getWhoClicked().getName(),
                "slot", event.getRawSlot(), "action", event.getAction().name(), "cancelled", event.isCancelled()));
    }

    private synchronized void emit(String type, Map<String, ?> fields) {
        Map<String, Object> entry = new LinkedHashMap<>(fields);
        entry.put("type", type);
        entry.put("seq", ++sequence);
        getLogger().info("MGC_TEST " + gson.toJson(entry));
    }
}
