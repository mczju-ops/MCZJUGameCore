package com.github.mczjuops.mczjugamecore.command;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** MGC 命令内部的配置重载入口，不作为子插件 API；各项失败互相隔离。 */
final class ReloadManager {
    private final Map<String, ReloadAction> modules = new LinkedHashMap<>();

    /** 可抛出异常的重载操作；先验证再提交，失败时由实现保留原有效配置。 */
    @FunctionalInterface
    private interface ReloadAction {
        /** @throws Exception 读取、验证或应用失败；异常会记录并转换为失败结果 */
        void reload() throws Exception;
    }

    /**
     * 一次独立重载的结果。
     * @param target 模块名或 game 加游戏 ID
     * @param success 是否成功
     * @param message 生效边界或失败原因
     */
    record ReloadResult(String target, boolean success, String message) {}

    /**
     * 注册独立模块（自动纳入补全和无参数重载）。
     * @param id 小写字母、数字、下划线或短横线组成的唯一名称；game 保留
     * @param action 只读取该模块的配置，不隐式重载其他模块
     * @throws IllegalArgumentException 名称非法或重复
     * @throws IllegalStateException 不在服务器线程
     */
    private void register(String id, ReloadAction action) {
        requireServerThread();
        if (id == null || !id.matches("[a-z][a-z0-9_-]*") || id.equals("game")) {
            throw new IllegalArgumentException("无效的重载模块名：" + id);
        }
        if (modules.putIfAbsent(id, Objects.requireNonNull(action)) != null) {
            throw new IllegalArgumentException("重载模块已注册：" + id);
        }
    }

    ReloadManager() {
        register("config", MCZJUGameCore.getConfigManager()::reloadCoreConfig);
        register("categories", MCZJUGameCore.getConfigManager()::reloadCategoryAssignments);
        register("wiki", MCZJUGameCore.getGameWikiManager()::reload);
        register("lobby", MCZJUGameCore.getLobbyManager()::reload);
    }

    /** @return 按注册顺序排列的只读模块名快照；不包含 game */
    Set<String> getModuleIds() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(modules.keySet()));
    }

    /**
     * 只执行指定模块，失败不抛出到命令层。
     * @param id 模块名
     * @return 成功、未知目标或失败结果
     */
    ReloadResult reload(String id) {
        requireServerThread();
        return execute(id, modules.get(id), "已生效；已打开的菜单请关闭后重新打开");
    }

    /**
     * 仅重载所选游戏的房间；活动实例存在时拒绝，不自动排队。
     * @param gameId 精确匹配的注册 ID
     * @return 重载结果；成功后配置用于后续新建对局
     */
    ReloadResult reloadGame(String gameId) {
        requireServerThread();
        String target = "game " + gameId;
        return execute(target, () -> MCZJUGameCore.getGameRoomManager().reloadGameRooms(gameId),
                "已重载房间；仅用于新建对局，旧房间菜单及编辑回调已失效");
    }

    /** @return 先全部模块、后按游戏 ID 排序重载全部游戏房间的结果快照；单项失败后仍继续执行其余项 */
    List<ReloadResult> reloadAll() {
        requireServerThread();
        List<ReloadResult> results = new ArrayList<>();
        for (String id : List.copyOf(modules.keySet())) results.add(reload(id));
        for (String id : MCZJUGameCore.getGameManager().getRegisteredGameIds().stream().sorted().toList()) {
            results.add(reloadGame(id));
        }
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
