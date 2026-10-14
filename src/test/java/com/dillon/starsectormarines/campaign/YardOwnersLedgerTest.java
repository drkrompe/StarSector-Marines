package com.dillon.starsectormarines.campaign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YardOwnersLedgerTest {
    @Test void prerequisiteAndFrozenMobilizationRejectForeignOrContradictoryReceipts() {
        var p = new AgreusArcProgress();
        assertFalse(p.begin(AgreusArcProgress.YARD_PREFIX + 1, "agreus"));
        p = afterWarrant(); String id = p.nextMissionId();
        assertEquals(-1, win(p, id));
        assertFalse(p.begin(id, "jangala"));
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(-1, p.settleYard(id, "asharu", true, 36000, true, true, true, true, true, false, 0, 0));
        assertEquals(-1, p.settleYard(id, "agreus", true, 72000, true, true, true, true, true, false, 0, 0));
        assertEquals(-1, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, true, 0, 0));
        assertEquals(-1, p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 2, 0));
        assertEquals(-1, p.settleYard(id, "agreus", false, 0, true, false, true, true, true, false, 0, 0));
        assertEquals(36000, win(p, id));
        assertNull(p.nextMissionId());
        assertEquals(-1, win(p, id));
    }

    @Test void replacementAndLossesSurviveReloadWithoutReopeningThePreviousAttempt() throws Exception {
        var p = afterWarrant(); String first = p.nextMissionId();
        assertTrue(p.begin(first, "agreus", "Agreus"));
        assertEquals(0, p.settleYard(first, "agreus", false, 0, true, false, false, false, false, true, 2, 3));
        String retry = p.nextMissionId(); assertNotEquals(first, retry);
        assertTrue(p.begin(retry, "agreus", "Agreus"));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { p = (AgreusArcProgress) in.readObject(); }
        assertTrue(p.acceptsTarget("Agreus")); assertFalse(p.acceptsTarget("Asharu"));
        assertEquals(-1, win(p, first));
        assertEquals(36000, win(p, retry));
        assertEquals(2, p.yardTechniciansLost()); assertEquals(3, p.yardConstablesLost()); assertEquals(1, p.toolingLost());
        assertTrue(p.yardCompleted()); assertTrue(p.warrantCompleted()); assertTrue(p.completed());
    }

    private static AgreusArcProgress afterWarrant() {
        var p = new AgreusArcProgress(); String id = p.missionId();
        assertTrue(p.begin(id, "agreus"));
        assertEquals(26000, p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, false));
        id = p.nextMissionId(); assertTrue(p.begin(id, "agreus"));
        assertEquals(30000, p.settleWarrant(id, "agreus", true, 30000, true, true, true, true, false, 0, 0));
        return p;
    }

    private static int win(AgreusArcProgress p, String id) {
        return p.settleYard(id, "agreus", true, 36000, true, true, true, true, true, false, 0, 0);
    }
}
