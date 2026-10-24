package com.github.mczjuops.mczjugamecore.wiki;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.google.gson.*;
import com.github.mczjuops.mczjugamecore.utils.TextParser;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import org.bukkit.Bukkit;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 按注册游戏 ID 保存可选 Wiki URL；配置读取失败或被外部修改时拒绝覆盖。 */
public final class GameWikiManager {
    private final Path file;
    private final Map<String, String> links = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private String savedContents;
    private boolean readable = true;

    /** 从插件数据目录的 game-wikis.json 加载配置；缺省文件视为空配置。 */
    public GameWikiManager() {
        file = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("game-wikis.json");
        try {
            if (Files.notExists(file)) return;
            savedContents = Files.readString(file);
            JsonElement parsed = JsonParser.parseString(savedContents);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("配置必须是 JSON 对象");
            Map<String, String> loaded = new HashMap<>();
            for (var entry : parsed.getAsJsonObject().entrySet()) {
                if (entry.getKey().isBlank() || !entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("无效的游戏 ID 或 URL");
                }
                loaded.put(entry.getKey(), validateUrl(entry.getValue().getAsString()));
            }
            links.putAll(loaded);
        } catch (IOException | RuntimeException e) {
            readable = false;
            MCZJUGameCore.getInstance().getLogger().severe("无法加载 Wiki 配置，拒绝覆盖：" + e.getMessage());
        }
    }

    /**
     * @param gameId 注册游戏 ID
     * @return 已保存的链接，未设置时为 null
     */
    public @Nullable String getLink(String gameId) {
        return links.get(gameId);
    }

    /**
     * 设置或移除一个已注册游戏的链接，只有成功落盘后才更新内存。
     * @param gameId 注册游戏 ID
     * @param url HTTP/HTTPS URL；null 或空白表示移除
     * @return 是否保存成功；无效游戏、配置损坏或磁盘冲突时为 false
     * @throws IllegalArgumentException URL 不合法时抛出
     */
    public boolean setLink(String gameId, @Nullable String url) {
        if (!readable || !MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) return false;
        String validated = url == null || url.isBlank() ? null : validateUrl(url);
        Map<String, String> updated = new HashMap<>(links);
        if (validated == null) updated.remove(gameId);
        else updated.put(gameId, validated);
        Path temporary = null;
        try {
            String current = Files.exists(file) ? Files.readString(file) : null;
            if (!Objects.equals(current, savedContents)) throw new IOException("配置已被外部修改，请重启后重试");
            if (updated.equals(links)) return true;
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "game-wikis-", ".tmp");
            String contents = gson.toJson(updated);
            Files.writeString(temporary, contents);
            current = Files.exists(file) ? Files.readString(file) : null;
            if (!Objects.equals(current, savedContents)) throw new IOException("配置已被外部修改，请重启后重试");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            savedContents = contents;
            links.clear();
            links.putAll(updated);
            return true;
        } catch (IOException e) {
            MCZJUGameCore.getInstance().getLogger().severe("保存 Wiki 配置失败：" + e.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { MCZJUGameCore.getInstance().getLogger().warning(e.getMessage()); }
            }
        }
    }

    /**
     * 加入游戏后发送 Wiki 聊天提醒，玩家可直接打开网页或永久关闭此游戏的提醒。
     * @param player 已成功加入游戏的玩家，在服务器线程调用
     * @param gameId 注册游戏 ID；没有 Wiki 或玩家已关闭提醒时不发送
     */
    public void remindOnJoin(Player player, String gameId) {
        String url = getLink(gameId);
        var meta = MCZJUGameCore.getGameManager().getGameMetas().get(gameId);
        NamespacedKey key = new NamespacedKey(MCZJUGameCore.getInstance(), "hidden_wiki_reminders");
        var hidden = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LIST.strings(), List.of());
        if (url == null || meta == null || hidden.contains(gameId)) return;
        var owner = player.getUniqueId();
        player.sendMessage(Component.text("了解玩法：", NamedTextColor.YELLOW)
                .append(TextParser.parse(meta.displayName()))
                .append(Component.text(" [打开 Wiki]", NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(url)))
                .append(Component.text(" [下次不再提醒此游戏]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.callback(audience -> {
                            if (!(audience instanceof Player who) || !who.getUniqueId().equals(owner)) return;
                            Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
                                if (!who.isOnline()) return;
                                var values = new ArrayList<>(who.getPersistentDataContainer()
                                        .getOrDefault(key, PersistentDataType.LIST.strings(), List.of()));
                                if (!values.contains(gameId)) values.add(gameId);
                                who.getPersistentDataContainer().set(key, PersistentDataType.LIST.strings(), values);
                                who.sendMessage(Component.text("已关闭此游戏的 Wiki 提醒，下次可用 /mgc wiki 打开 Wiki 菜单。",
                                                NamedTextColor.YELLOW)
                                        .clickEvent(ClickEvent.runCommand("/mgc wiki")));
                            });
                        }, ClickCallback.Options.builder().uses(1).lifetime(java.time.Duration.ofDays(1)).build()))));
    }

    private static String validateUrl(String value) {
        String url = value.trim();
        try {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || url.length() > 2048) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("请输入有效的 HTTP/HTTPS 链接（最多 2048 字符，不含登录信息）");
        }
    }

    /**
     * 显示带有客户端 OPEN_URL 按钮的 Wiki 对话框，保留客户端浏览器确认。
     * @param player 接收玩家
     * @param gameId 注册游戏 ID
     * @return 是否已安排显示对话框；不存在或未配置时发送提示并返回 false
     */
    public boolean sendLink(Player player, String gameId) {
        if (!MCZJUGameCore.getGameManager().getRegisteredGameIds().contains(gameId)) {
            player.sendMessage(Component.text("小游戏不存在：" + gameId, NamedTextColor.RED));
            return false;
        }
        String url = getLink(gameId);
        if (url == null) {
            player.sendMessage(Component.text("该游戏尚未设置 Wiki", NamedTextColor.YELLOW));
            return false;
        }
        // 延至下一 tick，避免箱子菜单的关闭事件覆盖新打开的对话框。
        Bukkit.getScheduler().runTask(MCZJUGameCore.getInstance(), () -> {
            if (!player.isOnline() || !player.hasPermission("mgc.mgc")) return;
            var meta = MCZJUGameCore.getGameManager().getGameMetas().get(gameId);
            if (meta == null) return;
            player.showDialog(Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(TextParser.parse(meta.displayName()).append(Component.text(" Wiki")))
                            .canCloseWithEscape(true)
                            .body(List.of(DialogBody.plainMessage(Component.text(url))))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(Component.text("打开 Wiki"))
                                    .action(DialogAction.staticAction(ClickEvent.openUrl(url))).build(),
                            ActionButton.builder(Component.text("取消")).build()))));
        });
        return true;
    }
}
