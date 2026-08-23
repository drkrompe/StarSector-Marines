package com.dillon.starsectormarines.battle.profile;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickStallWatchdogTest {

    @Test
    void stalledTickDumpsAllThreadsWithoutWaitingForTickExit() throws Exception {
        CountDownLatch dumped = new CountDownLatch(1);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> contents = new AtomicReference<>();
        try (TickStallWatchdog watchdog = TickStallWatchdog.createForTest(
                20L, 2L, (p, text) -> {
                    path.set(p);
                    contents.set(text);
                    dumped.countDown();
                });
             TickStallWatchdog.TickGuard ignored = watchdog.watchForTest(42)) {
            assertTrue(dumped.await(2, TimeUnit.SECONDS),
                    "watchdog should fire while the tick guard remains armed");
            assertNotNull(path.get());
            assertTrue(path.get().contains("thread_dump_stall_"));
            assertTrue(path.get().endsWith("_tick_42.txt"));
            assertTrue(contents.get().contains("tickIndex=42"));
            assertTrue(contents.get().contains("jvmDeadlockDetected="));
            assertTrue(contents.get().contains(Thread.currentThread().getName()));
            assertTrue(contents.get().contains("TickStallWatchdogTest"));
        }
    }

    @Test
    void completedTickDisarmsItsOwnWatch() throws Exception {
        CountDownLatch dumped = new CountDownLatch(1);
        try (TickStallWatchdog watchdog = TickStallWatchdog.createForTest(
                100L, 2L, (path, text) -> dumped.countDown())) {
            TickStallWatchdog.TickGuard guard = watchdog.watchForTest(7);
            assertEquals(1, watchdog.activeTickCountForTest());
            guard.close();
            guard.close();
            assertEquals(0, watchdog.activeTickCountForTest());
            assertFalse(dumped.await(200, TimeUnit.MILLISECONDS),
                    "a completed tick must not produce a delayed dump");
        }
    }
}
