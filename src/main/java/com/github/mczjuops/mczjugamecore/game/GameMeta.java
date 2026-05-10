package com.github.mczjuops.mczjugamecore.game;

import org.bukkit.Material;

public record GameMeta(
        String displayName,
        Material icon,
        String author,
        String description
) {
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .displayName(displayName)
                .icon(icon)
                .author(author)
                .description(description);
    }

    public static final class Builder {
        private String displayName = "<gray>未命名游戏</gray>";
        private Material icon = Material.PAPER;
        private String author = "<gray>未知</gray>";
        private String description = "<gray>无</gray>";

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = icon;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public GameMeta build() {
            return new GameMeta(displayName, icon, author, description);
        }
    }
}
