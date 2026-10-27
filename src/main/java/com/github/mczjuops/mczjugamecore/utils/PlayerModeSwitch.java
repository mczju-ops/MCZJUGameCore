package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 玩家游戏模式的安全切换工具，专治"模式切了但客户端卡住"。
 *
 * <p>直接 {@code player.setGameMode(...)} 会在下面两种情况让玩家卡住，本工具统一处理：
 *
 * <ul>
 *   <li><b>死亡界面里切模式</b>：玩家死后服务端会一直停在死亡界面，直到收到重生包。
 *       只改模式不发重生包，就会出现"服务端已经是旁观、客户端还躺在死亡页面"的错位，
 *       玩家点什么都没反应。因此本工具先让模式在服务端生效，下一 tick 再发重生包，
 *       客户端随重生包一起进入目标模式。</li>
 *   <li><b>从旁观切回其他模式</b>：旁观相机（spectator target）不主动解除时，
 *       客户端画面会继续黏在上一个目标实体身上，表现为"人已经复活但视角跟着别人跑"。
 *       因此切模式前统一解除旁观相机、关闭界面、下坐骑。</li>
 * </ul>
 *
 * <p>另外 {@code PlayerGameModeChangeEvent} 可能被其它插件取消，本工具会在下一 tick
 * 检查一次结果并补切，避免模式修改静默失败。
 *
 * <p><b>延迟任务会失效：</b>每次切换都会领一个代次标记，只有最新一次操作排下的任务才会执行。
 * 玩家离局、游戏结束或被新的切换操作取代时，旧任务自动作废，不会在玩家已经回到大厅、
 * 进入别的房间之后还把模式改回去。需要主动作废时调用 {@link #invalidate(Player)}。
 *
 * <p>所有方法必须在服务器主线程调用，并且只做"模式与相机"层面的事情：
 * 不修改背包、档案、血量恢复或计分板，这些仍由调用方（通常是 game.kit 的旁观会话）负责。
 */
public final class PlayerModeSwitch {

    /** 每个玩家最近一次切换的代次标记；没有条目表示该玩家没有待执行的任务。 */
    private static final Map<UUID, Long> GENERATIONS = new ConcurrentHashMap<>();
    private static final AtomicLong TOKENS = new AtomicLong();

    private PlayerModeSwitch() {
    }

    /**
     * 玩家当前是否仍停在死亡界面（服务端未发重生包）。
     * @param player 目标玩家，可为 null
     * @return 在线且生命值归零时为 true
     */
    public static boolean isOnDeathScreen(@Nullable Player player) {
        return player != null && player.isOnline()
                && (player.isDead() || player.getHealth() <= 0.0D);
    }

    /**
     * 安全切换到旁观模式；在死亡界面时会在下一 tick 自动重生。
     * @param player 目标玩家
     * @param after 模式生效（死亡界面时为重生完成）后执行的回调，可为 null
     */
    public static void toSpectator(Player player, @Nullable Runnable after) {
        toMode(player, GameMode.SPECTATOR, after);
    }

    /**
     * 安全切换到生存模式；在死亡界面时会在下一 tick 自动重生。
     * @param player 目标玩家
     * @param after 模式生效（死亡界面时为重生完成）后执行的回调，可为 null
     */
    public static void toSurvival(Player player, @Nullable Runnable after) {
        toMode(player, GameMode.SURVIVAL, after);
    }

    /**
     * 安全切换到指定模式。
     * @param player 目标玩家
     * @param mode 目标模式
     * @param after 模式生效（死亡界面时为重生完成）后执行的回调，可为 null
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public static void toMode(Player player, GameMode mode, @Nullable Runnable after) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mode, "mode");
        if (!player.isOnline()) return;

        long token = nextToken(player.getUniqueId());
        boolean deathScreen = isOnDeathScreen(player);
        prepare(player);
        if (player.getGameMode() != mode) player.setGameMode(mode);

        if (deathScreen) {
            // 死亡界面：模式已在服务端生效，下一 tick 再发重生包，客户端随重生包进入目标模式
            runNextTick(player, token, () -> {
                if (isOnDeathScreen(player)) player.spigot().respawn();
                if (player.isOnline() && player.getGameMode() != mode) player.setGameMode(mode);
                if (after != null) after.run();
            });
            return;
        }

        // 模式切换可能被其它插件取消，下一 tick 确认一次，避免玩家停在旧模式
        runNextTick(player, token, () -> {
            if (!player.isOnline() || isOnDeathScreen(player)) return;
            if (player.getGameMode() != mode) player.setGameMode(mode);
        });
        if (after != null) after.run();
    }

    /**
     * 把停在死亡界面的玩家拉回游戏（不改变当前游戏模式）。
     * @param player 目标玩家
     * @param after 重生完成后执行的回调，可为 null
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public static void forceRespawn(Player player, @Nullable Runnable after) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) return;
        if (!isOnDeathScreen(player)) {
            if (after != null) after.run();
            return;
        }
        long token = nextToken(player.getUniqueId());
        runNextTick(player, token, () -> {
            if (isOnDeathScreen(player)) player.spigot().respawn();
            if (after != null) after.run();
        });
    }

    /**
     * 作废该玩家尚未执行的模式切换任务（玩家离局、游戏结束、被新操作取代时调用）。
     * @param player 目标玩家，可为 null
     */
    public static void invalidate(@Nullable Player player) {
        if (player != null) invalidate(player.getUniqueId());
    }

    /**
     * 作废该玩家尚未执行的模式切换任务。
     * @param uuid 玩家 UUID，可为 null
     */
    public static void invalidate(@Nullable UUID uuid) {
        if (uuid != null) GENERATIONS.remove(uuid);
    }

    /**
     * 该玩家当前是否还有待执行的任务。
     * @param player 目标玩家，可为 null
     * @return 有排队中的切换任务时为 true
     */
    public static boolean hasPending(Player player) {
        return player != null && GENERATIONS.containsKey(player.getUniqueId());
    }

    /** 切模式前的统一准备：关界面、下坐骑、解除旁观相机。 */
    private static void prepare(Player player) {
        player.closeInventory();
        if (player.getVehicle() != null) player.leaveVehicle();
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
    }

    private static long nextToken(UUID uuid) {
        long token = TOKENS.incrementAndGet();
        GENERATIONS.put(uuid, token);
        return token;
    }

    private static boolean isCurrent(UUID uuid, long token) {
        Long current = GENERATIONS.get(uuid);
        return current != null && current == token;
    }

    private static void runNextTick(Player player, long token, Runnable task) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            if (!player.isOnline()) {
                GENERATIONS.remove(uuid, token);
                return;
            }
            // 离局、结束或新的切换操作已让本次任务失效
            if (!isCurrent(uuid, token)) return;
            GENERATIONS.remove(uuid, token);
            task.run();
        }, 1L);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("游戏模式切换必须在服务器主线程调用");
        }
    }
}
