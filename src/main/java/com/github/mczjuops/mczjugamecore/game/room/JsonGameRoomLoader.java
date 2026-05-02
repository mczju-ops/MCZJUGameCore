package com.github.mczjuops.mczjugamecore.game.room;

import com.github.mczjuops.mczjugamecore.MCZJUGameCore;
import com.github.mczjuops.mczjugamecore.utils.sender.impl.ConsoleSender;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonGameRoomLoader implements GameRoomLoader{

    private final ConsoleSender logger = new ConsoleSender(STR."MGC:\{getClass().getName()}");
    @Override
    public boolean loadAllGameRoom(String gameName, Class<? extends AbstractGameRoom> gameRoomClass) {
        if(JsonGameRoom.class.isAssignableFrom(gameRoomClass)){
            // 由自己加载，找路径下的所有json文件
            String pathStr = STR."\{MCZJUGameCore.getInstance().getDataFolder().getPath()}/\{gameName}";
            Path gameDataPath = Paths.get(pathStr);
            if (!Files.exists(gameDataPath)) return true;   // 没有地图文件，直接返回
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameDataPath, "*.json")) {
                for (Path entry : stream) {
                    String roomName = entry.getFileName().toString();
                    JsonGameRoom gameRoom = new JsonGameRoom();
                    gameRoom.setGameName(gameName);
                    gameRoom.setRoomName(roomName);
                    if (gameRoom.load()){
                        // 加载成功了
                        MCZJUGameCore.getGameRoomManager().registerGameRoom(gameName, gameRoom);
                        logger.info(STR."已加载地图\{gameName}-\{roomName}");
                    }else {
                        logger.error(STR."无法加载游戏\{gameName}的地图: \{roomName}");
                    }
                }
            } catch (IOException e) {
                logger.error(STR."无法加载游戏地图: \{gameName}");
                logger.error(e.toString());
            }

            return true;
        }else {
            return false;
        }
    }
}
