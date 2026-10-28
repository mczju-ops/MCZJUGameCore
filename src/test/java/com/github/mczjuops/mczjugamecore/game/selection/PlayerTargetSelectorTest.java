package com.github.mczjuops.mczjugamecore.game.selection;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTargetSelectorTest {

    @Test
    void nullKeepKeepsAll() {
        List<String> result = PlayerTargetSelector.filterCandidates(List.of("a", "b", "c"), null);
        assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void predicateFiltersCandidates() {
        List<String> result = PlayerTargetSelector.filterCandidates(
                List.of("alice", "bob", "carl"),
                name -> name.startsWith("a"));
        assertEquals(List.of("alice"), result);
    }

    @Test
    void nullCandidatesReturnsEmpty() {
        assertTrue(PlayerTargetSelector.filterCandidates(null, s -> true).isEmpty());
    }

    @Test
    void preservesOrder() {
        List<String> result = PlayerTargetSelector.filterCandidates(
                List.of("x", "keep", "y", "keep2"),
                name -> name.startsWith("keep"));
        assertEquals(List.of("keep", "keep2"), result);
    }

    @Test
    void rowsGrowWithCandidateCountAndClamp() {
        // 空/单人也至少一行，超出容量封顶六行
        assertEquals(1, PlayerTargetSelector.rowsFor(0));
        assertEquals(1, PlayerTargetSelector.rowsFor(1));
        assertEquals(1, PlayerTargetSelector.rowsFor(9));
        assertEquals(2, PlayerTargetSelector.rowsFor(10));
        assertEquals(6, PlayerTargetSelector.rowsFor(54));
        assertEquals(6, PlayerTargetSelector.rowsFor(100));
    }

    @Test
    void pickRandomIndexReturnsMinusOneWhenEmpty() {
        assertEquals(-1, PlayerTargetSelector.pickRandomIndex(0, new Random(1)));
        assertEquals(-1, PlayerTargetSelector.pickRandomIndex(-3, new Random(1)));
    }

    @Test
    void pickRandomIndexStaysInRange() {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            int index = PlayerTargetSelector.pickRandomIndex(3, random);
            assertTrue(index >= 0 && index < 3, "index out of range: " + index);
        }
    }

    @Test
    void pickRandomIndexCoversAllCandidates() {
        Random random = new Random(7);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(PlayerTargetSelector.pickRandomIndex(4, random));
        }
        assertEquals(Set.of(0, 1, 2, 3), seen);
    }
}
