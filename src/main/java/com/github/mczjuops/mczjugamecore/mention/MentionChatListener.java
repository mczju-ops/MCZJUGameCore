package com.github.mczjuops.mczjugamecore.mention;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/** 把聊天中的 <code>@</code> / <code>@@</code> 转换为提示音与 title；效果在主线程施加。 */
public final class MentionChatListener implements Listener {

    private final MentionManager manager;

    public MentionChatListener(MentionManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!manager.getSettings().enabled()) return;
        String text = TextParser.plain(event.message());
        if (text.indexOf('@') < 0) return;
        Player sender = event.getPlayer();
        Map<Player, MentionParser.Kind> targets = manager.resolveTargets(sender, text);
        if (targets.isEmpty()) {
            manager.hintIfAttempt(sender, text);
            return;
        }
        if (!manager.tryUse(sender)) return;
        if (manager.getSettings().hideMessage()) {
            event.setCancelled(true);
            manager.sendFeedback(sender, targets.keySet());
        }
        Bukkit.getScheduler().runTask(
                MCZJUGameCore.getInstance(),
                () -> manager.apply(sender, targets)
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer().getUniqueId());
    }
}
