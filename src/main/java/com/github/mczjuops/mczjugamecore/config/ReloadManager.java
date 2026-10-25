package com.github.mczjuops.mczjugamecore.config;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.Bukkit;
import com.github.mczjuops.mczjugamecore.game.room.AbstractGameRoom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** 统一配置重载入口；注册和执行均限服务器线程，按注册顺序执行并隔离各项失败。 */
public final class ReloadManager {
    private final Map<String, ReloadAction> modules = new LinkedHashMap<>();
    private final Map<String, Class<? extends AbstractGameRoom>> games = new LinkedHashMap<>();
    private final Map<String, ReloadAction> gameConfigs = new LinkedHashMap<>();

    /** 可抛出异常的重载操作；先验证再提交，失败时由实现保留原有效配置。 */
    @FunctionalInterface
    public interface ReloadAction {
        /** @throws Exception 读取、验证或应用失败；异常会记录并转换为失败结果 */
        void reload() throws Exception;
    }

    /**
     * 一次独立重载的结果。
     * @param target 模块名或 game 加游戏 ID
     * @param success 是否成功
     * @param message 生效边界或失败原因
     */
    public record ReloadResult(String target, boolean success, String message) {}

    /**
     * 注册独立模块（自动纳入补全和无参数重载）。
     * @param id 小写字母、数字、下划线或短横线组成的唯一名称；game 保留
     * @param action 只读取该模块的配置，不隐式重载其他模块
     * @throws IllegalArgumentException 名称非法或重复
     * @throws IllegalStateException 不在服务器线程
     */
    public void register(String id, ReloadAction action) {
        requireServerThread();
        if (id == null || !id.matches("[a-z][a-z0-9_-]*") || id.equals("game")) {
            throw new IllegalArgumentException("无效的重载模块名：" + id);
        }
        if (modules.putIfAbsent(id, Objects.requireNonNull(action)) != null) {
            throw new IllegalArgumentException("重载模块已注册：" + id);
        }
    }

    /**
     * 为已注册的游戏登记房间类型，由核心在注册游戏时调用。
     * @param gameId 精确匹配的已注册游戏 ID
     * @param roomClass 该游戏的房间类型；默认仅支持 JSON 房间
     * @throws IllegalArgumentException 游戏未注册或重复登记
     * @throws IllegalStateException 不在服务器线程
     */
    public void registerGame(String gameId, Class<? extends AbstractGameRoom> roomClass) {
        requireServerThread();
        requireGame(gameId);
        if (games.putIfAbsent(gameId, Objects.requireNonNull(roomClass)) != null) {
            throw new IllegalArgumentException("游戏重载已注册：" + gameId);
        }
    }

    /**
     * 登记或替换子插件 config.yml 的重载回调，不替换核心的房间重载。
     * 仅有 config.yml 的子插件需要登记；回调只能重读该子插件的 config.yml 并刷新其配置缓存，
     * 不得加载 Wiki、Lobby、玩家数据、排行榜或其他模块，也不得启动对局或修改房间。
     * 回调应先完整验证再提交，失败保留旧值并抛出异常；核心会保留旧房间。
     * @param gameId 已注册游戏 ID
     * @param action 仅负责该子插件 config.yml 的重载操作
     * @throws IllegalArgumentException 游戏未注册
     * @throws IllegalStateException 不在服务器线程
     */
    public void registerGameConfig(String gameId, ReloadAction action) {
        requireServerThread();
        requireGame(gameId);
        gameConfigs.put(gameId, Objects.requireNonNull(action));
    }

    private void requireGame(String gameId) {
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            throw new IllegalArgumentException("未注册游戏：" + gameId);
        }
    }

    /** @return 按注册顺序排列的只读模块名快照；不包含 game */
    public Set<String> getModuleIds() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(modules.keySet()));
    }

    /**
     * 只执行指定模块，失败不抛出到命令层。
     * @param id 模块名
     * @return 成功、未知目标或失败结果
     */
    public ReloadResult reload(String id) {
        requireServerThread();
        return execute(id, modules.get(id), "已生效；已打开的菜单请关闭后重新打开");
    }

    /**
     * 仅重载所选游戏的房间和已登记的子插件 config.yml；活动实例存在时拒绝，不自动排队。
     * @param gameId 精确匹配的注册 ID
     * @return 重载结果；成功后配置用于后续新建对局
     */
    public ReloadResult reloadGame(String gameId) {
        requireServerThread();
        String target = "game " + gameId;
        if (MCZJUGameCore.getGameManager().getAllGames().stream().anyMatch(game -> gameId.equals(game.getId()))) {
            return new ReloadResult(target, false, "该游戏仍有活动对局（含等待阶段），请结束后重试；未修改配置");
        }
        var roomClass = games.get(gameId);
        if (roomClass == null) return new ReloadResult(target, false, "未知游戏或未登记房间类型");
        ReloadAction config = gameConfigs.get(gameId);
        return execute(target, () -> {
            Runnable applyRooms = MCZJUGameCore.getGameRoomManager().prepareGameRoomReload(gameId, roomClass);
            if (config != null) config.reload();
            applyRooms.run();
        }, (config == null ? "已重载房间；未登记子插件 config.yml 回调（如有配置文件请先接入）"
                : "已重载子插件 config.yml 和房间") + "；仅用于新建对局，旧房间菜单及编辑回调已失效");
    }

    /** @return 先全部模块、后全部游戏的结果快照；单项失败后仍继续执行其余项 */
    public List<ReloadResult> reloadAll() {
        requireServerThread();
        List<ReloadResult> results = new ArrayList<>();
        for (String id : List.copyOf(modules.keySet())) results.add(reload(id));
        for (String id : List.copyOf(games.keySet())) results.add(reloadGame(id));
        return List.copyOf(results);
    }

    private ReloadResult execute(String target, ReloadAction action, String successMessage) {
        if (action == null) return new ReloadResult(target, false, "未知重载目标或未注册重载操作");
        try {
            action.reload();
            return new ReloadResult(target, true, successMessage);
        } catch (Exception e) {
            MCZJUGameCore.getInstance().getLogger().log(Level.SEVERE, "重载失败：" + target, e);
            return new ReloadResult(target, false, Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("配置重载必须在服务器线程执行");
    }
}
