package com.github.mczjuops.mczjugamecore.initialize;

import com.github.mczjuops.mczjugamecore.game.room.menu.GameRoomSettingMenu;
import com.github.mczjuops.mczjugamecore.menu.AlertMenu;
import com.github.mczjuops.mczjugamecore.menu.MenuFacade;

public class MenuInitializer {
    public static void initialize(){
        MenuFacade.registerMenu(AlertMenu.class, "确认操作", 3, "menu.default");
        MenuFacade.registerMenu(GameRoomSettingMenu.class,"房间参数编辑菜单", 6, "menu.op");
    }
}
