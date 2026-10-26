package com.github.mczjuops.mczjugamecore.mention;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentionParserTest {

    private static final Map<String, String> NAMES = Map.of(
            "steve", "Steve",
            "alex", "Alex",
            "all", "@all",
            "everyone", "@all"
    );

    private static String resolve(String token) {
        return NAMES.get(token.toLowerCase(Locale.ROOT));
    }

    private static List<MentionParser.Mention> parse(String text, boolean requireSpace) {
        return MentionParser.parse(text, requireSpace, MentionParserTest::resolve);
    }

    @Test
    void parsesSingleMention() {
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "Steve")),
                parse("@ Steve 你好", true));
    }

    @Test
    void parsesStrongMention() {
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.TITLE, "Steve")),
                parse("@@ Steve 来一下", true));
    }

    @Test
    void requiresSpaceWhenConfigured() {
        assertTrue(parse("@Steve 你好", true).isEmpty());
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "Steve")),
                parse("@Steve 你好", false));
    }

    @Test
    void parsesMultipleTargets() {
        assertEquals(List.of(
                        new MentionParser.Mention(MentionParser.Kind.PING, "Steve"),
                        new MentionParser.Mention(MentionParser.Kind.PING, "Alex")),
                parse("@ Steve Alex 你们好", true));
    }

    @Test
    void stopsAtUnknownToken() {
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "Steve")),
                parse("@ Steve 未知 Alex", true));
    }

    @Test
    void keepsOrderAndDuplicates() {
        assertEquals(List.of(
                        new MentionParser.Mention(MentionParser.Kind.PING, "Steve"),
                        new MentionParser.Mention(MentionParser.Kind.TITLE, "Alex"),
                        new MentionParser.Mention(MentionParser.Kind.PING, "Steve")),
                parse("@ Steve @@ Alex @ Steve", true));
    }

    @Test
    void parsesMarkerInMiddleOfText() {
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "Steve")),
                parse("你在吗@ Steve", true));
    }

    @Test
    void parsesAllKeyword() {
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "@all")),
                parse("@ all 集合", true));
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.TITLE, "@all")),
                parse("@@ all 集合", true));
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.PING, "@all")),
                parse("@ everyone 集合", true));
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.TITLE, "@all")),
                parse("@@ Everyone 集合", true));
    }

    @Test
    void requiresSpaceEvenForKeywords() {
        assertTrue(parse("@all 集合", true).isEmpty());
        assertTrue(parse("@@all 集合", true).isEmpty());
        assertTrue(parse("@all", true).isEmpty());
        assertTrue(parse("@@all", true).isEmpty());
        assertTrue(parse("@everyone 集合", true).isEmpty());
    }

    @Test
    void ignoresIncompleteMarkers() {
        assertTrue(parse("@", true).isEmpty());
        assertTrue(parse("@@", true).isEmpty());
        assertTrue(parse("@ ", true).isEmpty());
        assertTrue(parse("没有提醒", true).isEmpty());
        assertTrue(parse("", true).isEmpty());
        assertTrue(MentionParser.parse(null, true, MentionParserTest::resolve).isEmpty());
    }

    @Test
    void doubleAtWithoutSpaceIsNotStrongMentionWhenSpaceRequired() {
        assertTrue(parse("@@Steve", true).isEmpty());
        assertEquals(List.of(new MentionParser.Mention(MentionParser.Kind.TITLE, "Steve")),
                parse("@@Steve", false));
    }
}
