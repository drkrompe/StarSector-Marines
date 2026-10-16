package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.DistrictEscortProgress.Phase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistrictEscortProgressTest {
    private static final float DT = 1f / 30f;

    @Test void linkUpNeedsUnbrokenCompanyCoverAtTheCarrier() {
        var p = new DistrictEscortProgress();
        for (int i = 0; i < 90; i++) p.advance(DT, true, 2, true, i != 60, false, false, false);
        assertEquals(Phase.LINK_UP, p.phase(), "contact resets the link-up");
        for (int i = 0; i < 125; i++) p.advance(DT, true, 2, true, true, false, false, false);
        assertEquals(Phase.ESCORT, p.phase()); assertTrue(p.linkedUp());
    }

    @Test void deliveryNeedsLoadPartyAndCompanyTogetherUnderCover() {
        var p = escorting();
        p.advance(DT, true, 2, true, true, true, false, true);
        p.advance(DT, true, 2, true, true, true, true, false);
        p.advance(DT, true, 2, true, false, true, true, true);
        assertEquals(Phase.ESCORT, p.phase());
        p.advance(DT, true, 1, true, true, true, true, true);
        assertEquals(Phase.COMPLETE, p.phase());
        var report = p.report(1, 0);
        assertTrue(report.complete()); assertEquals(1, report.partyLost());
    }

    @Test void losingTheLoadThePartyOrTheCompanyFailsTheAttempt() {
        var lostLoad = escorting(); lostLoad.advance(DT, true, 2, false, true, false, false, false);
        assertEquals(Phase.FAILED, lostLoad.phase()); assertTrue(lostLoad.report(0, 0).cargoLost());
        var lostParty = escorting(); lostParty.advance(DT, true, 0, true, true, false, false, false);
        assertEquals(Phase.FAILED, lostParty.phase()); assertFalse(lostParty.report(2, 0).complete());
        var absent = new DistrictEscortProgress(); absent.advance(DT, false, 2, true, false, false, false, false);
        assertEquals(Phase.FAILED, absent.phase()); assertFalse(absent.report(0, 0).linkedUp());
        absent.advance(DT, true, 2, true, true, true, true, true);
        assertEquals(Phase.FAILED, absent.phase(), "terminal states do not reopen");
        assertThrows(IllegalArgumentException.class, () -> new DistrictEscortProgress().advance(DT, true, 3, true, true, false, false, false));
    }

    private static DistrictEscortProgress escorting() {
        var p = new DistrictEscortProgress();
        for (int i = 0; i < 125; i++) p.advance(DT, true, 2, true, true, false, false, false);
        assertEquals(Phase.ESCORT, p.phase());
        return p;
    }
}
