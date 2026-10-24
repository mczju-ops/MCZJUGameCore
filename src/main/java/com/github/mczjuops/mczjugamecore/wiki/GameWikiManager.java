package com.github.mczjuops.mczjugamecore.wiki;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
     * 向玩家发送使用客户端 OPEN_URL 的可点击链接，保留客户端浏览器确认。
     * @param player 接收玩家
     * @param gameId 注册游戏 ID
     * @return 是否成功提供链接；不存在或未配置时发送提示并返回 false
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
        player.sendMessage(Component.text("点击在浏览器中打开 " + gameId + " 的 Wiki：", NamedTextColor.YELLOW)
                .append(Component.text(url, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));
        return true;
    }
}
