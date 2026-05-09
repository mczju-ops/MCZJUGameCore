package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.MGCMaterial;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class LocationSelector implements Listener {

    // 单例模式
    private LocationSelector() {}

    private static class Holder {
        private static final LocationSelector INSTANCE = new LocationSelector();
    }

    public static LocationSelector getInstance() {
        return Holder.INSTANCE;
    }

    private final Map<Player, Consumer<Location>> playerCallbackMap = new HashMap<>();

    @EventHandler
    public void onClick(PlayerInteractEvent event){
        Player player = event.getPlayer();

        var action = event.getAction();
        if (action == Action.PHYSICAL) return;

        // 不是调试棒直接返回
        if (!MCZJUGameCore.getItemManager().is(
                player.getInventory().getItemInMainHand(), MGCMaterial.DEBUG_STICK.toString())) return;

        // 没有注册回调
        if (!playerCallbackMap.containsKey(player)) return;

        event.setCancelled(true); // 防止误触发方块交互

        Consumer<Location> callback = playerCallbackMap.remove(player);

        // 右键：取消操作
        if (action.isRightClick()) {
            player.sendMessage(TextParser.parse("<yellow>已取消本次位置选择"));
            return;
        }

        Location loc;

        // 点击方块
        if (event.getClickedBlock() != null) {
            loc = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5); // 取方块中心
        } else {
            // 点击空气 -> 取玩家当前坐标
            loc = player.getLocation();
        }

        callback.accept(loc);
    }

    public void selectLocation(PlayerExt player, Consumer<Location> callback){
        player.giveItemIfDontHave(MGCMaterial.DEBUG_STICK.toString());
        player.sender().info("<blue>使用<gold>调试棒</gold>选择位置：");
        player.sender().info("<yellow>左键方块 <gray>- <aqua>选择该方块的中心位置");
        player.sender().info("<yellow>左键空气 <gray>- <aqua>选择自己的位置（包含视角）");
        player.sender().info("<yellow>右键 <gray>- <red>取消本次位置选择");
        playerCallbackMap.put(player.player(), callback);
    }
}
