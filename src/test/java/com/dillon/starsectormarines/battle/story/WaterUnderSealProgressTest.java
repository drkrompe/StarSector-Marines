package com.dillon.starsectormarines.battle.story;

import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress.Phase;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterUnderSealProgressTest {
    private static void work(WaterUnderSealProgress progress, float dt, boolean safe) {
        progress.advance(dt, true, true, true, true, safe, false, false);
    }
    private static WaterUnderSealProgress reconnect() {
        var progress = new WaterUnderSealProgress();
        work(progress, WaterUnderSealProgress.UNLOAD_SECONDS, true);
        work(progress, WaterUnderSealProgress.RECONNECT_SECONDS, true);
        work(progress, WaterUnderSealProgress.RECONNECT_SECONDS, true);
        return progress;
    }
    @Test void clocksCannotReplacePhysicalArrivalAndProtectedWork() {
        var progress = new WaterUnderSealProgress();
        progress.advance(1000, true, true, true, false, true, true, true);
        work(progress, 1000, false);
        assertEquals(Phase.ESCORT, progress.phase());
        work(progress, WaterUnderSealProgress.UNLOAD_SECONDS, true);
        assertEquals(Phase.NORTH_ROOM, progress.phase());
        work(progress, 7, true);
        work(progress, 1000, false);
        assertEquals(7, progress.work());
        work(progress, WaterUnderSealProgress.RECONNECT_SECONDS - 7, true);
        assertEquals(Phase.SOUTH_ROOM, progress.phase());
        assertTrue(progress.report(true, 0, 0).northRoomReconnected());
        assertFalse(progress.report(true, 0, 0).southRoomReconnected());
    }
    @Test void victoryRequiresCommissionedPositionResolvedAttackAndSafeCompany() {
        var progress = reconnect();
        progress.advance(1000, true, true, true, true, true, false, true);
        assertEquals(Phase.CONSTRUCTION, progress.phase());
        progress.advance(0, true, true, true, true, true, true, false);
        assertEquals(Phase.ATTACK, progress.phase());
        progress.advance(1000, true, true, true, true, true, true, false);
        progress.advance(1000, true, true, true, true, false, true, true);

        assertEquals(Phase.ATTACK, progress.phase());
        progress.advance(0, true, true, true, true, true, true, true);
        assertEquals(Phase.COMPLETE, progress.phase());
        assertTrue(progress.report(true, 1, 8).complete());
    }
    @Test void destroyedPositionNeedsRealReplacementEvenAfterAttackResolution() {
        var progress = reconnect();
        progress.advance(0,true,true,true,true,true,true,false);
        assertEquals(Phase.ATTACK,progress.phase());
        progress.advance(1,true,true,true,true,true,false,true);
        assertEquals(Phase.CONSTRUCTION,progress.phase());
        assertTrue(progress.positionLost());
        var lost = progress.report(true,0,0);
        assertFalse(lost.attackResolved(), "Handover is not accepted until a standing gun and safe company cover exist");
        assertFalse(lost.turretConstructed());
        assertFalse(lost.complete());
        work(progress,1000,true);
        assertEquals(Phase.CONSTRUCTION,progress.phase());
        progress.advance(0,true,true,true,true,true,true,true);
        assertEquals(Phase.ATTACK,progress.phase());
        progress.advance(0,true,true,true,true,true,true,true);
        assertEquals(Phase.COMPLETE,progress.phase());
        assertTrue(progress.report(true,0,0).complete());
    }
    @Test void requiredCargoOrEveryTechnicianLostFailsWithoutRevivingThem() {
        var cargo = reconnect();
        cargo.advance(1, true, true, false, true, true, true, true);
        assertEquals(Phase.FAILED, cargo.phase());
        assertTrue(cargo.report(true, 0, 0).cargoLost());
        assertTrue(cargo.report(true, 0, 0).cargoDelivered());
        assertFalse(cargo.report(true, 0, 0).complete());
        var crew = reconnect();
        crew.advance(1, true, false, true, true, true, true, true);
        assertEquals(Phase.FAILED, crew.phase());
        work(crew, 1000, true);
        assertEquals(Phase.FAILED, crew.phase());
        assertFalse(new WaterUnderSealProgress().report(true, 0, 0).reconnected());
    }
    @Test void companyLossAndInvalidTimeCannotProduceSuccess() {
        var progress = reconnect();
        progress.advance(1, false, true, true, true, true, true, true);
        assertEquals(Phase.FAILED, progress.phase());
        var fresh = new WaterUnderSealProgress();
        for (float dt : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> work(fresh, dt, true));
        assertEquals(Phase.ESCORT, fresh.phase());
    }
}
