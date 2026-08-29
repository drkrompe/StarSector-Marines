package com.dillon.starsectormarines.diagnostics;

import com.dillon.starsectormarines.diagnostics.ProcessExitWatchdog.ThreadSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessExitWatchdogTest {

    private static ThreadSnapshot thread(String name, long id, String... frames) {
        StackTraceElement[] stack = new StackTraceElement[frames.length];
        for (int i = 0; i < frames.length; i++) {
            int split = frames[i].lastIndexOf('.');
            stack[i] = new StackTraceElement(frames[i].substring(0, split),
                    frames[i].substring(split + 1), null, -1);
        }
        return new ThreadSnapshot(name, id, stack);
    }

    @Test
    void shutdownBannerNamesTheThreadThatRequestedTheExit() {
        ThreadSnapshot[] threads = {
                thread("Thread-6", 6L, "java.lang.Thread.run"),
                thread("Thread-2", 2L,
                        "java.lang.Shutdown.exit",
                        "java.lang.Runtime.exit",
                        "java.lang.System.exit",
                        "com.fs.starfarer.StarfarerLauncher.crashed"),
        };

        String banner = ProcessExitWatchdog.describeShutdown(
                232_045L, "heap=812/2048MB", threads);

        assertTrue(banner.contains("exitRequestedBy=\"Thread-2\" id=2"), banner);
        assertTrue(banner.contains("com.fs.starfarer.StarfarerLauncher.crashed"),
                "the banner must carry the caller's frames, not just the name: "
                        + banner);
    }

    @Test
    void shutdownBannerReportsAnExternalKillWhenNoThreadIsInsideExit() {
        ThreadSnapshot[] threads = {
                thread("Thread-2", 2L, "org.lwjgl.opengl.Display.update"),
        };

        String banner = ProcessExitWatchdog.describeShutdown(
                232_045L, "heap=812/2048MB", threads);

        assertTrue(banner.contains("exitRequestedBy=<external>"), banner);
    }

    @Test
    void heartbeatCarriesUptimeMemoryAndWhereTheGameThreadWas() {
        String beat = ProcessExitWatchdog.describeHeartbeat(
                232_045L, "heap=812/2048MB direct=64MB/128buf", 31, "Thread-2",
                Thread.State.RUNNABLE,
                new StackTraceElement("com.dillon.starsectormarines.battle.sim."
                        + "BattleSimulation", "tick", null, -1));

        assertTrue(beat.contains("uptime=232s"), beat);
        assertTrue(beat.contains("direct=64MB/128buf"), beat);
        assertTrue(beat.contains("gameThread=\"Thread-2\" state=RUNNABLE"), beat);
        assertTrue(beat.contains("BattleSimulation.tick"), beat);
    }

    @Test
    void memorySummaryReadsHeapAndDirectBufferFootprint() {
        String memory = ProcessExitWatchdog.memorySummary();

        assertTrue(memory.contains("heap="), memory);
        assertTrue(memory.contains("direct="), memory);
        assertTrue(memory.contains("nonHeap="), memory);
    }
}
