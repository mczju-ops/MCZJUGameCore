package com.github.mczjuops.mczjugamecore.score.leaderboard.textdisplay;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

public class TextDisplayProperties {
    public Display.Billboard billboard = Display.Billboard.VERTICAL;
    public boolean hasBackground = true;

    public Display.Billboard getBillboard() {
        return billboard;
    }

    public void setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
    }

    public boolean hasBackground() {
        return hasBackground;
    }

    public void setHasBackground(boolean hasBackground) {
        this.hasBackground = hasBackground;
    }

    public void applyTo(TextDisplay entity) {
        entity.setBillboard(billboard);
        if (hasBackground) {
            entity.setBackgroundColor(Color.fromARGB(64, 0, 0, 0));
        } else {
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        }
    }
}
