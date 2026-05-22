package com.github.mczjuops.mczjugamecore.player.data;

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

public class JsonPlayerDataLoader implements AbstractPlayerDataLoader{
    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));
    
    @Override
    public boolean loadAllPlayerData(String gameId, Class<? extends AbstractPlayerData> pDataClass) {
        if(!JsonPlayerData.class.isAssignableFrom(pDataClass)) return false;
        // 由自己加载，找路径下的所有json文件
        Path pDataPath = Paths.get(
                MCZJUGameCore.getInstance().getDataFolder().getPath(),
                "player", gameId
        );
        if (!Files.exists(pDataPath)) {
            // 内存中添加空的 Map
            MCZJUGameCore.getPlayerDataManager().addEmptyData(gameId);
            return true;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pDataPath, "*.json")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                int lastDotIndex = fileName.lastIndexOf('.');
                String playerId = (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
                AbstractPlayerData pData = loadPlayerData(gameId, playerId, entry, pDataClass);
                if (pData != null){
                    // 加载成功了
                    MCZJUGameCore.getPlayerDataManager().addPlayerData(gameId, playerId, pData);
                }else {
                    logger.error("无法加载游戏%s的地图：%s".formatted(gameId, playerId));
                }
            }
        } catch (IOException e) {
            logger.error("无法加载游戏地图：%s".formatted(gameId));
            logger.error(e.toString());
        }

        return true;
    }

    private @Nullable <T extends AbstractPlayerData> T loadPlayerData(
            String gameId,
            String playerId,
            Path file,
            Class<T> pDataClass
    ) {
        try {
            if (!Files.exists(file)) {
                logger.warn("文件不存在：%s".formatted(file));
                return null;
            }

            Gson gson = LocationAdapter.getGsonBuilder();

            try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {

                T data = gson.fromJson(reader, pDataClass);

                if (data == null) {
                    logger.error("反序列化失败：%s".formatted(file.getFileName()));
                    return null;
                }

                // 补充运行时字段
                data.setGameID(gameId);
                data.setPlayerID(playerId);

                // 注册
                
                logger.info("已加载玩家数据 %s - %s".formatted(gameId, data.getPlayerID()));

                return data;
            }

        } catch (Exception e) {
            logger.error("加载玩家数据失败：%s".formatted(file));
            logger.error(e.toString());
            return null;
        }
    }
}
