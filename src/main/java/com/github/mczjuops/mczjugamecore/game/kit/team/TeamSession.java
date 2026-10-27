package com.github.mczjuops.mczjugamecore.game.kit.team;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.game.AbstractGame;
import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.VoiceGroupUtil;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 单局队伍会话：只负责"谁在哪一支队"以及队伍相关的通用表现。
 *
 * <p><b>分队规则留在游戏里。</b>本类不猜人数怎么分、也不做自动平衡：
 * 游戏（或管理员指令）决定谁进哪支队伍，调用 {@link #assign(PlayerExt, GameTeam)} 登记即可。
 * 因此它同时适用于"等待阶段让玩家自己点选"、"管理员/房主指定"和"脚本硬编码分队"。
 *
 * <p>登记的队员会得到三项通用能力，均可单独关闭：
 * <ul>
 *   <li>同队免伤：核心注册的监听器读取本会话，取消同队玩家之间的直接/弹射物/爆炸伤害
 *       （{@link #friendlyFire(boolean)}）；</li>
 *   <li>Tab 队伍前缀：按队伍显示名着色，走核心 {@code TabManager}，退出/结束自动清理
 *       （{@link #tabPrefixes(boolean)}）；</li>
 *   <li>队伍语音组：每支队伍一个 Simple Voice Chat 群组，成员变化后调用
 *       {@link #syncVoiceGroups()} 重建（{@link #voiceGroups(boolean)}）。</li>
 * </ul>
 *
 * <p>生命周期：每局创建一个实例（通常在 {@code onGameStart()}），
 * 在 {@code onGameEnd()} / {@code onGameAbort()} / {@code onGameCancel()} 中调用
 * {@link #reset()}。所有方法必须在服务器主线程调用。
 */
public final class TeamSession {

    private static final Map<AbstractGame, TeamSession> SESSIONS = new IdentityHashMap<>();

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));
    private final AbstractGame game;
    private final List<GameTeam> teams;
    private final Map<UUID, GameTeam> assignments = new HashMap<>();
    private final Map<GameTeam, UUID> voiceGroups = new HashMap<>();

    private boolean friendlyFireProtected = true;
    private boolean tabPrefixEnabled = true;
    private boolean voiceGroupsEnabled = true;

    /**
     * 创建本局队伍会话；同一游戏实例重复创建时，旧会话会先被 {@link #reset()} 收拾掉。
     * @param game 所属游戏实例
     * @param teams 本局可用的队伍定义，至少一支且 id 不重复
     * @throws IllegalArgumentException 队伍列表为空或 id 重复
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public TeamSession(AbstractGame game, Collection<GameTeam> teams) {
        requireMainThread();
        this.game = Objects.requireNonNull(game, "game");
        Objects.requireNonNull(teams, "teams");
        if (teams.isEmpty()) throw new IllegalArgumentException("至少需要一支队伍");
        List<GameTeam> copy = List.copyOf(teams);
        Set<String> ids = new HashSet<>();
        for (GameTeam team : copy) {
            if (!ids.add(team.id())) throw new IllegalArgumentException("队伍 id 重复：" + team.id());
        }
        this.teams = copy;

        TeamSession previous = SESSIONS.put(game, this);
        if (previous != null) previous.reset();
    }

    /** @return 本会话所属的游戏实例 */
    public AbstractGame game() {
        return game;
    }

    /** @return 本局的队伍定义（不可变） */
    public List<GameTeam> teams() {
        return teams;
    }

    /**
     * 按 id 查找队伍定义。
     * @param id 队伍 id
     * @return 对应队伍，不存在时返回 null
     */
    public @Nullable GameTeam teamById(@Nullable String id) {
        if (id == null) return null;
        for (GameTeam team : teams) {
            if (team.id().equals(id)) return team;
        }
        return null;
    }

    // ==================== 手动分配与查询 ====================

    /**
     * 把本局在线玩家登记到指定队伍；重复调用会覆盖原队伍。
     * @param player 本局在线玩家
     * @param team 本局声明的队伍之一
     * @throws IllegalArgumentException 玩家不在本局、已离线，或队伍不属于本局
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void assign(PlayerExt player, GameTeam team) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(team, "team");
        if (!teams.contains(team)) throw new IllegalArgumentException("本局没有队伍：" + team.id());
        Player p = player.player();
        if (!p.isOnline() || player.getGame() != game) {
            throw new IllegalArgumentException("只能给本局在线玩家分配队伍");
        }
        assignments.put(p.getUniqueId(), team);
        if (tabPrefixEnabled) safeApplyTabPrefix(player, team);
    }

    /**
     * 批量登记到同一支队伍，等价于逐个调用 {@link #assign(PlayerExt, GameTeam)}。
     * @param players 本局在线玩家
     * @param team 目标队伍
     */
    public void assignAll(Collection<PlayerExt> players, GameTeam team) {
        Objects.requireNonNull(players, "players");
        for (PlayerExt player : players) assign(player, team);
    }

    /**
     * 清空玩家的队伍登记并撤销其 Tab 前缀；未登记时无副作用。
     * @param player 玩家
     */
    public void unassign(PlayerExt player) {
        requireMainThread();
        if (player == null) return;
        if (assignments.remove(player.getUniqueId()) == null) return;
        safeResetTab(player);
    }

    /**
     * 查询玩家所在队伍。
     * @param uuid 玩家 UUID
     * @return 所在队伍；未登记返回 null
     */
    public @Nullable GameTeam teamOf(@Nullable UUID uuid) {
        return uuid == null ? null : assignments.get(uuid);
    }

    /**
     * 查询玩家所在队伍。
     * @param player 玩家
     * @return 所在队伍；未登记返回 null
     */
    public @Nullable GameTeam teamOf(@Nullable Player player) {
        return player == null ? null : teamOf(player.getUniqueId());
    }

    /**
     * 查询玩家所在队伍。
     * @param player 玩家扩展对象
     * @return 所在队伍；未登记返回 null
     */
    public @Nullable GameTeam teamOf(@Nullable PlayerExt player) {
        return player == null ? null : teamOf(player.getUniqueId());
    }

    /** @return 玩家是否已登记队伍 */
    public boolean hasTeam(UUID uuid) {
        return uuid != null && assignments.containsKey(uuid);
    }

    /**
     * 队伍成员 UUID（含已离线玩家，顺序不保证）。
     * @param team 队伍
     * @return 成员 UUID 的不可变副本
     */
    public Set<UUID> membersOf(GameTeam team) {
        Set<UUID> members = new LinkedHashSet<>();
        assignments.forEach((uuid, assigned) -> {
            if (assigned.equals(team)) members.add(uuid);
        });
        return Collections.unmodifiableSet(members);
    }

    /**
     * 队伍中仍在线的本局成员。
     * @param team 队伍
     * @return 在线成员列表
     */
    public List<PlayerExt> onlineMembersOf(GameTeam team) {
        List<PlayerExt> members = new ArrayList<>();
        for (UUID uuid : membersOf(team)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) members.add(new PlayerExt(p));
        }
        return members;
    }

    /**
     * 两名玩家是否同属一队（仅比较本会话的登记）。
     * @param first 玩家一
     * @param second 玩家二
     * @return 两人都已登记且同队时为 true
     */
    public boolean isTeammate(@Nullable Player first, @Nullable Player second) {
        return first != null && second != null
                && isTeammate(first.getUniqueId(), second.getUniqueId());
    }

    /**
     * 两名玩家是否同属一队（仅比较本会话的登记）。
     * @param first 玩家一 UUID
     * @param second 玩家二 UUID
     * @return 两人都已登记且同队时为 true
     */
    public boolean isTeammate(@Nullable UUID first, @Nullable UUID second) {
        if (first == null || second == null) return false;
        GameTeam team = assignments.get(first);
        return team != null && team.equals(assignments.get(second));
    }

    /** @return 已登记队伍的玩家数量（含离线） */
    public int size() {
        return assignments.size();
    }

    // ==================== 通用能力开关 ====================

    /**
     * 开关同队免伤（默认开启）。
     * @param protectedSameTeam true 时同队玩家之间的伤害会被核心监听器取消
     * @return 本会话，便于链式配置
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public TeamSession friendlyFire(boolean protectedSameTeam) {
        requireMainThread();
        this.friendlyFireProtected = protectedSameTeam;
        return this;
    }

    /** @return 是否启用同队免伤 */
    public boolean friendlyFireProtected() {
        return friendlyFireProtected;
    }

    /**
     * 开关 Tab 队伍前缀（默认开启）；切换为关闭时立即撤销已有前缀。
     * @param enabled 是否启用
     * @return 本会话，便于链式配置
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public TeamSession tabPrefixes(boolean enabled) {
        requireMainThread();
        this.tabPrefixEnabled = enabled;
        if (enabled) refreshTabPrefixes();
        else resetTabPrefixes();
        return this;
    }

    /**
     * 开关队伍语音组（默认开启）；关闭时会立即移除本会话创建的全部语音组。
     * @param enabled 是否启用
     * @return 本会话，便于链式配置
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public TeamSession voiceGroups(boolean enabled) {
        requireMainThread();
        this.voiceGroupsEnabled = enabled;
        if (!enabled) cleanupVoiceGroups();
        return this;
    }

    /** @return 是否启用队伍语音组 */
    public boolean voiceGroupsEnabled() {
        return voiceGroupsEnabled;
    }

    // ==================== Tab 前缀 ====================

    /**
     * 按玩家当前队伍刷新其 Tab 前缀；未登记队伍时会撤销前缀。
     * @param player 本局在线玩家
     */
    public void applyTabPrefix(PlayerExt player) {
        requireMainThread();
        if (player == null || !tabPrefixEnabled) return;
        GameTeam team = teamOf(player);
        if (team == null) safeResetTab(player);
        else safeApplyTabPrefix(player, team);
    }

    /** 按当前登记刷新全部在线成员的 Tab 前缀。 */
    public void refreshTabPrefixes() {
        requireMainThread();
        if (!tabPrefixEnabled) return;
        for (UUID uuid : List.copyOf(assignments.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) applyTabPrefix(new PlayerExt(p));
        }
    }

    /** 撤销全部在线成员的 Tab 前缀。 */
    public void resetTabPrefixes() {
        requireMainThread();
        for (UUID uuid : List.copyOf(assignments.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) safeResetTab(new PlayerExt(p));
        }
    }

    // ==================== 队伍语音组 ====================

    /**
     * 按当前成员重建每支队伍的语音组：先移除旧组，再给有在线成员的队伍各建一个新组。
     * 成员变化（开局、换队、重连、离场）后调用一次即可，重复调用不会残留旧组。
     * 语音插件不可用时静默跳过（{@link VoiceGroupUtil#isAvailable()} 为 false）。
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void syncVoiceGroups() {
        requireMainThread();
        cleanupVoiceGroups();
        if (!voiceGroupsEnabled || !VoiceGroupUtil.isAvailable()) return;

        String roomName = game.getGameRoom() == null ? null : game.getGameRoom().getRoomName();
        for (GameTeam team : teams) {
            List<PlayerExt> members = onlineMembersOf(team);
            if (members.isEmpty()) continue;
            String groupName = roomName == null || roomName.isBlank()
                    ? team.plainName()
                    : "%s-%s".formatted(team.plainName(), roomName);
            try {
                VoiceGroupUtil.createGroup(groupName, members)
                        .ifPresent(groupId -> voiceGroups.put(team, groupId));
            } catch (RuntimeException e) {
                // 第三方语音插件拒绝建组时不影响游戏流程，只记录一次提示
                logger.warn("创建队伍 %s 的语音组失败：%s".formatted(team.plainName(), e.getMessage()));
            }
        }
    }

    /**
     * 查询队伍当前的语音组。
     * @param team 队伍
     * @return 语音组 UUID；未创建时为 null
     */
    public @Nullable UUID voiceGroupOf(GameTeam team) {
        return team == null ? null : voiceGroups.get(team);
    }

    /** 移除本会话创建的全部语音组；未安装语音插件或未建组时无副作用。 */
    public void cleanupVoiceGroups() {
        requireMainThread();
        for (UUID groupId : voiceGroups.values()) {
            try {
                VoiceGroupUtil.removeGroup(groupId);
            } catch (RuntimeException e) {
                logger.warn("移除队伍语音组 %s 失败：%s".formatted(groupId, e.getMessage()));
            }
        }
        voiceGroups.clear();
    }

    // ==================== 生命周期 ====================

    /**
     * 玩家离场（退出、掉线）时清理其登记与 Tab 修改；队伍语音组需由游戏在成员变化后
     * 调用 {@link #syncVoiceGroups()} 重建。
     * @param player 离场玩家
     */
    public void onPlayerQuit(PlayerExt player) {
        requireMainThread();
        if (player == null) return;
        if (assignments.remove(player.getUniqueId()) != null) safeResetTab(player);
    }

    /**
     * 结束本局：撤销全部 Tab 前缀、移除语音组、清空登记并注销会话。幂等。
     * @throws IllegalStateException 不在服务器主线程调用
     */
    public void reset() {
        requireMainThread();
        resetTabPrefixes();
        assignments.clear();
        cleanupVoiceGroups();
        if (SESSIONS.get(game) == this) SESSIONS.remove(game);
    }

    /**
     * 按游戏实例取回会话。
     * @param game 游戏实例
     * @return 该实例的队伍会话；未创建时为 null
     */
    public static @Nullable TeamSession of(@Nullable AbstractGame game) {
        return game == null ? null : SESSIONS.get(game);
    }

    /**
     * 按玩家当前所在游戏取回会话（比较的是具体游戏实例，不同房间互不影响）。
     * @param player 在线玩家
     * @return 该玩家所在游戏实例的队伍会话；玩家不在游戏中时为 null
     */
    public static @Nullable TeamSession ofPlayer(@Nullable Player player) {
        return player == null ? null : of(new PlayerExt(player).getGame());
    }

    /** @return 当前活动的全部会话，供核心的离场清理监听器使用 */
    static Collection<TeamSession> activeSessions() {
        return List.copyOf(SESSIONS.values());
    }

    // ==================== 内部 ====================

    private void safeApplyTabPrefix(PlayerExt player, GameTeam team) {
        try {
            MCZJUGameCore.getTabManager().setPrefix(game, player, team.tabPrefix());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 游戏已结束或未注册时 Tab 管理器会拒绝修改；队伍数据本身仍然有效
            logger.debug("无法为 %s 设置队伍 Tab 前缀：%s".formatted(player.getName(), e.getMessage()));
        }
    }

    private void safeResetTab(PlayerExt player) {
        try {
            MCZJUGameCore.getTabManager().resetPlayer(game, player);
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.debug("无法撤销 %s 的队伍 Tab 前缀：%s".formatted(player.getName(), e.getMessage()));
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("队伍会话必须在服务器主线程调用");
    }
}
