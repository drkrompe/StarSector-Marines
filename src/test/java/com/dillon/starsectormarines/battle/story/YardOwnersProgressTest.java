package com.dillon.starsectormarines.battle.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YardOwnersProgressTest {
    @Test void employerPresenceAndTimeCannotSecureCompanyObjectives() {
        var p = new YardOwnersProgress();
        p.advance(200,true,true,true,true,0,7,0);
        assertEquals(YardOwnersProgress.Phase.DISPATCH,p.phase());
        assertFalse(p.report(0,0).complete());
    }
    @Test void recaptureInterruptsInspectionAndRequiresCompanyToRestoreTheHold() {
        var p = new YardOwnersProgress();
        p.advance(6,true,true,true,true,7,7,0);
        assertEquals(YardOwnersProgress.Phase.INSPECTION,p.phase());
        p.advance(2,true,true,true,true,2,7,1);
        assertFalse(p.held(0)); assertEquals(0,p.inspection());
        p.advance(100,true,true,true,true,2,7,0);
        assertFalse(p.held(0));
        p.advance(6,true,true,true,true,3,7,0);
        p.advance(12,true,true,true,true,2,7,0);
        assertTrue(p.report(1,2).complete());
    }
    @Test void inspectionNeedsAllHoldersLivingTechnicianAndCompanyAtTools() {
        var p = new YardOwnersProgress();
        p.advance(6,true,true,true,false,7,7,0);
        p.advance(100,true,true,true,false,2,7,0);
        assertEquals(0,p.inspection());
        p.advance(100,true,true,true,true,2,3,0);
        assertEquals(0,p.inspection());
        p.advance(100,true,true,true,true,0,7,0);
        assertFalse(p.report(0,0).complete());
    }
    @Test void destroyedToolingOrEntireInspectionPartyCannotBeReplacedInsideBattle() {
        for (boolean tooling : new boolean[]{false,true}) {
            var p = new YardOwnersProgress();
            p.advance(1,true,tooling,!tooling,true,7,7,0);
            assertEquals(YardOwnersProgress.Phase.FAILED,p.phase());
            p.advance(100,true,true,true,true,7,7,0);
            assertFalse(p.report(tooling ? 2 : 0,0).complete());
        }
    }
}
