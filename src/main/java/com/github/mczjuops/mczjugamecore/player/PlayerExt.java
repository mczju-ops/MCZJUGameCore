package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.data.AbstractPlayerData;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.player.strategy.PlayerQuitReason;
import com.github.mczjuops.mczjugamecore.utils.LocationSelector;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.PlayerSender;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import static com.github.mczjuops.mczjugamecore.profile.ProfileData.LOBBY_PROFILE_ID;

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

    /** 玩家是否在任意游戏中 */
    public boolean isInGame(){
        return getGame() != null;
    }

    public boolean isInGame(Class<? extends AbstractGame> gameClass){
        return MCZJUGameCore.getPlayerManager().isPlayerInGame(this, gameClass);
    }

    /**
     * 切换玩家的 profile
     *
     * @param profileId 对应的游戏 ID
     */
    public void switchProfile(@Nullable String profileId) {
        if (profileId == null) profileId = LOBBY_PROFILE_ID;
        MCZJUGameCore.getProfileManager().switchProfile(player, profileId);
    }

    public @Nullable Party getParty(){
        return MCZJUGameCore.getPartymanager().getPlayerParty(this);
    }

    public boolean isPartyLeader(){
        return getParty() != null && getParty().getLeader().equals(this);
    }

    public boolean isInParty(){
        return getParty() != null;
    }

    /**
     * 让玩家选择位置。执行后，玩家会得到一个调试棒，用于选择位置
     * @param callback  选择位置后，要执行的callback
     */
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
            sender().error("无法获取物品%s，因为物品不存在".formatted(id));
            return;
        }
        giveItem(item);
    }

    public void giveItem(ItemStack itemStack){
        HashMap<Integer, ItemStack> itemMap = player.getInventory().addItem(itemStack);
        if (!itemMap.isEmpty()){
            sender().warn("背包已满，请检查周围掉落物");
            itemMap.forEach((integer, item) -> player.getWorld().dropItem(player.getLocation(), item));
        }else {
            sender().success("已获取物品，请检查背包");
        }
    }

    public String getName() {
        return player.getName();
    }

    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    public String getDisplayName() {
        if (player.isOp()) return "<dark_red>%s</dark_red>".formatted(getName());
        else return "<green>%s</green>".formatted(getName());
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

    public void resetState() {
        if (!player.isOnline()) return;

        player.setFireTicks(0);
        player.setFreezeTicks(0);

        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            player.setHealth(attribute.getValue());
        }

        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
    }

    /** 退出当前所在的游戏，不论游戏是否开始 */
    public void quitGame(PlayerQuitReason reason){
        MCZJUGameCore.getPlayerManager().leaveGame(this, reason);
    }

    /**
     * 获取玩家的数据，在使用前，你需要在onEnable时注册数据类，注册时MGC会自动加载已有的数据
     * <p>直接调用即可获取玩家数据，如果此前改玩家没有数据，则会创建一个新的playerData对象
     * <p>数据文件默认以JSON的格式保存在player/{gameId}/{playerId}.json
     * <p>每5分钟会自动保存，玩家退出和关服时也会自动保存
     * <p>重要：修改后记得 data.setModified(true)
     * @param dataClass 数据类，不能填JsonPlayerData等抽象类
     * @return  玩家数据，没有则新建一个
     * @param <T> dataClass相同的数据类class
     */
    public <T extends AbstractPlayerData> @NotNull T getData(Class<T> dataClass){
        return MCZJUGameCore.getPlayerDataManager().getPlayerData(player.getUniqueId().toString(), dataClass);
    }

    /**
     * 获取玩家数据，这个函数一般用于获取其它游戏的玩家数据，不推荐使用
     * @param gameId    游戏ID
     * @return  玩家数据，如果gameID对应的数据类未注册，返回空
     */
    public @Nullable AbstractPlayerData getData(String gameId){
        return MCZJUGameCore.getPlayerDataManager().getPlayerData(gameId, player.getUniqueId().toString());
    }
}
