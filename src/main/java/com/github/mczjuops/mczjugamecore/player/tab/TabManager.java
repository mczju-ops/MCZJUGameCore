package com.github.mczjuops.mczjugamecore.player.tab;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.game.GameState;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 全服统一的 Tab 展示管理器，不修改页眉、页脚或实体可见性。
 * 所有方法须在服务器主线程调用；修改按游戏实例归属，退出或结束时自动清理。
 * 安装 PacketEvents 2.14.0 或更新的兼容版本时，实体隐藏不影响 Tab，仅隐藏 API 控制条目。
 * 未安装时前后缀仍可用，数据包操作不生效并在调用相关 API 时提示控制台。
 */
public final class TabManager implements Listener {
    private final Map<UUID, Decoration> decorations = new HashMap<>();
    private final Map<AbstractGame, Set<UUID>> hiddenPlayers = new IdentityHashMap<>();
    private final @Nullable TabPacketBackend backend;
    private final Set<UUID> disconnecting = new HashSet<>();
    private final Set<UUID> managedNames = new HashSet<>();
    private BukkitTask pendingRefresh;
    private boolean closed;

    /** 创建核心管理器并按需启用可选数据包后端，仅由核心初始化调用。 */
    @ApiStatus.Internal
    public TabManager() {
        backend = Bukkit.getPluginManager().isPluginEnabled("packetevents")
                ? new PacketEventsTabBackend() : null;
    }

    /**
     * 设置玩家名前缀，不自动插入空格。
     * @param game 玩家所属的活动游戏实例
     * @param player 本局在线玩家
     * @param prefix 前缀；null 恢复无前缀，空 Component 表示空前缀
     * @throws IllegalArgumentException 玩家不属于本局或游戏已结束
     * @throws IllegalStateException 不在主线程调用
     */
    public void setPrefix(AbstractGame game, PlayerExt player, @Nullable Component prefix) {
        Decoration decoration = decoration(game, player);
        decoration.prefix = prefix;
        refresh();
    }

    /**
     * 使用 MiniMessage 字符串设置玩家名前缀，推荐使用此重载；不自动插入空格。
     * @param game 玩家所属的活动游戏实例
     * @param player 本局在线玩家
     * @param prefix 非 null 的 MiniMessage 前缀；空字符串恢复无前缀
     * @throws NullPointerException prefix 为 null
     * @throws IllegalArgumentException 玩家不属于本局或游戏已结束
     * @throws IllegalStateException 不在主线程调用
     */
    public void setPrefix(AbstractGame game, PlayerExt player, String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        setPrefix(game, player, prefix.isEmpty() ? (Component) null : TextParser.parse(prefix));
    }

    /**
     * 替换核心默认的游戏名后缀，不自动插入空格。
     * @param game 玩家所属的活动游戏实例
     * @param player 本局在线玩家
     * @param suffix 后缀；null 恢复默认游戏名后缀，空 Component 清除后缀
     * @throws IllegalArgumentException 玩家不属于本局或游戏已结束
     * @throws IllegalStateException 不在主线程调用
     */
    public void setSuffix(AbstractGame game, PlayerExt player, @Nullable Component suffix) {
        Decoration decoration = decoration(game, player);
        decoration.suffix = suffix;
        refresh();
    }

    /**
     * 使用 MiniMessage 字符串替换默认游戏名后缀，推荐使用此重载；不自动插入空格。
     * @param game 玩家所属的活动游戏实例
     * @param player 本局在线玩家
     * @param suffix 非 null 的 MiniMessage 后缀；空字符串恢复默认游戏名后缀
     * @throws NullPointerException suffix 为 null
     * @throws IllegalArgumentException 玩家不属于本局或游戏已结束
     * @throws IllegalStateException 不在主线程调用
     */
    public void setSuffix(AbstractGame game, PlayerExt player, String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        setSuffix(game, player, suffix.isEmpty() ? (Component) null : TextParser.parse(suffix));
    }

    /**
     * 控制旁观者在所有人的 Tab 中是否使用普通玩家样式，不改变实际游戏模式。
     * 此操作保留已有前缀和后缀，后续游戏模式切换也不会清除它们。
     * 缺少 PacketEvents 时不修改设置、不抛依赖异常，并在调用相关 API 时提示控制台。
     * @param game 玩家所属的活动游戏实例
     * @param player 本局在线玩家
     * @param normal true 取消旁观者变淡，false 恢复原版样式
     * @throws IllegalArgumentException 玩家不属于本局或游戏已结束
     * @throws IllegalStateException 非主线程调用
     */
    public void setNormalAppearance(AbstractGame game, PlayerExt player, boolean normal) {
        checkPlayer(game, player);
        if (!packetBackendAvailable("旁观者 Tab 样式修改（setNormalAppearance）")) return;
        decoration(game, player).normal = normal;
        refresh();
    }

    /**
     * 设置指定玩家是否在全服 Tab 中隐藏，不影响实体可见性。
     * 多局隐藏名单取并集；false 仅撤销本局的隐藏请求，其他局仍可隐藏此玩家。
     * 缺少 PacketEvents 时不修改设置、不抛依赖异常，并在调用相关 API 时提示控制台。
     * @param game 隐藏请求所属的活动游戏实例
     * @param player 实际在线玩家，允许其他局或大厅玩家
     * @param hidden true 隐藏，false 撤销本局对该玩家的隐藏
     * @throws IllegalArgumentException 游戏非活动实例或玩家离线
     * @throws NullPointerException game 或 player 为 null
     * @throws IllegalStateException 非主线程调用
     */
    public void setHiddenPlayer(AbstractGame game, PlayerExt player, boolean hidden) {
        checkGame(game);
        UUID id = onlineId(player);
        if (!packetBackendAvailable("隐藏 Tab 玩家（setHiddenPlayer）")) return;
        if (hidden) hiddenPlayers.computeIfAbsent(game, key -> new HashSet<>()).add(id);
        else removeHiddenPlayer(game, id);
        refresh();
    }

    /**
     * 替换本局的全服 Tab 隐藏名单，不影响其他局的隐藏请求或实体可见性。
     * 集合调用时复制、重复玩家合并；空集合撤销本局的全部隐藏请求。
     * 启用 PacketEvents 时，未被任何游戏隐藏的在线玩家均显示，包括被 Paper 隐藏实体的玩家。
     * 缺少 PacketEvents 时不修改设置、不抛依赖异常，并在调用相关 API 时提示控制台。
     * @param game 隐藏名单所属的活动游戏实例
     * @param players 要隐藏的实际在线玩家，允许其他局或大厅玩家，不表示排列顺序
     * @throws IllegalArgumentException 游戏非活动实例或集合包含离线玩家
     * @throws NullPointerException 参数或集合元素为 null
     * @throws IllegalStateException 非主线程调用
     */
    public void setHiddenPlayers(AbstractGame game, Collection<PlayerExt> players) {
        checkGame(game);
        Objects.requireNonNull(players, "players");
        Set<UUID> ids = new HashSet<>();
        for (PlayerExt player : players) ids.add(onlineId(player));
        if (!packetBackendAvailable("批量隐藏 Tab 玩家（setHiddenPlayers）")) return;
        if (ids.isEmpty()) hiddenPlayers.remove(game);
        else hiddenPlayers.put(game, ids);
        refresh();
    }

    /**
     * 清除指定游戏对玩家的前后缀、样式及隐藏请求；重复调用无副作用。
     * @param game 修改所属的游戏实例
     * @param player 要恢复的玩家，允许已退出本局
     * @throws IllegalStateException 非主线程调用
     */
    public void resetPlayer(AbstractGame game, PlayerExt player) {
        checkThread();
        Objects.requireNonNull(game, "game");
        UUID id = Objects.requireNonNull(player, "player").player().getUniqueId();
        Decoration value = decorations.get(id);
        if (value != null && value.game == game) decorations.remove(id);
        removeHiddenPlayer(game, id);
        refresh();
    }

    /**
     * 撤销本局的全部 Tab 隐藏请求；其他局仍隐藏的玩家保持隐藏。
     * 缺少 PacketEvents 时不生效，并在每次调用时输出控制台提示。
     * @param game 名单所属实例，重复调用无副作用
     * @throws IllegalStateException 非主线程调用
     */
    public void resetHiddenPlayers(AbstractGame game) {
        checkThread();
        Objects.requireNonNull(game, "game");
        if (!packetBackendAvailable("恢复 Tab 隐藏名单（resetHiddenPlayers）")) return;
        hiddenPlayers.remove(game);
        refresh();
    }

    /**
     * 清除玩家在本局的全部修改及隐藏请求，核心退出流程调用。
     * @param game 原游戏实例
     * @param player 退出的玩家
     */
    @ApiStatus.Internal
    public void onPlayerLeave(AbstractGame game, PlayerExt player) {
        checkThread();
        UUID id = player.player().getUniqueId();
        Decoration value = decorations.get(id);
        if (value != null && value.game == game) decorations.remove(id);
        removeHiddenPlayer(game, id);
        refresh();
    }

    /**
     * 清除本局所有修改及隐藏请求，核心结束和销毁流程调用。
     * @param game 已结束的游戏实例
     */
    @ApiStatus.Internal
    public void onGameEnd(AbstractGame game) {
        checkThread();
        decorations.values().removeIf(value -> value.game == game);
        hiddenPlayers.remove(game);
        refresh();
    }

    /**
     * 刷新在线玩家展示，核心加入流程调用；不会触碰页眉或页脚。
     * 默认游戏名保留 GameMeta 的 MiniMessage 颜色，未指定颜色时为白色，玩家名和括号仍为橙色。
     */
    @ApiStatus.Internal
    public void refresh() {
        checkThread();
        if (closed) return;
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.removeIf(player -> disconnecting.contains(player.getUniqueId()));
        Set<UUID> normal = new HashSet<>();
        for (Player player : online) {
            AbstractGame game = new PlayerExt(player).getGame();
            if (game != null && game.getState() == GameState.END) game = null;
            Decoration decoration = decorations.get(player.getUniqueId());
            if (decoration != null && decoration.game != game) decoration = null;
            if (game == null && decoration == null) {
                if (managedNames.remove(player.getUniqueId())) player.playerListName(null);
                continue;
            }
            managedNames.add(player.getUniqueId());
            Component name = game == null ? Component.text(player.getName())
                    : TextParser.parse("<#DEB12D>" + player.getName());
            Component suffix = game == null ? Component.empty()
                    : Component.empty()
                            .append(TextParser.parse("<#DEB12D> ["))
                            .append(Component.empty().color(NamedTextColor.WHITE)
                                    .append(TextParser.parse(game.getGameMeta().displayName())))
                            .append(TextParser.parse("<#DEB12D>]"));
            if (decoration != null) {
                if (decoration.prefix != null) name = Component.empty().append(decoration.prefix).append(name);
                if (decoration.suffix != null) suffix = decoration.suffix;
                if (decoration.normal) normal.add(player.getUniqueId());
            }
            player.playerListName(name.append(suffix));
        }
        Set<UUID> hidden = new HashSet<>();
        hiddenPlayers.values().forEach(hidden::addAll);
        if (backend != null) backend.refresh(online, Set.copyOf(hidden), Set.copyOf(normal));
    }

    /** 核心关闭时恢复展示并解除数据包监听。 */
    @ApiStatus.Internal
    public void shutdown() {
        checkThread();
        if (pendingRefresh != null) pendingRefresh.cancel();
        decorations.clear();
        hiddenPlayers.clear();
        refresh();
        for (UUID id : managedNames) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.playerListName(null);
        }
        managedNames.clear();
        closed = true;
        if (backend != null) backend.close();
    }

    /**
     * 玩家登录后同步全服名单。
     * @param event 登录事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        disconnecting.remove(event.getPlayer().getUniqueId());
        scheduleRefresh();
    }

    /**
     * 离线时清除所有名单中的引用，不在重新登录时自动恢复。
     * @param event 离线事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        disconnecting.add(id);
        decorations.remove(id);
        managedNames.remove(id);
        hiddenPlayers.values().forEach(ids -> ids.remove(id));
        hiddenPlayers.values().removeIf(Set::isEmpty);
        refresh();
        if (backend != null) backend.forget(id);
        scheduleRefresh();
    }

    /**
     * 模式切换后同步样式。
     * @param event 游戏模式事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) { scheduleRefresh(); }

    /**
     * 隐藏玩家后补齐 Tab 条目。
     * @param event 隐藏事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHide(PlayerHideEntityEvent event) {
        if (backend != null && event.getEntity() instanceof Player) scheduleRefresh();
    }

    /**
     * 恢复玩家实体后保持 Tab 隐藏请求。
     * @param event 恢复事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onShow(PlayerShowEntityEvent event) {
        if (event.getEntity() instanceof Player) scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (closed || pendingRefresh != null) return;
        pendingRefresh = Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            pendingRefresh = null;
            disconnecting.removeIf(id -> Bukkit.getPlayer(id) == null);
            refresh();
        });
    }

    private Decoration decoration(AbstractGame game, PlayerExt player) {
        checkPlayer(game, player);
        return decorations.computeIfAbsent(player.player().getUniqueId(), id -> new Decoration(game));
    }

    private void checkPlayer(AbstractGame game, PlayerExt player) {
        checkGame(game);
        Objects.requireNonNull(player, "player");
        if (!player.player().isOnline() || disconnecting.contains(player.player().getUniqueId())
                || player.getGame() != game) {
            throw new IllegalArgumentException("只能修改本局在线玩家的 Tab 展示");
        }
    }

    private void checkGame(AbstractGame game) {
        checkThread();
        Objects.requireNonNull(game, "game");
        if (game.getState() == GameState.END
                || MCZJUGameCore.getGameManager().getAllGames().stream().noneMatch(active -> active == game)) {
            throw new IllegalArgumentException("只能为活动游戏实例设置 Tab 展示");
        }
    }

    private void checkThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tab API 必须在服务器主线程调用");
        if (closed) throw new IllegalStateException("TabManager 已关闭");
    }

    private boolean packetBackendAvailable(String operation) {
        if (backend != null) return true;
        warnMissingBackend(operation);
        return false;
    }

    private void warnMissingBackend(String operation) {
        MCZJUGameCore.getInstance().getLogger().warning(
                "未安装或未启用 PacketEvents：" + operation + "未生效。前后缀仍可用，请安装兼容的 PacketEvents 2.14.0+。");
    }

    private UUID onlineId(PlayerExt player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.player().getUniqueId();
        if (!player.player().isOnline() || disconnecting.contains(id)) {
            throw new IllegalArgumentException("Tab 隐藏名单只能包含在线玩家");
        }
        return id;
    }

    private void removeHiddenPlayer(AbstractGame game, UUID id) {
        Set<UUID> ids = hiddenPlayers.get(game);
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) hiddenPlayers.remove(game);
        }
    }

    private static final class Decoration {
        private final AbstractGame game;
        private Component prefix;
        private Component suffix;
        private boolean normal;
        private Decoration(AbstractGame game) { this.game = game; }
    }

}
