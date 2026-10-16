package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.SmallWarProgress.Phase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallWarProgressTest {
    private static final float DT = 1f / 30f;
    private static final int ALL = 7;

    @Test void stagesFollowPhysicalCustodyAndTheRearPosition() {
        var p = new SmallWarProgress();
        hold(p, 1, 1, 0, false, false);
        assertEquals(Phase.REAR_POSITION, p.phase(), "customs is taken, the rear position is not built");
        hold(p, 1, 1, 0, false, true);
        assertTrue(p.rearPositionBuilt());
        assertEquals(Phase.FUEL_CONTROL, p.phase());
        hold(p, 3, 3, 0, false, false);
        assertEquals(Phase.DISPATCH, p.phase(), "losing the rear gun later does not undo the established position");
        hold(p, ALL, ALL, 0, false, false);
        assertEquals(Phase.HANDOVER, p.phase());
        // A hostile at customs reopens it: lost required sites must be reclaimed.
        p.advance(DT, true, true, true, true, false, ALL, ALL, 1);
        assertEquals(Phase.CUSTOMS, p.phase());
        assertEquals(0, p.handover());
    }

    @Test void theSplitHappensOnceAndOnlyWhenFuelControlActuallyFalls() {
        var p = new SmallWarProgress();
        int splits = 0;
        for (int i = 0; i < 400; i++) if (p.advance(DT, true, true, true, false, false, 1, 1, 2)) splits++;
        assertEquals(0, splits, "contested fuel control never splits Greyhook");
        for (int i = 0; i < 400; i++) if (p.advance(DT, true, true, true, false, false, 2, 2, 0)) splits++;
        assertEquals(1, splits);
        assertTrue(p.split());
        for (int i = 0; i < 400; i++) if (p.advance(DT, true, true, true, false, false, 0, 0, 2)) splits++;
        assertEquals(1, splits, "recapture never orders a second withdrawal");
    }

    @Test void handoverNeedsTheLivingPartyInsideWithCompanyPresentAndNothingContested() {
        var p = established();
        for (int i = 0; i < 600; i++) p.advance(DT, true, true, true, false, false, ALL, ALL, 0);
        assertEquals(Phase.HANDOVER, p.phase(), "holding the sites without the custody party hands nothing over");
        for (int i = 0; i < 600; i++) p.advance(DT, true, true, true, true, false, 5, ALL, 0);
        assertEquals(Phase.HANDOVER, p.phase(), "no company infantry in fuel control");
        for (int i = 0; i < 600 && !p.terminal(); i++) p.advance(DT, true, true, true, true, false, ALL, ALL, 0);
        assertEquals(Phase.COMPLETE, p.phase());
        var report = p.report(1, 2, 5, true);
        assertTrue(report.complete());
        assertTrue(report.houseCustody());
        assertEquals(5, report.greyhookWithdrawn());
    }

    @Test void destroyedInfrastructureOrTheWholePartyFailsTheAttempt() {
        var lost = established();
        lost.advance(DT, true, false, true, false, false, ALL, ALL, 0);
        assertEquals(Phase.FAILED, lost.phase());
        assertTrue(lost.report(0, 0, 0, false).infrastructureLost());
        assertFalse(lost.report(0, 0, 0, false).complete());

        var party = established();
        party.advance(DT, true, true, false, false, false, ALL, ALL, 0);
        assertEquals(Phase.FAILED, party.phase());

        var absent = new SmallWarProgress();
        absent.advance(DT, false, true, true, false, false, 0, 0, 0);
        assertEquals(Phase.FAILED, absent.phase(), "employer troops alone cannot carry the attempt");
    }

    @Test void alliedPresenceAloneCapturesNothing() {
        var p = new SmallWarProgress();
        for (int i = 0; i < 900; i++) p.advance(DT, true, true, true, true, true, 0, ALL, 0);
        assertFalse(p.visited(0) || p.visited(1) || p.visited(2));
        assertFalse(p.split());
    }

    private static SmallWarProgress established() {
        var p = new SmallWarProgress();
        hold(p, 1, 1, 0, false, true);
        hold(p, ALL, ALL, 0, false, false);
        assertEquals(Phase.HANDOVER, p.phase());
        return p;
    }

    private static void hold(SmallWarProgress p, int company, int friendly, int contested, boolean party, boolean rear) {
        for (int i = 0; i < 200; i++) p.advance(DT, true, true, true, party, rear, company, friendly, contested);
    }
}
