package com.github.mczjuops.mczjugamecore.game.room;

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

public class JsonGameRoomLoader implements GameRoomLoader{

    private final ConsoleSender logger = new ConsoleSender("MGC: %s".formatted(getClass().getSimpleName()));
    @Override
    public boolean loadAllGameRoom(String gameId, Class<? extends AbstractGameRoom> gameRoomClass) {
        if(!JsonGameRoom.class.isAssignableFrom(gameRoomClass)) return false;
        // 由自己加载，找路径下的所有json文件
        Path gameDataPath = Paths.get(
                MCZJUGameCore.getInstance().getDataFolder().getPath(),
                gameId
        );
        if (!Files.exists(gameDataPath)) return true;   // 没有地图文件，直接返回
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameDataPath, "*.json")) {
            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                int lastDotIndex = fileName.lastIndexOf('.');
                String roomName = (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
                AbstractGameRoom room = loadGameRoom(gameId, roomName, entry, gameRoomClass);
                if (room != null){
                    // 加载成功了
                    MCZJUGameCore.getGameRoomManager().registerGameRoom(gameId, room);
                }else {
                    logger.error("无法加载游戏%s的地图：%s".formatted(gameId, roomName));
                }
            }
        } catch (IOException e) {
            logger.error("无法加载游戏地图：%s".formatted(gameId));
            logger.error(e.toString());
        }

        return true;
    }

    private @Nullable <T extends AbstractGameRoom> T loadGameRoom(
            String gameId,
            String roomName,
            Path file,
            Class<T> roomClass
    ) {
        try {
            if (!Files.exists(file)) {
                logger.warn("文件不存在：%s".formatted(file));
                return null;
            }

            Gson gson = LocationAdapter.getGsonBuilder();

            try (FileReader reader = new FileReader(file.toFile(), StandardCharsets.UTF_8)) {

                T room = gson.fromJson(reader, roomClass);

                if (room == null) {
                    logger.error("反序列化失败：%s".formatted(file.getFileName()));
                    return null;
                }

                // 补充运行时字段（JSON里通常没有）
                room.setGameId(gameId);
                room.setRoomName(roomName);

                // 注册
                MCZJUGameCore.getGameRoomManager()
                        .registerGameRoom(gameId, room);

                logger.info("已加载地图 %s - %s".formatted(gameId, room.getRoomName()));

                return room;
            }

        } catch (Exception e) {
            logger.error("加载地图失败：%s".formatted(file));
            logger.error(e.toString());
            return null;
        }
    }
}
