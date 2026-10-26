package com.github.mczjuops.mczjugamecore.mention;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MentionManagerTest {

    private static final Map<String, String> NAMES = Map.of(
            "steve", "Steve",
            "steve2", "Steve2",
            "alex", "Alex",
            "小明", "小明"
    );

    @Test
    void resolvesExactNamesIgnoringCase() {
        assertEquals("Steve", MentionManager.resolveName("Steve", NAMES, false));
        assertEquals("Steve", MentionManager.resolveName("steve", NAMES, false));
        assertEquals("Steve", MentionManager.resolveName("STEVE", NAMES, false));
        assertEquals("Steve2", MentionManager.resolveName("Steve2", NAMES, false));
        assertNull(MentionManager.resolveName("nobody", NAMES, false));
    }

    @Test
    void resolvesAllKeywordOnlyWhenAllowed() {
        assertEquals(MentionManager.ALL_TARGETS, MentionManager.resolveName("all", NAMES, true));
        assertEquals(MentionManager.ALL_TARGETS, MentionManager.resolveName("ALL", NAMES, true));
        assertEquals(MentionManager.ALL_TARGETS, MentionManager.resolveName("everyone", NAMES, true));
        assertNull(MentionManager.resolveName("all", NAMES, false));
        assertNull(MentionManager.resolveName("alll", NAMES, true));
    }

    @Test
    void onlyWholeTokensMatch() {
        // 只有空白才截断：名字后粘连任何字符都不再是同一个目标
        assertNull(MentionManager.resolveName("Steve，来一下", NAMES, false));
        assertNull(MentionManager.resolveName("Steve在吗", NAMES, false));
        assertNull(MentionManager.resolveName("Steve!", NAMES, false));
        assertNull(MentionManager.resolveName("Steve_hi", NAMES, false));
        assertNull(MentionManager.resolveName("Steve2abc", NAMES, false));
        assertNull(MentionManager.resolveName("St", NAMES, false));
    }

    @Test
    void chineseNamesMatchAsWholeTokens() {
        assertEquals("小明", MentionManager.resolveName("小明", NAMES, false));
        assertNull(MentionManager.resolveName("小明你好", NAMES, false));
        assertNull(MentionManager.resolveName("小", NAMES, false));
    }
}
