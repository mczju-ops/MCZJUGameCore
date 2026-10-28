package com.github.mczjuops.mczjugamecore.game.kit.team;

import com.github.mczjuops.mczjugamecore.utils.TextParser;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameTeamTest {

    private static GameTeam red() {
        return new GameTeam("red", "<red>红队", NamedTextColor.RED, Material.RED_GLAZED_TERRACOTTA);
    }

    @Test
    void tabPrefixIsDisplayNameWithTrailingSpace() {
        assertEquals("红队 ", TextParser.plain(red().tabPrefix()));
    }

    @Test
    void plainNameDropsMiniMessageTags() {
        assertEquals("红队", red().plainName());
    }

    @Test
    void plainNameFallsBackToIdWhenOnlyFormatting() {
        GameTeam team = new GameTeam("blue", "<bold>", NamedTextColor.BLUE, Material.BLUE_GLAZED_TERRACOTTA);
        assertEquals("blue", team.plainName());
    }

    @Test
    void coloredKeepsTextAndAppliesColor() {
        var component = red().colored("10 分");
        assertEquals("10 分", TextParser.plain(component));
        assertEquals(NamedTextColor.RED, component.color());
    }

    @Test
    void rejectsBlankIdOrDisplayName() {
        assertThrows(IllegalArgumentException.class,
                () -> new GameTeam(" ", "红队", NamedTextColor.RED, Material.STONE));
        assertThrows(IllegalArgumentException.class,
                () -> new GameTeam("red", "", NamedTextColor.RED, Material.STONE));
    }

    @Test
    void rejectsMissingColorOrIcon() {
        assertThrows(NullPointerException.class,
                () -> new GameTeam("red", "红队", null, Material.STONE));
        assertThrows(NullPointerException.class,
                () -> new GameTeam("red", "红队", NamedTextColor.RED, null));
    }
}
