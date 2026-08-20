package com.github.mczjuops.mczjugamecore.item;

public enum MGCMaterial {
    DEBUG_STICK("debug_stick"),
    LOBBY_MENU_CLOCK("lobby_menu_clock");

    private final String id;

    MGCMaterial(String id){
        this.id = id;
    }

    @Override
    public String toString(){
        return id;
    }
}
