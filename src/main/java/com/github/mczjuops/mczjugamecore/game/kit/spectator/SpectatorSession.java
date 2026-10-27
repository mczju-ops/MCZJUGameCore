package com.github.mczjuops.mczjugamecore.game.kit.spectator;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.CountDown;
import com.github.mczjuops.mczjugamecore.utils.PlayerModeSwitch;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 单局旁观会话：把"死亡后变旁观"的两种常见规则收成一套流程。
 *
 * <p>规则由游戏自己选，一个会话里可以同时存在两种人：
 * <ul>
 *   <li>{@link #eliminate(PlayerExt)} —— <b>永久旁观</b>（饥饿游戏式）：被淘汰后一直观战到本局结束。
 *       可附加观战锚点与淘汰文案；</li>
 *   <li>{@link #awaitRespawn(PlayerExt, Location)} —— <b>等复活</b>（核心战争式）：
 *       死亡先进入旁观，倒计时结束后原地（或自定义位置）复活回生存模式，
 *       复活后短暂无敌并复位血量/饥饿/药水。</li>
 * </ul>
 *
 * <p><b>由死亡策略调用。</b>在游戏的 {@code AbstractPlayerDeathStrategy#onPlayerDeath} 里
 * 调用本会话，不要自己 {@code setGameMode}；退出与结束的清理由核心接管：
 * 玩家离局时核心调用 {@link #releaseFor(PlayerExt)}，
 * 游戏结束/中止/取消时核心调用 {@link #reset()}，子插件不需要自己兜底
 * （仍建议在自己的终局钩子里调用一次，重复调用是幂等的）。
 *
 * <p><b>为什么不会卡住客户端：</b>死亡界面里直接改游戏模式会让服务端与客户端错位
 * （服务端已是旁观，客户端还躺在死亡页面）。本会话统一走 {@link PlayerModeSwitch}：
 * 先让模式在服务端生效，下一 tick 发重生包，重生时由守卫监听器把重生点钉在观战锚点，
 * 再补切一次旁观；复活时同样先解除旁观相机再切生存，避免视角黏在别人身上。
 * 这些延迟任务带代次标记，玩家离局、结束或被新操作取代后自动失效。
 *
 * <p><b>展示与机制分离：</b>标题、聊天文案与音效全部来自 {@link SpectatorNotices}，
 * 默认给一套通用展示，可以逐项替换、整体静音，或用
 * {@link #onSpectate(Consumer)} / {@link #onRevive(Consumer)} 回调自己渲染。
 *
 * <p><b>只处理"模式与位置"。</b>背包、掉落物、计分板、淘汰名单广播仍由游戏负责；
 * 本类不会清空背包，也不会调用档案、Tab 或玩家数据接口。
 *
 * <p><b>关于自动重连：</b>本会话不负责把掉线玩家拉回游戏。重连由核心的
 * {@code AutoReconnect} 接口与 {@code PlayerReconnectListener} 完成（前提是游戏实现了
 * {@code AutoReconnect}，且退出策略在 {@code DISCONNECT} 时不把玩家移出本局）。
 * 玩家成功重新入局、档案加载完成后，守卫监听器才会调用 {@link #reapply(PlayerExt)}
 * 恢复旁观与剩余倒计时；没有入局就没有恢复。
 *
 * <p>用法：在 {@code onGameStart()} 创建一个实例，在死亡策略里调用
 * {@link #eliminate(PlayerExt)} 或 {@link #awaitRespawn(PlayerExt, Location)}，
 * 并在 {@code onGameEnd()} / {@code onGameAbort()} / {@code onGameCancel()} 中
 * 调用 {@link #reset()}。所有方法必须在服务器主线程调用。
 */
public final class SpectatorSession {

    /** 默认的等复活时长（秒） */
    public static final int DEFAULT_RESPAWN_SECONDS = 5;

    private static final Map<AbstractGame, SpectatorSession> SESSIONS = new IdentityHashMap<>();
    private static final Title.Times ELIMINATE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500));
    private static final Title.Times REVIVE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500));

    private final AbstractGame game;
    private final Map<UUID, SpectatorState> states = new HashMap<>();
    private final SpectatorNotices notices = new SpectatorNotices();

    private int respawnSeconds = DEFAULT_RESPAWN_SECONDS;
    private int reviveInvulnerableTicks = 40;
    private boolean restoreHealthOnRevive = true;
    private @Nullable Location defaultAnchor;
    private @Nullable Consumer<SpectatorTransition> spectateCallback;
    private @Nullable Consumer<SpectatorTransition> reviveCallback;

    /**
     * 一次旁观状态变化，用于把展示交给游戏自己渲染。
     *
     * @param player 发生变化的玩家
     * @param permanent true 表示永久旁观（淘汰），false 表示等复活/重新进入旁观
     */
    public record SpectatorTransition(PlayerExt player, boolean permanent) {
    }

    /**
     * 创建本局旁观会话；同一游戏实例重复创建时，旧会话会先被 {@link #reset()} 收拾掉。
     * @param game 所属游戏实例
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public SpectatorSession(AbstractGame game) {
        requireMainThread();
        this.game = Objects.requireNonNull(game, "game");
        SpectatorSession previous = SESSIONS.put(game, this);
        if (previous != null) previous.reset();
    }

    /** @return 本会话所属的游戏实例 */
    public AbstractGame game() {
        return game;
    }

    /**
     * 本会话的展示配置，可直接修改（例如 {@code session.notices().sounds(false)}）。
     * @return 展示配置实例
     */
    public SpectatorNotices notices() {
        return notices;
    }

    /**
     * 替换展示配置。
     * @param config 新的展示配置；null 时恢复默认展示
     * @return 本会话，便于链式配置
     */
    public SpectatorSession notices(@Nullable SpectatorNotices config) {
        SpectatorNotices source = config;
        SpectatorNotices target = notices;
        if (source == null) source = new SpectatorNotices();
        target.chat(source.chat()).titles(source.titles()).sounds(source.sounds())
                .eliminateText(source.eliminateChat(), source.eliminateTitle(), source.eliminateSubtitle())
                .reviveText(source.reviveChat(), source.reviveTitle(), source.reviveSubtitle())
                .respawnText(source.awaitingChat(), source.countdownText())
                .sounds(source.eliminateSound(), source.countdownSound(), source.reviveSound())
                .volumes(source.eliminateVolume(), source.countdownVolume(), source.reviveVolume());
        return this;
    }

    /**
     * 注册"玩家进入旁观"之后的回调，用于游戏自己播报或刷新记分板。
     * @param callback 回调，可为 null 表示取消
     * @return 本会话，便于链式配置
     */
    public SpectatorSession onSpectate(@Nullable Consumer<SpectatorTransition> callback) {
        this.spectateCallback = callback;
        return this;
    }

    /**
     * 注册"玩家复活完成"之后的回调，用于游戏自己播报或发装备。
     * @param callback 回调，可为 null 表示取消
     * @return 本会话，便于链式配置
     */
    public SpectatorSession onRevive(@Nullable Consumer<SpectatorTransition> callback) {
        this.reviveCallback = callback;
        return this;
    }

    // ==================== 规则配置 ====================

    /**
     * 设置 {@link #awaitRespawn(PlayerExt, Location)} 的默认倒计时秒数（默认 5 秒）。
     * @param seconds 秒数，必须大于 0
     * @return 本会话，便于链式配置
     * @throws IllegalArgumentException seconds 不大于 0
     */
    public SpectatorSession respawnSeconds(int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("复活倒计时必须大于 0 秒");
        this.respawnSeconds = seconds;
        return this;
    }

    /** @return 默认复活倒计时秒数 */
    public int respawnSeconds() {
        return respawnSeconds;
    }

    /**
     * 设置复活后的无敌 tick 数（默认 40 tick，即 2 秒）。
     * @param ticks tick 数，0 表示不给无敌时间
     * @return 本会话，便于链式配置
     * @throws IllegalArgumentException ticks 为负数
     */
    public SpectatorSession reviveInvulnerableTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("无敌时间不能为负数");
        this.reviveInvulnerableTicks = ticks;
        return this;
    }

    /**
     * 设置复活时是否复位血量、饥饿、火焰与药水（默认开启）。
     * @param restore true 时调用 {@link PlayerExt#resetState()}
     * @return 本会话，便于链式配置
     */
    public SpectatorSession restoreHealthOnRevive(boolean restore) {
        this.restoreHealthOnRevive = restore;
        return this;
    }

    /**
     * 设置默认观战锚点：没显式给位置时，旁观者会被放在这里而不是死亡地点。
     * @param anchor 观战位置，null 表示使用死亡地点
     * @return 本会话，便于链式配置
     */
    public SpectatorSession defaultAnchor(@Nullable Location anchor) {
        this.defaultAnchor = anchor == null ? null : anchor.clone();
        return this;
    }

    // ==================== 永久旁观 ====================

    /**
     * 让玩家永久旁观（饥饿游戏式淘汰）。
     * @param player 本局在线玩家
     * @throws IllegalArgumentException 玩家已离线或不在本局
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void eliminate(PlayerExt player) {
        eliminate(player, null, null);
    }

    /**
     * 让玩家永久旁观（饥饿游戏式淘汰），并可以自定义观战位置与淘汰文案。
     * @param player 本局在线玩家
     * @param anchor 观战位置；null 时使用本会话默认锚点，再退化到死亡地点
     * @param message 直接发给该玩家的淘汰文案（MiniMessage）；给出时会无视
     *                {@link SpectatorNotices#chat()} 的开关强制发送
     * @throws IllegalArgumentException 玩家已离线或不在本局
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void eliminate(PlayerExt player, @Nullable Location anchor, @Nullable String message) {
        requireMainThread();
        Player p = requireInGame(player);
        Location watch = firstNonNull(anchor, defaultAnchor, p.getLocation());

        SpectatorState state = new SpectatorState(false, watch);
        cancelCountdown(states.put(p.getUniqueId(), state));

        String chat = message != null && !message.isBlank()
                ? message
                : (notices.chat() ? notices.eliminateChat() : null);
        if (chat != null) player.sender().info(chat);
        if (notices.titles()) {
            p.showTitle(Title.title(
                    TextParser.parse(notices.eliminateTitle()),
                    TextParser.parse(notices.eliminateSubtitle()),
                    ELIMINATE_TIMES
            ));
        }
        playSound(p, notices.eliminateSound(), notices.eliminateVolume(), 1f);
        enterSpectator(p, watch, () -> fireSpectate(player, true));
    }

    // ==================== 等复活 ====================

    /**
     * 让玩家进入等复活状态，使用本会话配置的倒计时秒数。
     * @param player 本局在线玩家
     * @param watchLocation 等待期间的观战位置；null 时使用默认锚点，再退化到死亡地点
     * @throws IllegalArgumentException 玩家已离线或不在本局
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void awaitRespawn(PlayerExt player, @Nullable Location watchLocation) {
        awaitRespawn(player, respawnSeconds, watchLocation, null, null);
    }

    /**
     * 让玩家进入等复活状态，倒计时结束后复活。
     * @param player 本局在线玩家
     * @param seconds 倒计时秒数，必须大于 0
     * @param watchLocation 等待期间的观战位置；null 时使用默认锚点，再退化到死亡地点
     * @param respawnLocation 复活位置解析器（参数是复活的玩家），null 时使用观战位置
     * @param onRevived 复活完成后的回调（此时玩家已在生存模式且已传送），可为 null
     * @throws IllegalArgumentException 玩家已离线、不在本局，或秒数不大于 0
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void awaitRespawn(PlayerExt player, int seconds, @Nullable Location watchLocation,
                             @Nullable Function<Player, Location> respawnLocation,
                             @Nullable Consumer<PlayerExt> onRevived) {
        requireMainThread();
        if (seconds <= 0) throw new IllegalArgumentException("复活倒计时必须大于 0 秒");
        Player p = requireInGame(player);
        Location watch = firstNonNull(watchLocation, defaultAnchor, p.getLocation());

        SpectatorState state = new SpectatorState(true, watch);
        state.respawnLocation = respawnLocation;
        state.onRevived = onRevived;
        cancelCountdown(states.put(p.getUniqueId(), state));

        if (notices.chat()) {
            player.actionBarSender().info(format(notices.awaitingChat(), seconds));
        }
        playSound(p, notices.eliminateSound(), notices.eliminateVolume(), 1.2f);
        enterSpectator(p, watch, () -> fireSpectate(player, false));
        // 死亡界面里的重生要等到下一 tick，倒计时同样延后启动，避免"倒计时跑完了人还躺着"
        runNextTick(p, () -> startCountdown(p, state, seconds));
    }

    /**
     * 立即复活（无视剩余倒计时）。
     * @param player 玩家
     * @return 该玩家原本在旁观名单里并已复活时为 true
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public boolean revive(PlayerExt player) {
        requireMainThread();
        if (player == null) return false;
        Player p = player.player();
        SpectatorState state = states.get(p.getUniqueId());
        if (state == null) return false;
        revive(state, p);
        return true;
    }

    /**
     * 取消等复活，玩家继续留在旁观（之后可以调用 {@link #revive(PlayerExt)} 手动复活）。
     * @param player 玩家
     * @return 是否有待进行的复活被取消
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public boolean cancelAwaitRespawn(PlayerExt player) {
        requireMainThread();
        if (player == null) return false;
        SpectatorState state = states.get(player.getUniqueId());
        if (state == null || !state.awaitingRespawn) return false;
        state.awaitingRespawn = false;
        cancelCountdown(state);
        if (notices.chat()) player.actionBarSender().info("<gray>复活已取消");
        return true;
    }

    // ==================== 查询 ====================

    /** @return 玩家是否在本会话的旁观名单里（含等复活） */
    public boolean isSpectating(@Nullable UUID uuid) {
        return uuid != null && states.containsKey(uuid);
    }

    /** @return 玩家是否正在等待复活 */
    public boolean isWaitingRespawn(@Nullable UUID uuid) {
        SpectatorState state = uuid == null ? null : states.get(uuid);
        return state != null && state.awaitingRespawn;
    }

    /** @return 玩家是否已被永久淘汰（旁观且不等复活） */
    public boolean isEliminated(@Nullable UUID uuid) {
        SpectatorState state = uuid == null ? null : states.get(uuid);
        return state != null && !state.awaitingRespawn;
    }

    /**
     * 玩家的观战锚点。
     * @param uuid 玩家 UUID
     * @return 锚点副本；玩家不在旁观名单时为 null
     */
    public @Nullable Location anchorFor(@Nullable UUID uuid) {
        SpectatorState state = uuid == null ? null : states.get(uuid);
        if (state == null || state.anchor == null) return null;
        return state.anchor.clone();
    }

    /**
     * 等复活剩余秒数。
     * @param uuid 玩家 UUID
     * @return 剩余秒数；不在等复活状态时为 -1
     */
    public int secondsLeft(@Nullable UUID uuid) {
        SpectatorState state = uuid == null ? null : states.get(uuid);
        if (state == null || state.countdown == null) return -1;
        return state.countdown.getSecondsLeft();
    }

    /** @return 当前在线且正在旁观的玩家 */
    public List<PlayerExt> spectators() {
        return onlinePlayers(false);
    }

    /** @return 当前在线且正在等待复活的玩家 */
    public List<PlayerExt> waitingRespawn() {
        return onlinePlayers(true);
    }

    // ==================== 生命周期 ====================

    /**
     * 重连或脚本重新施加旁观状态：把玩家放回旁观模式并传送到观战锚点；
     * 等复活的玩家会接着剩余秒数继续倒计时。
     *
     * <p>只在该玩家已经重新入局时生效（{@link PlayerExt#getGame()} 就是本局），
     * 本身不会把离线玩家拉回游戏。
     *
     * @param player 本局在线玩家
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void reapply(PlayerExt player) {
        requireMainThread();
        if (player == null) return;
        Player p = player.player();
        SpectatorState state = states.get(p.getUniqueId());
        if (state == null || !p.isOnline()) return;
        // 重连后可能已经不在本局（回大厅或进了别的房间），此时不能抢它的模式与位置
        if (player.getGame() != game) {
            discard(p, state);
            return;
        }

        enterSpectator(p, state.anchor, null);
        if (state.awaitingRespawn && state.countdown == null) {
            int seconds = Math.max(1, state.remainingSeconds);
            runNextTick(p, () -> startCountdown(p, state, seconds));
        }
    }

    /**
     * 玩家掉线：停掉倒计时但保留登记，重连后由守卫监听器重新施加旁观。
     * @param player 离场玩家
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void onPlayerQuit(PlayerExt player) {
        requireMainThread();
        if (player == null) return;
        SpectatorState state = states.get(player.getUniqueId());
        if (state != null) cancelCountdown(state);
    }

    /**
     * 玩家离开本局（主动退出、被移出、加入失败回滚）：撤销旁观登记，
     * 取消倒计时与延迟任务，并把玩家切回与大厅一致的安全模式，避免带着旁观模式离场。
     *
     * <p>由核心在离局流程中调用；子插件一般不需要自己调。
     *
     * @param player 离场玩家
     * @return 该玩家原本在旁观名单里并已释放时为 true
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public boolean release(@Nullable PlayerExt player) {
        requireMainThread();
        if (player == null) return false;
        Player p = player.player();
        SpectatorState state = states.remove(p.getUniqueId());
        if (state == null) return false;
        cancelCountdown(state);
        if (p.isOnline()) {
            PlayerModeSwitch.invalidate(p);
            p.resetTitle();
            // 离局后玩家会回大厅，用大厅一致的冒险模式，不用生存模式
            PlayerModeSwitch.toMode(p, GameMode.ADVENTURE, null);
        }
        return true;
    }

    /**
     * 结束本局：停掉全部倒计时，把仍留在本局的旁观者（含等复活中）从旁观模式里放出来，
     * 避免他们把旁观模式带回大厅；随后清空登记并注销会话。幂等。
     *
     * <p>由核心在结束/中止/取消流程中调用；子插件在自己的终局钩子里再调一次是安全的。
     *
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void reset() {
        requireMainThread();
        List<PlayerExt> spectators = spectators();
        for (SpectatorState state : states.values()) cancelCountdown(state);
        states.clear();

        for (PlayerExt player : spectators) {
            Player p = player.player();
            if (!p.isOnline()) continue;
            AbstractGame current = player.getGame();
            // 已经去了别的游戏时不要抢它的模式设置
            if (current != null && current != game) continue;
            PlayerModeSwitch.invalidate(p);
            p.resetTitle();
            PlayerModeSwitch.toMode(p, GameMode.ADVENTURE, null);
        }
        spectateCallback = null;
        reviveCallback = null;
        if (SESSIONS.get(game) == this) SESSIONS.remove(game);
    }

    /**
     * 撤销玩家在所有活动旁观会话中的登记（核心离局流程使用）。
     * @param player 离场玩家
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public static void releaseFor(@Nullable PlayerExt player) {
        if (player == null) return;
        for (SpectatorSession session : activeSessions()) session.release(player);
    }

    /**
     * 按游戏实例取回会话。
     * @param game 游戏实例
     * @return 该实例的旁观会话；未创建时为 null
     */
    public static @Nullable SpectatorSession of(@Nullable AbstractGame game) {
        return game == null ? null : SESSIONS.get(game);
    }

    /**
     * 按玩家当前所在游戏取回会话（比较具体游戏实例，不同房间互不影响）。
     * @param player 在线玩家
     * @return 该玩家所在游戏的旁观会话；玩家不在游戏中时为 null
     */
    public static @Nullable SpectatorSession ofPlayer(@Nullable Player player) {
        return player == null ? null : of(new PlayerExt(player).getGame());
    }

    /** @return 当前活动的全部会话，供核心的守卫监听器使用 */
    static Collection<SpectatorSession> activeSessions() {
        return List.copyOf(SESSIONS.values());
    }

    // ==================== 内部 ====================

    /** 切旁观 + 传送锚点；死亡界面下由 PlayerModeSwitch 在重生包之后回调。 */
    private void enterSpectator(Player p, @Nullable Location anchor, @Nullable Runnable done) {
        PlayerModeSwitch.toSpectator(p, () -> {
            if (!stillInGame(p)) return;
            if (anchor != null && anchor.getWorld() != null) p.teleport(anchor);
            if (done != null) done.run();
        });
    }

    private void startCountdown(Player p, SpectatorState state, int seconds) {
        if (!stillInGame(p) || states.get(p.getUniqueId()) != state) {
            discard(p, state);
            return;
        }
        if (!state.awaitingRespawn) return;
        state.remainingSeconds = seconds;
        CountDown countdown = CountDown.builder(MCZJUGameCore.getInstance(), seconds)
                .onTick((left, cd) -> {
                    if (!stillInGame(p) || states.get(p.getUniqueId()) != state) {
                        cd.cancel();
                        discard(p, state);
                        return;
                    }
                    state.remainingSeconds = left;
                    if (notices.chat()) {
                        new PlayerExt(p).actionBarSender().info(format(notices.countdownText(), left));
                    }
                    playSound(p, notices.countdownSound(), notices.countdownVolume(), 1.4f);
                })
                .onComplete(() -> {
                    if (states.get(p.getUniqueId()) != state) return;
                    if (!stillInGame(p)) {
                        discard(p, state);
                        return;
                    }
                    if (p.isOnline()) revive(state, p);
                })
                .build();
        state.countdown = countdown;
        countdown.start();
    }

    private void revive(SpectatorState state, Player p) {
        PlayerExt player = new PlayerExt(p);
        // 玩家已离局（主动退出、加入别的游戏）时只摘登记，不再回传、重置状态或触发回调
        if (!stillInGame(p)) {
            discard(p, state);
            return;
        }
        // 先摘除登记：复活回调里重新登记时不会被旧状态干扰
        if (!states.remove(p.getUniqueId(), state)) return;
        cancelCountdown(state);

        PlayerModeSwitch.toMode(p, GameMode.SURVIVAL, () -> {
            if (!stillInGame(p)) return;
            Location target = state.respawnLocation == null ? state.anchor : state.respawnLocation.apply(p);
            if (target == null) target = state.anchor;
            if (target != null && target.getWorld() != null) p.teleport(target);
            if (restoreHealthOnRevive) player.resetState();
            if (reviveInvulnerableTicks > 0) p.setNoDamageTicks(reviveInvulnerableTicks);

            if (notices.chat()) player.sender().info(notices.reviveChat());
            if (notices.titles()) {
                p.showTitle(Title.title(
                        TextParser.parse(notices.reviveTitle()),
                        TextParser.parse(notices.reviveSubtitle()),
                        REVIVE_TIMES
                ));
            }
            playSound(p, notices.reviveSound(), notices.reviveVolume(), 1.5f);
            if (state.onRevived != null) state.onRevived.accept(player);
            fireRevive(player);
        });
    }

    private void fireSpectate(PlayerExt player, boolean permanent) {
        Consumer<SpectatorTransition> callback = spectateCallback;
        if (callback != null && player.player().isOnline() && player.getGame() == game) {
            callback.accept(new SpectatorTransition(player, permanent));
        }
    }

    private void fireRevive(PlayerExt player) {
        Consumer<SpectatorTransition> callback = reviveCallback;
        if (callback != null) callback.accept(new SpectatorTransition(player, false));
    }

    private void cancelCountdown(SpectatorState state) {
        if (state == null) return;
        CountDown countdown = state.countdown;
        state.countdown = null;
        if (countdown != null) countdown.cancel();
    }

    /**
     * 登记已失效（玩家离局或进了别的游戏）时摘下状态并停表，
     * 避免旧倒计时继续切模式、传送到旧地图或执行旧回调。
     */
    private void discard(Player p, SpectatorState state) {
        cancelCountdown(state);
        states.remove(p.getUniqueId(), state);
    }

    /** 玩家仍在线且仍属于本局（比较具体游戏实例）。 */
    private boolean stillInGame(Player p) {
        return p.isOnline() && new PlayerExt(p).getGame() == game;
    }

    private List<PlayerExt> onlinePlayers(boolean onlyWaiting) {
        List<PlayerExt> result = new ArrayList<>();
        for (Map.Entry<UUID, SpectatorState> entry : states.entrySet()) {
            if (onlyWaiting && !entry.getValue().awaitingRespawn) continue;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) result.add(new PlayerExt(p));
        }
        return result;
    }

    /** 校验玩家在线且确实在本局，避免误改其它房间的玩家。 */
    private Player requireInGame(PlayerExt player) {
        Objects.requireNonNull(player, "player");
        Player p = player.player();
        if (!p.isOnline()) throw new IllegalArgumentException("只能处理在线玩家");
        if (player.getGame() != game) throw new IllegalArgumentException("只能处理本局玩家");
        return p;
    }

    private void playSound(Player p, @Nullable Sound sound, float volume, float pitch) {
        if (!notices.sounds() || sound == null) return;
        p.playSound(p.getLocation(), sound, volume, pitch);
    }

    /** 按 %d 占位填充秒数；没有占位时原样返回。 */
    private static String format(String template, int seconds) {
        return template == null ? "" : template.replace("%d", Integer.toString(seconds));
    }

    private static Location firstNonNull(@Nullable Location first, @Nullable Location second, Location fallback) {
        if (first != null) return first.clone();
        if (second != null) return second.clone();
        return fallback.clone();
    }

    private static void runNextTick(Player player, Runnable task) {
        Bukkit.getScheduler().runTaskLater(MCZJUGameCore.getInstance(), () -> {
            if (player.isOnline()) task.run();
        }, 1L);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("旁观会话必须在服务器主线程调用");
    }

    /** 单个玩家的旁观状态；只有倒计时与剩余秒数会变，所以不做成不可变对象。 */
    private static final class SpectatorState {
        private boolean awaitingRespawn;
        private final @Nullable Location anchor;
        private @Nullable CountDown countdown;
        private int remainingSeconds;
        private @Nullable Function<Player, Location> respawnLocation;
        private @Nullable Consumer<PlayerExt> onRevived;

        private SpectatorState(boolean awaitingRespawn, @Nullable Location anchor) {
            this.awaitingRespawn = awaitingRespawn;
            this.anchor = anchor;
        }
    }
}
