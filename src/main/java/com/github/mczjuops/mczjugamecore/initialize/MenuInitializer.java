package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSettingMenu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;

public class MenuInitializer {
    public static void initialize(){
        MenuFacade.registerMenu(new GameRoomSettingMenu(), "room", "房间参数编辑菜单", 27, "menu.op");
    }
}
