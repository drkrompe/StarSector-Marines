package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.mech.MechGaitState.FootEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RavBombardRigTest {

    private static final float DT = 1f / 30f;

    @Test
    void itWalksATripodAtATimeWithABeatOfFullSupportBetween() {
        RavBombardRig rig = new RavBombardRig(7L);
        long seenLift = 0L, seenLanding = 0L;
        int lastSet = -1, landings = 0;
        float sinceLanding = Float.MAX_VALUE;
        for (int t = 0; t < 120; t++) {
            rig.advance(0f, t * DT, 0f, 0f, 0f, 0f, DT);
            sinceLanding += DT;
            FootEvent landing = rig.lastTouchdown(), lift = rig.lastLiftOff();
            if (landing != null && landing.sequence() > seenLanding) {
                seenLanding = landing.sequence();
                sinceLanding = 0f;
                landings++;
            }
            if (lift != null && lift.sequence() > seenLift) {
                seenLift = lift.sequence();
                assertTrue(sinceLanding >= RavBombardRig.SUPPORT_SECONDS - DT, "all six stand a beat first");
                assertTrue(lift.foot() != lastSet, "the sets take turns");
                lastSet = lift.foot();
            }
            int inAir = -1;
            for (int i = 0; i < RavBombardRig.LEGS; i++) {
                if (rig.legLift(i) <= 0f) continue;
                int set = RavBombardRig.legSet(i);
                assertTrue(inAir < 0 || inAir == set, "never both sets in the air");
                inAir = set;
            }
        }
        assertTrue(landings >= 4, "a walking Bombard keeps stepping: " + landings);
    }

    @Test
    void aStandingBombardDoesNotStep() {
        RavBombardRig rig = new RavBombardRig(7L);
        for (int t = 0; t < 60; t++) rig.advance(2f, 3f, 40f, 0f, 0f, 0f, DT);
        assertEquals(null, rig.lastLiftOff());
    }
}
