package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;
import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSettingMenu;
import com.github.mczjuops.mczjugamecore.menu.AlertMenu;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MenuCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("该命令只能由玩家执行"));
            return false;
        }
        PlayerExt player = new PlayerExt(p);
        if (args.length < 1){
            // TODO 直接打开一个菜单，选择打开哪个菜单
            player.sender().warn("用法: /room <edit|list|delete> [参数]");
            return false;
        }

        return switch (args[0].toLowerCase()) {
            case "edit" -> handleEdit(player, args);
            case "list" -> handleList(player, args);
            case "delete" -> handleDelete(player, args);
            default -> {
                player.sender().warn("未知子命令：%s".formatted(args[0]));
                yield false;
            }
        };
    }

    private boolean handleEdit(PlayerExt player, String[] args) {
        if (args.length < 3) {
            player.sender().warn("用法: /room edit <gameName> <mapName>");
            return false;
        }
        String gameName = args[1];
        String mapName = args[2];

        AbstractGameRoom gameRoom = MCZJUGameCore.getGameRoomManager().getGameRoom(gameName, mapName);
        if (gameRoom == null) {
            gameRoom = MCZJUGameCore.getGameRoomManager().createGameRoom(gameName, mapName);
        }
        if (gameRoom == null) {
            player.sender().warn("无法找到或创建游戏房间，请检查游戏名是否正确");
            return false;
        }

        var gui = new GameRoomSettingMenu(player.player(), gameRoom);
        gui.open();
        return true;
    }

    private boolean handleList(PlayerExt player, String[] args) {
        if (args.length < 2) {
            player.sender().warn("用法: /room list <gameName>");
            return false;
        }
        String gameName = args[1];
        // todo 以某种形式获取并呈现，可以做成 gui
        player.sender().success("此功能待添加");
        return true;
    }

    private boolean handleDelete(PlayerExt player, String[] args) {
        var gui = new AlertMenu(player.player(), () -> player.sender().info("<gold>只是测试一下Alert菜单，还没有加删除功能！"));
        gui.open();
        return true;
    }

    // todo 优化补全
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return List.of("edit", "list", "delete");
        }
        if (args.length == 2) {
            // return MCZJUGameCore.getGameRoomManager().getAllGameRooms();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            // return MCZJUGameCore.getGameRoomManager().getAllMaps(args[0]);
        }
        return List.of();
    }
}
