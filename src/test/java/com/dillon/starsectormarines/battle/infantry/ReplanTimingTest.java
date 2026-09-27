package com.dillon.starsectormarines.battle.infantry;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplanTimingTest {
    @Test
    void cpuEnvelopesAccumulateSelectionsWithoutPerGoalReads() {
        AtomicInteger reads = new AtomicInteger();
        long[] values = {100, 120, 150, 180, 220, 300};
        GoapInfantryBehavior.ReplanTiming timing = new GoapInfantryBehavior.ReplanTiming(
                () -> values[reads.getAndIncrement()]);
        long actualStart = timing.cpuNow();
        long selectionStart = timing.cpuNow();
        timing.record(RecoverFromAmbush.INSTANCE, 13_000_000L);
        timing.record(RecoverFromAmbush.INSTANCE, 100L);
        assertEquals(2, reads.get(), "individual relevance probes must not read CPU");
        timing.finishSelection(selectionStart);
        selectionStart = timing.cpuNow();
        timing.finishSelection(selectionStart);
        timing.finishReplan(System.nanoTime(), actualStart);
        GoapInfantryBehavior.ReplanBreakdown result = timing.snapshot();
        assertEquals(200, result.actualReplanCpuNanos());
        assertEquals(70, result.selectionCpuNanos());
        assertEquals(2, result.relevanceCalls());
        assertEquals(13_000_100L, result.relevanceNanos());
        assertTrue(result.actualReplanNanos() >= 0L);
        assertEquals(6, reads.get());
    }

    @Test
    void missingOrRegressingCpuReadingPoisonsAccumulationInsteadOfReportingPartialCpu() {
        long[] values = {10, 20, -1, 40, 50, 60, 100, 99};
        AtomicInteger reads = new AtomicInteger();
        GoapInfantryBehavior.ReplanTiming timing = new GoapInfantryBehavior.ReplanTiming(
                () -> values[reads.getAndIncrement()]);
        timing.finishSelection(timing.cpuNow());
        assertEquals(10, timing.snapshot().selectionCpuNanos());
        timing.finishSelection(timing.cpuNow());
        timing.finishSelection(timing.cpuNow());
        timing.finishReplan(System.nanoTime(), timing.cpuNow());
        assertEquals(-1, timing.snapshot().selectionCpuNanos());
        assertEquals(-1, timing.snapshot().actualReplanCpuNanos());
    }

    @Test
    void disabledAndUnstartedDiagnosticsKeepUnavailableSentinel() {
        GoapInfantryBehavior.ReplanTiming timing = new GoapInfantryBehavior.ReplanTiming(null);
        assertEquals(-1, timing.cpuNow());
        assertEquals(-1, timing.snapshot().selectionCpuNanos());
        assertEquals(-1, timing.snapshot().actualReplanCpuNanos());
        timing.finishSelection(timing.cpuNow());
        timing.finishReplan(System.nanoTime(), timing.cpuNow());
        assertEquals(-1, timing.snapshot().selectionCpuNanos());
        assertEquals(-1, timing.snapshot().actualReplanCpuNanos());
        assertEquals(-1, GoapInfantryBehavior.ReplanBreakdown.EMPTY.actualReplanCpuNanos());
    }

    @Test
    void productionClockIsReadOnlyWhenExplicitlyEnabled() {
        long actual = new GoapInfantryBehavior.ReplanTiming().cpuNow();
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (Boolean.getBoolean("battle.tail.replanCpu")
                && bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()) {
            assertTrue(actual >= 0L);
        } else {
            assertEquals(-1, actual);
        }
    }
}
