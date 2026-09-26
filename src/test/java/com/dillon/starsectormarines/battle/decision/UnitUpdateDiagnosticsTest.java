package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.unit.UnitRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitUpdateDiagnosticsTest {

    @Test
    void parallelismOverrideLeavesTheDefaultPolicyUnchanged() {
        assertEquals(17, UnitUpdateSystem.resolvePoolParallelism(18, null));
        assertEquals(1, UnitUpdateSystem.resolvePoolParallelism(1, null));
        assertEquals(8, UnitUpdateSystem.resolvePoolParallelism(18, "8"));
        assertEquals(1, UnitUpdateSystem.resolvePoolParallelism(18, "1"));
        assertThrows(IllegalArgumentException.class,
                () -> UnitUpdateSystem.resolvePoolParallelism(18, "0"));
        assertThrows(IllegalArgumentException.class,
                () -> UnitUpdateSystem.resolvePoolParallelism(18, "32768"));
        assertThrows(IllegalArgumentException.class,
                () -> UnitUpdateSystem.resolvePoolParallelism(18, "invalid"));
    }

    @Test
    void unsupportedOrResetCpuCountersAreNotReportedAsZeroWork() {
        assertEquals(25L, UnitUpdateSystem.cpuDeltaNanos(100L, 125L));
        assertEquals(0L, UnitUpdateSystem.cpuDeltaNanos(100L, 100L));
        assertEquals(-1L, UnitUpdateSystem.cpuDeltaNanos(-1L, 125L));
        assertEquals(-1L, UnitUpdateSystem.cpuDeltaNanos(100L, -1L));
        assertEquals(-1L, UnitUpdateSystem.cpuDeltaNanos(100L, 99L));
    }

    @Test
    void keepsOnlySlowestEightAcrossWorkerCollectors() {
        UnitUpdateSystem.SlowUnitCollector first = new UnitUpdateSystem.SlowUnitCollector();
        UnitUpdateSystem.SlowUnitCollector second = new UnitUpdateSystem.SlowUnitCollector();
        for (long id = 1; id <= 16; id++) {
            UnitUpdateSystem.SlowUnitCollector worker = id <= 8 ? first : second;
            worker.offer(new UnitUpdateSystem.UnitSample(id, UnitRole.COMBATANT, id * 100L));
        }

        UnitUpdateSystem.SlowUnitCollector merged = new UnitUpdateSystem.SlowUnitCollector();
        for (UnitUpdateSystem.UnitSample sample : first.samples()) merged.offer(sample);
        for (UnitUpdateSystem.UnitSample sample : second.samples()) merged.offer(sample);

        List<Long> ids = merged.samples().stream()
                .map(UnitUpdateSystem.UnitSample::entityId).toList();
        assertEquals(List.of(16L, 15L, 14L, 13L, 12L, 11L, 10L, 9L), ids);
        assertFalse(merged.accepts(1L, 100L));
        assertTrue(merged.accepts(17L, 1700L));
    }

    @Test
    void equalDurationOrderingAndResetAreStable() {
        UnitUpdateSystem.SlowUnitCollector collector = new UnitUpdateSystem.SlowUnitCollector();
        for (long id = 1; id <= 9; id++) {
            collector.offer(new UnitUpdateSystem.UnitSample(id, UnitRole.GARRISON, 100L));
        }
        assertEquals(9L, collector.samples().get(0).entityId());
        assertEquals(2L, collector.samples().get(7).entityId());

        collector.clear();
        assertTrue(collector.samples().isEmpty());
        collector.offer(new UnitUpdateSystem.UnitSample(23L, UnitRole.TURRET, 10L));
        assertEquals(UnitRole.TURRET, collector.samples().get(0).role());
    }
}
