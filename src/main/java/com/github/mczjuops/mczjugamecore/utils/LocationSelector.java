package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.item.MGCMaterial;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
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

        boolean sneaking = player.isSneaking();

        if (action.isRightClick()) {
            // 右键（含潜行右键）：选择玩家位置
            Consumer<Location> callback = playerCallbackMap.remove(player);
            Location loc = sneaking ? snapLocation(player.getLocation()) : player.getLocation();
            callback.accept(loc);
            player.sendMessage(TextParser.parse("<green>成功选择自身位置<newline>" + formatLocation(loc)));
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        } else if (action.isLeftClick()) {
            // 左键空气：忽略，不消耗回调
            if (event.getClickedBlock() == null) return;

            Consumer<Location> callback = playerCallbackMap.remove(player);
            Location loc = sneaking
                    ? getAdaptedLocation(event)
                    : event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5); // 直接左键：取方块中心
            callback.accept(loc);
            player.sendMessage(TextParser.parse("<green>成功选择方块位置<newline>" + formatLocation(loc)));
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!playerCallbackMap.containsKey(player)) return;

        // 只拦截调试棒的丢弃
        if (!MCZJUGameCore.getItemManager().is(
                event.getItemDrop().getItemStack(), MGCMaterial.DEBUG_STICK.toString())) return;

        event.setCancelled(true); // 物品留在手中
        playerCallbackMap.remove(player);
        player.sendMessage(TextParser.parse("<yellow>已取消本次位置选择"));
        player.playSound(player, Sound.ENTITY_BREEZE_SHOOT, 1.0f, 2.0f);
    }

    private Location getAdaptedLocation(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        assert block != null;
        RayTraceResult result = player.rayTraceBlocks(6.0);
        if (result != null && block.equals(result.getHitBlock())) {
            Vector hit = result.getHitPosition(); // 精确命中点（世界坐标）
            return new Location(
                    block.getWorld(),
                    Math.round(hit.getX() * 2) / 2.0,
                    Math.round(hit.getY() * 2) / 2.0,
                    Math.round(hit.getZ() * 2) / 2.0
            );
        }
        // 兜底：取方块中心
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    /** 潜行右键：XYZ 吸附到 0.5 的倍数，yaw/pitch 吸附到 45° 的倍数 */
    private Location snapLocation(Location loc) {
        double x = Math.round(loc.getX() * 2) / 2.0;
        double y = Math.round(loc.getY() * 2) / 2.0;
        double z = Math.round(loc.getZ() * 2) / 2.0;
        float yaw = Math.round(loc.getYaw() / 45f) * 45f;
        float pitch = Math.round(loc.getPitch() / 45f) * 45f;
        return new Location(loc.getWorld(), x, y, z, yaw, pitch);
    }

    public void selectLocation(PlayerExt player, Consumer<Location> callback){
        player.giveItemIfDontHave(MGCMaterial.DEBUG_STICK.toString());
        player.sender().info("<aqua><b>使用<reset><gold>调试棒</gold><aqua><b>选择位置：");
        player.sender().info("<yellow>左键方块 <gray>- <aqua>选择点击的方块的中心坐标");
        player.sender().info("<yellow>潜行左键方块 <gray>- <aqua>选择命中的点位，自动将坐标值适应为0.5的倍数");
        player.sender().info("<yellow>右键 <gray>- <aqua>选择自己的位置（包含朝向）");
        player.sender().info("<yellow>潜行右键 <gray>- <aqua>选择自己的位置，自动将坐标或角度值适应为0.5或45°的倍数");
        player.sender().info("<yellow>按下<key:key.drop>键 <gray>- <red>取消本次位置选择");
        playerCallbackMap.put(player.player(), callback);
    }

    private String formatLocation(Location location) {
        DecimalFormat df = new DecimalFormat("#.##");
        return "<aqua>世界 <dark_aqua>%s</dark_aqua>，坐标 <dark_aqua>[%s %s %s]</dark_aqua>，朝向 <dark_aqua>[%s° %s°]</dark_aqua>".formatted(
                location.getWorld().getName(),
                df.format(location.getX()), df.format(location.getY()), df.format(location.getZ()),
                df.format(location.getPitch()), df.format(location.getYaw())
        );
    }
}
