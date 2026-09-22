package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SquadReplanDiagnosticsTest {

    @Test
    void countsActualReplansAndKeepsOnlyTheEightSlowestCalls() {
        SquadReplanSystem.DiagnosticsCollector collector =
                new SquadReplanSystem.DiagnosticsCollector();
        for (int id = 1; id <= 10; id++) {
            collector.record(sample(id, id * 10L, id % 2 == 0));
        }
        SquadReplanSystem.TickDiagnostics snapshot = collector.snapshot();

        assertEquals(10, snapshot.squadCount());
        assertEquals(5, snapshot.replanCount());
        assertEquals(List.of(10, 9, 8, 7, 6, 5, 4, 3),
                snapshot.slowestSquads().stream()
                        .map(SquadReplanSystem.SquadSample::squadId).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.slowestSquads().clear());

        collector.clear();
        collector.record(sample(11, 5L, false));
        assertEquals(10, snapshot.squadCount(),
                "a prior tick's published snapshot must remain intact");
        SquadReplanSystem.TickDiagnostics next = collector.snapshot();
        assertEquals(1, next.squadCount());
        assertEquals(0, next.replanCount());
        assertEquals(List.of(11), next.slowestSquads().stream()
                .map(SquadReplanSystem.SquadSample::squadId).toList());
    }

    private static SquadReplanSystem.SquadSample sample(int id, long nanos,
                                                        boolean replanned) {
        return new SquadReplanSystem.SquadSample(id,
                SquadReplanSystem.SquadKind.INFANTRY, nanos, replanned,
                false, false, false, false, false, false, false, false);
    }
}
