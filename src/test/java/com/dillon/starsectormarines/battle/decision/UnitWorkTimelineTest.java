package com.dillon.starsectormarines.battle.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitWorkTimelineTest {
    @Test void growthPreservesEveryFieldAndExecutionOrder() {
        UnitWorkTimeline timeline = new UnitWorkTimeline();
        assertEquals(0, timeline.size());
        for (int i = 0; i < 257; i++) {
            timeline.record(900L - i, 1000L + i * 10, 1004L + i * 10, i, "action-" + i);
        }
        assertEquals(257, timeline.size());
        for (int i = 0; i < timeline.size(); i++) {
            assertEquals(900L - i, timeline.entityId(i), "do not sort by entity id");
            assertEquals(1000L + i * 10, timeline.startNanos(i));
            assertEquals(1004L + i * 10, timeline.endNanos(i));
            assertEquals(i, timeline.cpuNanos(i));
            assertEquals("action-" + i, timeline.action(i));
        }
    }

    @Test void resetReusesTimelineWithoutExposingEarlierEntries() {
        UnitWorkTimeline timeline = new UnitWorkTimeline();
        for (int i = 0; i < 100; i++) timeline.record(i, i, i + 1, i, "old-action");
        timeline.reset();
        assertEquals(0, timeline.size());
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.entityId(0));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.startNanos(0));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.endNanos(0));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.cpuNanos(0));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.action(0));

        timeline.record(42, 500, 600, 70, "");
        assertEquals(1, timeline.size());
        assertEquals(42, timeline.entityId(0));
        assertEquals(500, timeline.startNanos(0));
        assertEquals(600, timeline.endNanos(0));
        assertEquals(70, timeline.cpuNanos(0));
        assertEquals("", timeline.action(0), "reflex-only work must not inherit an earlier GOAP action");
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.entityId(1));
        timeline.reset();
        timeline.reset();
        assertEquals(0, timeline.size());
    }

    @Test void cpuSentinelsAndExactIntervalsAreRetainedWithoutNormalization() {
        UnitWorkTimeline timeline = new UnitWorkTimeline();
        timeline.record(1, -20, -10, -1, "HoldZone");
        timeline.record(2, -10, -10, Long.MIN_VALUE, "");
        timeline.record(3, 0, 90, 0, "ClearZone");
        assertEquals(-1, timeline.cpuNanos(0));
        assertEquals(Long.MIN_VALUE, timeline.cpuNanos(1));
        assertEquals(0, timeline.cpuNanos(2));
        assertEquals(-20, timeline.startNanos(0));
        assertEquals(-10, timeline.endNanos(0));
        assertEquals(timeline.endNanos(0), timeline.startNanos(1));
        assertEquals(timeline.startNanos(1), timeline.endNanos(1));
        assertEquals(0, timeline.startNanos(2));
        assertEquals(90, timeline.endNanos(2));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.entityId(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> timeline.cpuNanos(timeline.size()));
    }
}
