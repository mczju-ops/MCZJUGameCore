package com.github.mczjuops.mczjugamecore.player;

import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.party.Party;
import com.github.mczjuops.mczjugamecore.utils.sender.Sender;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.PlayerSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
玩家类扩展，传Player参数进去可以获得拥有扩展函数的PlayerExt
 */
public record PlayerExt(@NotNull Player player) {

    public Sender sender(){
        return new PlayerSender(player);
    }

    public @Nullable AbstractGame getGame(){
        //TODO
        return null;
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
        return getParty() == null;
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
}
