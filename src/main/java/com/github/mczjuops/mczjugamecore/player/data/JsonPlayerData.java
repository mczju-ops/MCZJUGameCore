package com.github.mczjuops.mczjugamecore.player.data;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.JsonMGCSerializable;

/**
 * 使用Json保存玩家数据，部分复杂类型也能保存，但和MC相关的复杂类型大部分无法保存，比如ItemStack等
 * 支持的MC相关复杂类型：Location；其它类型如果需要支持，可以向本仓库提issue，需要参考LocationAdapter，加一个序列化实现
 * 和GameRoom类似，只有public的字段能被保存。如果有临时变量，可以标记为private，这个时候想用什么类型都可以
 * 修改值后，请记得调用setModified，否则数据将视为没有被更新，不会自动保存。
 * 要获取玩家的数据，参考playerExt的getData方法
 * 保存的路径为plugin/MCZJUGameCore/player/game_id/player_uuid.json
 */
public class JsonPlayerData extends AbstractPlayerData implements JsonMGCSerializable {
    @Override
    public String getFilePath() {
        String dataPath = MCZJUGameCore.getInstance().getDataFolder().getAbsolutePath();
        return "%s/player_data/%s/%s.json".formatted(dataPath, getGameID(), getPlayerID());
    }
}
