package com.github.mczjuops.mczjugamecore.mention;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionSettingsTest {

    private static MentionSettings load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return MentionSettings.load(config);
    }

    @Test
    void missingFileUsesDefaults() {
        MentionSettings settings = load("");
        assertEquals(MentionSettings.DEFAULTS.enabled(), settings.enabled());
        assertFalse(settings.hideMessage());
        assertTrue(settings.requireSpace());
        assertNotNull(settings.pingSound());
        assertNotNull(settings.bigSound());
        assertEquals(MentionSettings.DEFAULTS.title(), settings.title());
        assertEquals(MentionSettings.DEFAULTS.pingActionBar(), settings.pingActionBar());
        assertEquals("<gray>{player} 提醒了你</gray>", settings.pingActionBar());
    }

    @Test
    void readsConfiguredValues() {
        MentionSettings settings = load("""
                enabled: false
                hide-message: true
                require-space: false
                allow-all: false
                max-targets: 3
                cooldown-millis: 1500
                ping-actionbar: "<gray>{player} 提醒了你</gray>"
                ping:
                  sound: "minecraft:block.note_block.pling"
                  volume: 0.5
                  pitch: 1.5
                big:
                  sound: "none"
                  title: "<red>hi</red>"
                  subtitle: ""
                  fade-in: 1
                  stay: 20
                  fade-out: 3
                """);
        assertFalse(settings.enabled());
        assertTrue(settings.hideMessage());
        assertFalse(settings.requireSpace());
        assertFalse(settings.allowAll());
        assertEquals(3, settings.maxTargets());
        assertEquals(1500L, settings.cooldownMillis());
        assertEquals("<gray>{player} 提醒了你</gray>", settings.pingActionBar());
        assertNotNull(settings.pingSound());
        assertEquals(0.5f, settings.pingSound().volume());
        assertEquals(1.5f, settings.pingSound().pitch());
        assertNull(settings.bigSound());
        assertEquals("<red>hi</red>", settings.title());
        assertEquals("", settings.subtitle());
        assertEquals(1, settings.fadeInTicks());
        assertEquals(20, settings.stayTicks());
        assertEquals(3, settings.fadeOutTicks());
    }

    @Test
    void clampsNegativeValues() {
        MentionSettings settings = load("""
                max-targets: -5
                cooldown-millis: -1
                big:
                  fade-in: -2
                """);
        assertEquals(0, settings.maxTargets());
        assertEquals(0L, settings.cooldownMillis());
        assertEquals(0, settings.fadeInTicks());
        assertTrue(settings.enabled());
    }

    @Test
    void rejectsInvalidSoundKey() {
        assertThrows(IllegalArgumentException.class, () -> load("""
                ping:
                  sound: "not a key"
                """));
    }
}
