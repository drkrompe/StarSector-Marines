package com.dillon.starsectormarines.campaign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgreusArcProgressTest {
    @Test void activeMobilizationAndSiteSurviveReloadAndVictoryPaysExactlyOnce() throws Exception {
        var p = new AgreusArcProgress(); String id = p.missionId();
        assertTrue(p.begin(id, "agreus", "Agreus"));
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(p); }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { p = (AgreusArcProgress) input.readObject(); }
        assertTrue(p.acceptsTarget("Agreus"));
        assertFalse(p.acceptsTarget("Asharu"));
        assertEquals(-1, success(p, id, "asharu"));
        assertEquals(26000, success(p, id, "agreus"));
        assertTrue(p.completed()); assertNull(p.missionId());
        assertEquals(-1, success(p, id, "agreus"));
    }
    @Test void defeatRetainsConfirmedEmployerLossesAndRequiresANewAttempt() {
        var p = new AgreusArcProgress(); String old = p.missionId();
        assertFalse(p.begin(old, "asharu")); assertTrue(p.begin(old, "agreus"));
        assertEquals(0, p.settle(old, "agreus", false, 0, false, true, false, false, false, true, 2, 3, true));
        assertEquals(2, p.techniciansLost()); assertEquals(3, p.militiaLost()); assertEquals(1, p.repairLoadsLost());
        assertNotEquals(old, p.missionId()); assertFalse(p.begin(old, "agreus"));
        String retry = p.missionId(); assertTrue(p.begin(retry, "agreus"));
        assertEquals(-1, success(p, old, "agreus"));
        assertEquals(26000, success(p, retry, "agreus"));
        assertEquals(2, p.techniciansLost());
    }
    @Test void contradictoryOrIncompleteReceiptsLeaveTheActiveAttemptOpen() {
        var p = new AgreusArcProgress(); String id = p.missionId(); p.begin(id, "agreus");
        assertEquals(-1, p.settle(id, "agreus", true, 26000, true, true, false, true, true, true, 0, 0, false));
        assertEquals(-1, p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 0, 0, true));
        assertEquals(-1, p.settle(id, "agreus", true, 26000, true, true, true, true, true, true, 2, 0, false));
        assertEquals(-1, p.settle(id, "agreus", true, 52000, true, true, true, true, true, true, 0, 0, false));
        assertEquals(id, p.missionId()); assertEquals(26000, success(p, id, "agreus"));
    }
    private static int success(AgreusArcProgress p, String id, String planet) {
        return p.settle(id, planet, true, 26000, true, true, true, true, true, true, 0, 0, false);
    }
}
