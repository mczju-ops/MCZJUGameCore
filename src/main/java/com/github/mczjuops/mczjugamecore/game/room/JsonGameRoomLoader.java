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
                "rooms", gameId
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

    /**
     * 完整读取一个游戏的 JSON 房间快照，不修改管理器或磁盘。
     * @param gameId 游戏 ID
     * @param roomClass JSON 房间类型
     * @return 验证成功的房间列表；目录不存在时为空
     * @throws IOException 文件读取失败
     * @throws IllegalArgumentException 类型不支持、JSON 损坏或房间为空
     */
    public java.util.List<AbstractGameRoom> readRooms(String gameId, Class<? extends AbstractGameRoom> roomClass)
            throws IOException {
        if (!JsonGameRoom.class.isAssignableFrom(roomClass)) {
            throw new IllegalArgumentException("此房间类型需由子插件注册自定义重载操作");
        }
        Path directory = MCZJUGameCore.getInstance().getDataFolder().toPath().resolve("rooms").resolve(gameId);
        java.util.List<AbstractGameRoom> rooms = new java.util.ArrayList<>();
        if (Files.notExists(directory)) return rooms;
        Gson gson = LocationAdapter.getGsonBuilder().newBuilder()
                .setStrictness(com.google.gson.Strictness.STRICT).create();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) {
                try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    AbstractGameRoom room = gson.fromJson(reader, roomClass);
                    if (room == null) throw new IllegalArgumentException("房间不能为空：" + file.getFileName());
                    String name = file.getFileName().toString();
                    room.setGameId(gameId);
                    room.setRoomName(name.substring(0, name.length() - 5));
                    room.setState(GameRoomState.READY);
                    room.setModified(false);
                    rooms.add(room);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("房间 " + file.getFileName() + " 加载失败：" + e.getMessage(), e);
                }
            }
        }
        return rooms;
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
