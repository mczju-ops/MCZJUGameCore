package com.github.mczjuops.mczjugamecore.game.kit.team;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 队伍会话的通用监听器：同队免伤与离线清理。
 *
 * <p>由 MGC 启动时自动注册，子插件不需要再注册一次。所有判断都以
 * {@link TeamSession} 中"本局具体游戏实例"的登记为准，因此：
 * 不在对局中的玩家、其它房间/其它游戏的玩家永远不会被当作队友；
 * 未登记队伍的玩家（比如裁判、旁观者）保持原版伤害规则。
 */
public final class TeamGuardListener implements Listener {

    /**
     * 取消同队玩家之间的伤害，伤害来源包括近战、弹射物、点燃的 TNT 与区域效果云。
     * @param event 实体伤害事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSameTeamDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        TeamSession session = TeamSession.ofPlayer(victim);
        if (session == null || !session.friendlyFireProtected()) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (!session.isTeammate(attacker, victim)) return;

        event.setCancelled(true);
    }

    /**
     * 玩家离线时清理其队伍登记与 Tab 修改，避免下一局拿到过期的队友关系。
     * @param event 玩家离线事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerExt player = new PlayerExt(event.getPlayer());
        for (TeamSession session : TeamSession.activeSessions()) {
            session.onPlayerQuit(player);
        }
    }

    /** 从伤害来源反查玩家：直接攻击、弹射物射手、TNT 点燃者、区域效果云来源。 */
    private static @Nullable Player resolveAttacker(Entity damager) {
        if (damager == null) return null;
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }
}
