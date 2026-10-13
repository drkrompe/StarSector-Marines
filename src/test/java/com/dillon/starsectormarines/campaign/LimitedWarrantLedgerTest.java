package com.dillon.starsectormarines.campaign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitedWarrantLedgerTest {
    @Test void cannotSkipNorthWeirAndInspectionDoesNotAcceptWarrant() {
        var p = new AgreusArcProgress();
        assertFalse(p.begin(AgreusArcProgress.WARRANT_PREFIX + 1, "agreus"));
        p = afterWeir();
        String id = p.nextMissionId();
        assertEquals(-1, win(p, id));
        assertFalse(p.warrantCompleted());
        assertFalse(p.begin(id, "jangala"));
        assertTrue(p.begin(id, "agreus", "Agreus"));
        assertEquals(30000, win(p, id));
        assertNull(p.nextMissionId());
        assertEquals(-1, win(p, id));
    }
    @Test void retainedRecordsAndReplacementAttemptSurviveSerialization() throws Exception {
        var p = afterWeir(); String first = p.nextMissionId(); p.begin(first, "agreus", "Agreus");
        assertEquals(0, p.settleWarrant(first, "agreus", false, 0, true, true, false, false, true, 1, 2));
        assertTrue(p.recordsRecovered()); assertEquals(1, p.calibrationLoadsLost());
        String retry = p.nextMissionId(); assertNotEquals(first, retry); assertTrue(p.begin(retry, "agreus", "Agreus"));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { p = (AgreusArcProgress)in.readObject(); }
        assertTrue(p.acceptsTarget("Agreus"));
        assertEquals(-1, win(p, first));
        assertEquals(-1, p.settleWarrant(retry, "agreus", false, 0, false, false, false, false, false, 0, 0));
        assertEquals(30000, win(p, retry));
        assertTrue(p.completed()); assertTrue(p.warrantCompleted());
        assertEquals(1, p.warrantTechniciansLost()); assertEquals(2, p.warrantConstablesLost());
    }
    @Test void wrongPaymentForeignSiteAndContradictoryReceiptDoNotConsumeAttempt() {
        var p = afterWeir(); String id = p.nextMissionId(); p.begin(id, "agreus");
        assertEquals(-1, p.settleWarrant(id, "asharu", true, 30000, true, true, true, true, false, 0, 0));
        assertEquals(-1, p.settleWarrant(id, "agreus", true, 60000, true, true, true, true, false, 0, 0));
        assertEquals(-1, p.settleWarrant(id, "agreus", true, 30000, true, true, true, true, true, 0, 0));
        assertEquals(-1, p.settleWarrant(id, "agreus", true, 30000, false, true, true, true, false, 0, 0));
        assertEquals(-1, p.settleWarrant(id, "agreus", true, 30000, true, true, false, true, false, 0, 0));
        assertEquals(30000, win(p, id));
    }
    private static AgreusArcProgress afterWeir() {
        var p = new AgreusArcProgress(); String id = p.missionId(); p.begin(id, "agreus");
        assertEquals(26000, p.settle(id,"agreus",true,26000,true,true,true,true,true,true,0,0,false));
        return p;
    }
    private static int win(AgreusArcProgress p, String id) {
        return p.settleWarrant(id,"agreus",true,30000,true,true,true,true,false,0,0);
    }
}
