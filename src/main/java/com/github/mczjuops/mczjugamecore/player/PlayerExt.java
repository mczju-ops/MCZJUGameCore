package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.LocationSelector;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.PlayerSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.function.Consumer;

/*
玩家类扩展，传Player参数进去可以获得拥有扩展函数的PlayerExt
 */
public record PlayerExt(@NotNull Player player) {

    public Sender sender(){
        return new PlayerSender(player);
    }

    public Sender actionBarSender(){
        PlayerSender sender = new PlayerSender(player);
        sender.setShouldActionbar(true);
        return sender;
    }

    public @Nullable AbstractGame getGame(){
        return MCZJUGameCore.getPlayerManager().getPlayerGame(this);
    }

    /**
     * 玩家是否在游戏中
     * @return true: 在游戏中
     */
    public boolean isInGame(){
        return getGame() != null;
    }

    public @Nullable Party getParty(){
        // TODO
        return null;
    }

    public boolean isPartyLeader(){
        return getParty() != null && getParty().getLeader().equals(this);
    }

    public boolean isInParty(){
        return getParty() != null;
    }

    public void selectLocation(Consumer<Location> callback){
        LocationSelector.getInstance().selectLocation(this, callback);
    }

    public void giveItemIfDontHave(String id) {
        for (ItemStack it : player.getInventory()) {
            if (MCZJUGameCore.getItemManager().is(it, id)) {
                return;
            }
        }
        giveItem(id);
    }

    public void giveItem(String id){
        ItemStack item = MCZJUGameCore.getItemManager().getItem(id);
        if (item == null){
            sender().error(STR."无法获取物品\{id}，因为物品不存在");
            return;
        }
        giveItem(item);
    }

    public void giveItem(ItemStack itemStack){
        HashMap<Integer, ItemStack> itemMap = player.getInventory().addItem(itemStack);
        if (!itemMap.isEmpty()){
            sender().warn("背包已满，请检查周围掉落物");
            itemMap.forEach((_, item) ->{
                player.getWorld().dropItem(player.getLocation(), item);
            });
        }else {
            sender().success("已获取物品，请检查背包");
        }
    }

    /**
     * 重写equal函数，不确定这样写在mc中对不对
     * @param obj   the reference object with which to compare.
     * @return  他们的player为同一个，则返回true
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PlayerExt){
            return ((PlayerExt) obj).player == player;
        } else if (obj instanceof Player) {
            return obj == player;
        }
        return false;
    }

    /**
     * 退出当前所在的游戏，不论游戏是否开始
     */
    public void quitGame(PlayerQuitReason reason){
        MCZJUGameCore.getPlayerManager().leaveGame(this, reason);
    }
}
