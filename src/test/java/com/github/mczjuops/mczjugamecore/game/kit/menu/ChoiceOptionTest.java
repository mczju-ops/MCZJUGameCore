package com.github.mczjuops.mczjugamecore.game.kit.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选项排序与置灰。
 *
 * <p>日麻的声明窗口要求：「荣」排在「碰」前面，振听时「荣」要显示但不可选。
 */
class ChoiceOptionTest {

    @Test
    void sortsByOrderAscending() {
        ChoiceOption pon = new ChoiceOption("碰", "pon", "用两张同牌", null, true, 20);
        ChoiceOption ron = new ChoiceOption("荣和", "ron", "和这张牌", null, true, 10);
        ChoiceOption pass = new ChoiceOption("过", "pass", null, null, true, 30);

        List<ChoiceOption> sorted = ChoiceMenu.sorted(List.of(pon, ron, pass));
        assertEquals(List.of("荣和", "碰", "过"), sorted.stream().map(ChoiceOption::label).toList());
    }

    @Test
    void sameOrderKeepsInputSequence() {
        ChoiceOption a = new ChoiceOption("甲", "a");
        ChoiceOption b = new ChoiceOption("乙", "b");
        ChoiceOption c = new ChoiceOption("丙", "c");
        assertEquals(List.of("甲", "乙", "丙"),
                ChoiceMenu.sorted(List.of(a, b, c)).stream().map(ChoiceOption::label).toList());
    }

    @Test
    void sortedDoesNotMutateInput() {
        ChoiceOption a = new ChoiceOption("甲", "a", null, null, true, 5);
        ChoiceOption b = new ChoiceOption("乙", "b", null, null, true, 1);
        List<ChoiceOption> input = List.of(a, b);
        ChoiceMenu.sorted(input);
        assertEquals(List.of("甲", "乙"), input.stream().map(ChoiceOption::label).toList());
    }

    @Test
    void withEnabledKeepsOtherFields() {
        ChoiceOption ron = new ChoiceOption("荣和", "ron", "和这张牌", null, true, 10);
        ChoiceOption blocked = ron.withEnabled(false);
        assertFalse(blocked.enabled());
        assertEquals("荣和", blocked.label());
        assertEquals("ron", blocked.value());
        assertEquals(10, blocked.order(), "置灰不能顺手把排序弄丢");
    }

    @Test
    void withOrderKeepsEnabled() {
        ChoiceOption ron = new ChoiceOption("荣和", "ron", null, null, false, 10);
        assertEquals(false, ron.withOrder(1).enabled());
    }

    @Test
    void defaultIsSelectableAndUnordered() {
        ChoiceOption plain = new ChoiceOption("甲", "a");
        assertTrue(plain.enabled());
        assertEquals(0, plain.order());
    }
}
