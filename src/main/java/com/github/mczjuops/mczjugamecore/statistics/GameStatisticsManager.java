package com.github.mczjuops.mczjugamecore.statistics;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.achievement.AchievementStorage;
import com.github.mczjuops.mczjugamecore.event.*;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** 通用统计服务。所有公开方法在服务器线程调用，数据修改发布通知，成就系统独立监听。 */
public final class GameStatisticsManager implements Listener {
    private final AchievementStorage storage;
    private final Map<UUID, Clock> clocks = new HashMap<>();
    private final Set<DeathKey> reported = new HashSet<>();
    private int reportTick = -1;
    private final Map<AbstractGame, Set<UUID>> participants = new WeakHashMap<>();
    private final Map<StatisticKey, OwnedDisplay> displays = new LinkedHashMap<>();
    private record OwnedDisplay(Plugin owner, StatisticDisplay display) {}
    private record Clock(AbstractGame game, int ticks) {}
    private record DeathKey(AbstractGame game, UUID victim) {}

    /** 核心初始化共享存储。 */
    public GameStatisticsManager(AchievementStorage storage) { this.storage = storage; }
    /**
     * 批量注册独立的统计菜单展示项，按注册顺序排列在内置统计之后，不保存取值结果。
     * 先校验整批定义，重复游戏内 ID、未知游戏或已停用的插件会被拒绝，不会部分注册。
     * 插件停用时自动移除定义。
     * @param owner 所属子插件
     * @param additions 新展示定义，其取值函数在主线程执行，不得进行阻塞 I/O
     */
    public void register(Plugin owner, Collection<StatisticDisplay> additions) {
        AchievementStorage.requireMainThread();
        Objects.requireNonNull(owner);
        if (!owner.isEnabled()) throw new IllegalArgumentException("Owner is disabled");
        var batch = List.copyOf(additions);
        Set<StatisticKey> keys = new HashSet<>(displays.keySet());
        for (var display : batch) {
            validateGame(display.key().gameId());
            if (!keys.add(display.key()))
                throw new IllegalArgumentException("Duplicate statistic display: " + display.key());
        }
        for (var display : batch) {
            displays.put(display.key(), new OwnedDisplay(owner, display));
        }
    }
    /** @return 指定已注册游戏的自定义展示项只读快照，按注册顺序排列；未注册展示项时为空 */
    public List<StatisticDisplay> getDisplays(String gameId) {
        validateGame(gameId);
        return displays.values().stream().map(OwnedDisplay::display)
                .filter(display -> display.key().gameId().equals(gameId)).toList();
    }
    /** 移除子插件的全部统计展示定义，不修改任何玩家数据。 */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        AchievementStorage.requireMainThread();
        displays.values().removeIf(entry -> entry.owner() == event.getPlugin());
    }

    private void validate(String gameId, GameStatistic stat) {
        validateGame(gameId);
        Objects.requireNonNull(stat);
    }
    private void validateGame(String gameId) {
        AchievementStorage.requireMainThread();
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId))
            throw new IllegalArgumentException("Unknown game: " + gameId);
    }
    /** @return 指定玩家/游戏/统计的当前值，无记录为 0；未知游戏抛异常 */
    public long get(UUID playerId, String gameId, GameStatistic statistic) {
        validate(gameId, statistic);
        return storage.statistic(playerId, gameId, statistic);
    }
    /** 增加或扣减统计，结果不得小于零或溢出；返回最新值。 */
    public long add(UUID playerId, String gameId, GameStatistic statistic, long amount) {
        return set(playerId, gameId, statistic, Math.addExact(get(playerId, gameId, statistic), amount));
    }
    /** 设置非负绝对值，降低统计不撤销已解锁成就；返回最新值。 */
    public long set(UUID playerId, String gameId, GameStatistic statistic, long value) {
        validate(gameId, statistic);
        if (!storage.hasLoggedIn(playerId)) throw new IllegalArgumentException("Player has never logged into this server");
        return change(playerId, gameId, statistic, value, GameStatisticChangedEvent.Source.API);
    }
    private long change(UUID id, String game, GameStatistic stat, long value, GameStatisticChangedEvent.Source source) {
        if (value < 0) throw new IllegalArgumentException("Statistic cannot be negative");
        long old = storage.statistic(id, game, stat);
        if (old == value) return value;
        storage.statistic(id, game, stat, value);
        Bukkit.getPluginManager().callEvent(new GameStatisticChangedEvent(id, game, stat, old, value, source));
        return value;
    }
    private void increment(UUID id, String game, GameStatistic stat, GameStatisticChangedEvent.Source source) {
        long old = storage.statistic(id, game, stat);
        if (old < Long.MAX_VALUE) change(id, game, stat, old + 1, source);
    }
    /**
     * 主动报告自定义死亡，必须在移除受害者/切换旁观前调用。
     * 同一实例、受害者、服务器 tick 最多处理一次，与自动采集共用去重。
     * @param game 正在运行的游戏
     * @param victimId 本局有效参赛者
     * @param killerId 击杀者，可为 null；事件可修正归因
     * @return 是否受理；受理不代表两个统计事件都未被取消
     */
    public boolean reportDeath(AbstractGame game, UUID victimId, UUID killerId) {
        return reportDeath(game, victimId, killerId, GameStatisticChangedEvent.Source.REPORTED_DEATH);
    }
    private boolean reportDeath(AbstractGame game, UUID victim, UUID killer, GameStatisticChangedEvent.Source source) {
        AchievementStorage.requireMainThread();
        if (game.getState() != GameState.RUNNING || !eligible(game, victim)) return false;
        int tick = Bukkit.getCurrentTick();
        if (tick != reportTick) { reported.clear(); reportTick = tick; }
        if (!reported.add(new DeathKey(game, victim))) return false;
        var death = new GameDeathEvent(game, victim);
        var kill = new GameKillEvent(game, victim, killer);
        Bukkit.getPluginManager().callEvent(death);
        Bukkit.getPluginManager().callEvent(kill);
        UUID credited = kill.getKillerId();
        // 在提交前捕获资格，避免死亡统计的通知回调结束游戏后丢失击杀。
        boolean validKill = !kill.isCancelled() && credited != null && !credited.equals(victim) && eligible(game, credited);
        if (!death.isCancelled()) collect(game, victim, GameStatistic.DEATHS, source);
        if (validKill) collect(game, credited, GameStatistic.KILLS, source);
        return true;
    }
    private boolean eligible(AbstractGame game, UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) return false;
        PlayerExt ext = new PlayerExt(player);
        return ext.getGame() == game && isParticipant(ext);
    }
    /** 同一实例每位玩家只计一次参赛，过滤被取消时不记为已参与。 */
    private void recordParticipation(AbstractGame game, PlayerExt player) {
        AchievementStorage.requireMainThread();
        if (game.getState() != GameState.RUNNING || player.getGame() != game || !isParticipant(player)) return;
        var seen = participants.computeIfAbsent(game, ignored -> new HashSet<>());
        if (!seen.contains(player.getUniqueId())
                && collect(game, player.getUniqueId(), GameStatistic.GAMES_PLAYED, GameStatisticChangedEvent.Source.AUTOMATIC))
            seen.add(player.getUniqueId());
    }
    /** 每个未取消结算事件累计一次；子插件负责只上报一次，允许离线结算。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResult(GameResultEvent event) {
        AchievementStorage.requireMainThread();
        GameStatistic statistic = switch (event.getOutcome()) {
            case WIN -> GameStatistic.WINS;
            case LOSS -> GameStatistic.LOSSES;
            case DRAW -> GameStatistic.DRAWS;
        };
        validate(event.getGameId(), statistic);
        if (event.isCancelled() || !storage.hasLoggedIn(event.getPlayerId())) return;
        increment(event.getPlayerId(), event.getGameId(), statistic, GameStatisticChangedEvent.Source.API);
    }
    /** 监听成功开局，业务生命周期无需依赖统计管理器。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(GameStartEvent event) {
        event.getPlayers().forEach(player -> recordParticipation(event.getGame(), player));
    }
    /** 监听成功中途加入。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerGameJoinEvent event) {
        recordParticipation(event.getGame(), event.getPlayer());
    }
    /** 监听核心死亡通知，再提供可过滤的击杀/死亡采集。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerGameDeathEvent event) {
        reportDeath(event.getGame(), event.getPlayer().getUniqueId(), event.getKillerId(), GameStatisticChangedEvent.Source.AUTOMATIC);
    }
    private boolean isParticipant(PlayerExt player) {
        return player.player().isOnline() && player.player().getGameMode() != GameMode.SPECTATOR;
    }
    private boolean collect(AbstractGame game, UUID player, GameStatistic statistic, GameStatisticChangedEvent.Source source) {
        var event = new GameStatisticCollectEvent(game, player, statistic, source);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        increment(player, game.getId(), statistic, source);
        return true;
    }
    /** 核心每 tick 调用；每 20 个有效 tick 计一秒，等待/离线/旁观不计时。 */
    public void tick() {
        AchievementStorage.requireMainThread();
        Set<UUID> active = new HashSet<>();
        for (AbstractGame game : List.copyOf(MCZJUGameCore.getGameManager().getAllGames())) {
            if (game.getState() != GameState.RUNNING) continue;
            for (PlayerExt player : List.copyOf(game.getPlayers())) {
                UUID id = player.getUniqueId();
                if (game.getState() != GameState.RUNNING || player.getGame() != game || !isParticipant(player)) continue;
                active.add(id);
                Clock previous = clocks.get(id);
                int ticks = previous != null && previous.game() == game ? previous.ticks() + 1 : 1;
                clocks.put(id, new Clock(game, ticks % 20));
                if (ticks == 20) collect(game, id, GameStatistic.PLAY_TIME_SECONDS, GameStatisticChangedEvent.Source.AUTOMATIC);
            }
        }
        clocks.keySet().retainAll(active);
        // 不跨 tick 持有结束游戏引用。
        if (reportTick != Bukkit.getCurrentTick()) reported.clear();
    }
}
