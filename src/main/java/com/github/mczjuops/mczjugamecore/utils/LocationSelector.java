package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.MGCItem;
import com.github.mczjuops.mczjugamecore.item.MGCMaterial;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

        // 不是调试棒直接返回
        if (!MCZJUGameCore.getItemManager().is(
                player.getInventory().getItemInMainHand(), MGCMaterial.DEBUG_STICK.toString())) return;

        // 没有注册回调
        if (!playerCallbackMap.containsKey(player)) return;

        event.setCancelled(true); // 防止误触发方块交互

        Consumer<Location> callback = playerCallbackMap.remove(player);

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
        player.sender().primary("用调试棒点击方块以选择方块，点击空气以选择当前所处位置");
        playerCallbackMap.put(player.player(), callback);
    }
}
