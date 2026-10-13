package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitedWarrantProgressTest {
    @Test void elapsedTimeOrEmployerPresenceCannotRecoverAnything() {
        var p = new LimitedWarrantProgress(false);
        p.advance(100, true, true, true, false, true, false);
        assertEquals(LimitedWarrantProgress.Phase.RECORDS, p.phase());
        assertFalse(p.report(0, 0).complete());
    }
    @Test void interruptedAuthenticationRestartsAndPhysicalReturnRequiresCompany() {
        var p = new LimitedWarrantProgress(false);
        p.advance(7, true, true, true, true, false, false);
        p.advance(1, true, true, true, false, false, false);
        assertEquals(0, p.work());
        p.advance(8, true, true, true, true, false, false);
        assertTrue(p.recordsRecovered());
        p.advance(8, true, true, true, true, false, false);
        assertEquals(LimitedWarrantProgress.Phase.EGRESS, p.phase());
        p.advance(40, true, true, true, true, true, false);
        assertFalse(p.report(0, 0).complete());
        p.advance(1, true, false, true, true, true, true);
        assertTrue(p.report(1, 0).complete(), "Technician loss after the kit is recovered does not erase that work");
    }
    @Test void cargoDestructionFailsEvenAfterCalibrationWhileRecordsRemain() {
        var p = new LimitedWarrantProgress(true);
        p.advance(8, true, true, true, true, false, false);
        p.advance(1, true, true, false, true, true, true);
        assertEquals(LimitedWarrantProgress.Phase.FAILED, p.phase());
        assertTrue(p.report(0, 0).cargoLost());
        assertTrue(p.recordsRecovered());
        assertFalse(p.report(0, 0).complete());
    }
    @Test void losingSupplierBeforeCalibrationFails() {
        var p = new LimitedWarrantProgress(true);
        p.advance(1, true, false, true, true, true, true);
        assertEquals(LimitedWarrantProgress.Phase.FAILED, p.phase());
    }
}
