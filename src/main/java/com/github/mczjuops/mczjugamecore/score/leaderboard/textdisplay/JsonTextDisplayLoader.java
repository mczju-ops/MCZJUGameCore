package com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.serialize.LocationAdapter;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;
import com.google.gson.Gson;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonTextDisplayLoader implements TextDisplayLoader {

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));

    @Override
    public void loadAllTextDisplay(String leaderboardId) {
        Path recordDataPath = Paths.get(
                MCZJUGameCore.getInstance().getDataFolder().getPath(),
                "text_displays", leaderboardId
        );
        if (!Files.exists(recordDataPath)) return; // 该排行榜没有任何展示实体
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(recordDataPath, "*.json")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                int lastDotIndex = fileName.lastIndexOf('.');
                String entityId = (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
                TextDisplayRecord record = loadTextDisplayRecord(leaderboardId, entityId, entry);
                if (record != null) {
                    MCZJUGameCore.getLeaderboardManager().createTextDisplay(leaderboardId, record);
                } else {
                    logger.error("无法加载排行榜%s的展示实体%s".formatted(leaderboardId, entityId));
                }
            }
        } catch (IOException e) {
            logger.error("无法加载排行榜%s：%s".formatted(leaderboardId, e));
        }

    }

    private @Nullable TextDisplayRecord loadTextDisplayRecord(String leaderboardId, String entityId, Path file) {
        try {
            if (!Files.exists(file)) {
                logger.warn("文件不存在：" + file);
                return null;
            }

            Gson gson = LocationAdapter.getGsonBuilder();

            try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {
                var record = gson.fromJson(reader, JsonTextDisplayRecord.class);
                if (record == null) {
                    logger.error("反序列化失败：" + file.getFileName());
                    return null;
                }

                logger.info("已加载排行榜 %s 的展示实体 %s".formatted(leaderboardId, entityId));
                return record;
            }
        } catch (IOException e) {
            logger.error("排行榜加载失败：" + file);
            logger.error(e.toString());
            return null;
        }
    }
}
