package com.github.mczjuops.mczjugamecore.game.timer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountdownEngineTest {

    @Test
    void startThenTickDecrements() {
        CountdownEngine engine = new CountdownEngine(5);
        engine.start();
        assertTrue(engine.isRunning());
        assertFalse(engine.tick());
        assertEquals(4, engine.getSecondsLeft());
        assertEquals(CountdownEngine.State.RUNNING, engine.getState());
    }

    @Test
    void tickToZeroCompletes() {
        CountdownEngine engine = new CountdownEngine(2);
        engine.start();
        assertFalse(engine.tick()); // 2 -> 1
        assertTrue(engine.tick());  // 1 -> 0
        assertEquals(0, engine.getSecondsLeft());
        assertEquals(CountdownEngine.State.COMPLETED, engine.getState());
    }

    @Test
    void zeroTotalSecondsCompletesOnFirstTick() {
        CountdownEngine engine = new CountdownEngine(0);
        engine.start();
        assertTrue(engine.tick());
        assertEquals(CountdownEngine.State.COMPLETED, engine.getState());
    }

    @Test
    void pauseKeepsRemainingAndResumeContinues() {
        CountdownEngine engine = new CountdownEngine(10);
        engine.start();
        engine.tick(); // 9
        engine.pause();
        assertTrue(engine.isPaused());
        assertFalse(engine.tick()); // 暂停时 tick 无效
        assertEquals(9, engine.getSecondsLeft());
        engine.resume();
        assertTrue(engine.isRunning());
        assertFalse(engine.tick()); // 8
        assertEquals(8, engine.getSecondsLeft());
    }

    @Test
    void cancelOnlyFromRunningOrPaused() {
        CountdownEngine engine = new CountdownEngine(5);
        engine.cancel(); // IDLE 状态不生效
        assertEquals(CountdownEngine.State.IDLE, engine.getState());

        engine.start();
        engine.cancel();
        assertEquals(CountdownEngine.State.CANCELLED, engine.getState());
    }

    @Test
    void cancelWhilePausedWorks() {
        CountdownEngine engine = new CountdownEngine(5);
        engine.start();
        engine.pause();
        engine.cancel();
        assertEquals(CountdownEngine.State.CANCELLED, engine.getState());
    }

    @Test
    void restartResetsAndStarts() {
        CountdownEngine engine = new CountdownEngine(5);
        engine.start();
        engine.tick();
        engine.tick();
        assertEquals(3, engine.getSecondsLeft());
        engine.restart();
        assertTrue(engine.isRunning());
        assertEquals(5, engine.getSecondsLeft());
    }

    @Test
    void resetReturnsToIdle() {
        CountdownEngine engine = new CountdownEngine(5);
        engine.start();
        engine.tick();
        engine.reset();
        assertEquals(CountdownEngine.State.IDLE, engine.getState());
        assertEquals(5, engine.getSecondsLeft());
    }

    @Test
    void startAfterCompletionThrows() {
        CountdownEngine engine = new CountdownEngine(1);
        engine.start();
        engine.tick();
        assertEquals(CountdownEngine.State.COMPLETED, engine.getState());
        assertThrows(IllegalStateException.class, engine::start);
    }

    @Test
    void negativeTotalSecondsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CountdownEngine(-1));
    }

    @Test
    void progressComputesCorrectly() {
        CountdownEngine engine = new CountdownEngine(4);
        assertEquals(0f, engine.getProgress(), 0.0001f);
        engine.start();
        engine.tick(); // 3
        assertEquals(0.25f, engine.getProgress(), 0.0001f);
        engine.tick(); // 2
        assertEquals(0.5f, engine.getProgress(), 0.0001f);
    }
}
