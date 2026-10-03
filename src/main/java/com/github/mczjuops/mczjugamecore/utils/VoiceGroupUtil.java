package com.github.mczjuops.mczjugamecore.utils;

import com.github.mczjuops.mczjugamecore.player.PlayerExt;
import com.github.mczjuops.mczjugamecore.utils.voicechat.VoiceGroupIntegration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 可选的 Simple Voice Chat 群组接口，不向调用者暴露语音插件的 API 类型。
 * 创建、移除和生命周期操作必须在服务器主线程调用。
 */
public final class VoiceGroupUtil {
    private static Backend backend;

    private VoiceGroupUtil() {
    }

    /**
     * 使用无密码、可见、非持久的 NORMAL 群组配置，将有语音连接的玩家移入新群组。
     * @param groupName 群组名称，特殊字符和空白可能被语音插件处理
     * @param players 玩家集合，可以直接传入 List&lt;PlayerExt&gt;，重复玩家仅加入一次
     * @return 新群组 UUID；语音不可用或没有可加入的玩家时返回 empty
     * @throws IllegalArgumentException 群组名称为空白
     * @throws IllegalStateException 不在服务器主线程、名称被拒绝或第三方事件阻止创建/加入
     */
    public static Optional<UUID> createGroup(String groupName, Collection<PlayerExt> players) {
        return createGroup(groupName, players, Options.defaults());
    }

    /**
     * 使用自定义配置创建新群组。同名群组不会复用；玩家将离开原语音群组。
     * 离线或未连接语音的玩家跳过，不安排稍后加入，也不会在移除时恢复原群组。
     * @param groupName 群组名称
     * @param players 玩家集合，集合及成员不能为 null
     * @param options 群组配置
     * @return 新群组 UUID；语音不可用或没有可加入的玩家时返回 empty
     * @throws IllegalArgumentException 群组名称为空白
     * @throws IllegalStateException 不在服务器主线程、名称被拒绝或第三方事件阻止创建/加入
     */
    public static Optional<UUID> createGroup(String groupName, Collection<PlayerExt> players, Options options) {
        requireMainThread();
        Objects.requireNonNull(groupName, "groupName");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(options, "options");
        if (groupName.isBlank()) throw new IllegalArgumentException("群组名称不能为空白");
        players.forEach(player -> Objects.requireNonNull(player, "player"));
        return isAvailable() ? backend.createGroup(groupName, players, options) : Optional.empty();
    }

    /** @return 语音插件已启用且语音服务器 API 已就绪时为 true */
    public static boolean isAvailable() {
        return backend != null && Bukkit.getPluginManager().isPluginEnabled("voicechat") && backend.isAvailable();
    }

    /**
     * 让当前成员退出并移除本工具创建的群组；不影响其他来源的群组。
     * @param groupId createGroup 返回的群组 UUID
     * @return 移除成功或已自动消失时为 true；不可用、非本工具群组或删除失败时为 false
     */
    public static boolean removeGroup(UUID groupId) {
        requireMainThread();
        Objects.requireNonNull(groupId, "groupId");
        return isAvailable() && backend.removeGroup(groupId);
    }

    /** 核心插件启动时调用；仅在 voicechat 已启用时加载其 API 集成类。 */
    public static void initialize(JavaPlugin plugin) {
        requireMainThread();
        if (backend != null || !Bukkit.getPluginManager().isPluginEnabled("voicechat")) return;
        try {
            backend = VoiceGroupIntegration.register(plugin);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Simple Voice Chat 集成不可用：" + e);
        }
    }

    /** 核心插件停用时调用，清理本工具群组并释放 API 引用。 */
    public static void shutdown() {
        requireMainThread();
        if (backend == null) return;
        try {
            backend.shutdown();
        } finally {
            backend = null;
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("语音群组操作必须在服务器主线程调用");
    }

    /** 群组语音模式：普通、向附近开放、仅组内可听。 */
    public enum GroupType { NORMAL, OPEN, ISOLATED }

    /**
     * 不可变群组配置，可从 defaults() 开始链式修改。
     * @param password 密码，null 表示无密码；仅限制手动加入，不影响本工具直接加入
     * @param persistent 是否在最后一名玩家离开后保留空组，不代表跨服务器重启保存
     * @param hidden 是否在客户端群组列表隐藏，不是访问控制
     * @param type 语音模式
     */
    public record Options(@Nullable String password, boolean persistent, boolean hidden, GroupType type) {
        /** 验证语音模式不能为空。 */
        public Options {
            Objects.requireNonNull(type, "type");
        }

        /** @return 无密码、非持久、可见的 NORMAL 配置 */
        public static Options defaults() {
            return new Options(null, false, false, GroupType.NORMAL);
        }

        /** @return 使用指定密码的新配置，null 表示无密码 */
        public Options withPassword(@Nullable String password) {
            return new Options(password, persistent, hidden, type);
        }

        /** @return 使用指定空组保留行为的新配置 */
        public Options withPersistent(boolean persistent) {
            return new Options(password, persistent, hidden, type);
        }

        /** @return 使用指定列表可见性的新配置 */
        public Options withHidden(boolean hidden) {
            return new Options(password, persistent, hidden, type);
        }

        /** @return 使用指定语音模式的新配置 */
        public Options withType(GroupType type) {
            return new Options(password, persistent, hidden, type);
        }
    }

    /** 内部集成边界，保证未安装语音插件时公共接口仍可加载。 */
    public interface Backend {
        boolean isAvailable();
        Optional<UUID> createGroup(String name, Collection<PlayerExt> players, Options options);
        boolean removeGroup(UUID groupId);
        void shutdown();
    }
}
