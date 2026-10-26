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
    void itDigsInATripodAtATimeAndUprootsTheSameWay() {
        RavBombardRig rig = new RavBombardRig(7L);
        rig.advance(0f, 0f, 0f, 0f, 0f, 0f, DT);
        float walking = spread(rig);
        int ticks = Math.round(3f / DT);
        int lifts = 0, lastSet = -1;
        long seen = 0L;
        for (int t = 1; t <= ticks; t++) {
            rig.advance(0f, 0f, 0f, 0f, 0f, t / (float) ticks, DT);
            FootEvent lift = rig.lastLiftOff();
            if (lift != null && lift.sequence() > seen) {
                seen = lift.sequence();
                assertTrue(lift.foot() != lastSet, "the tripods take turns");
                lastSet = lift.foot();
                lifts++;
            }
        }
        for (int t = 0; t < 30; t++) rig.advance(0f, 0f, 0f, 0f, 0f, 1f, DT);
        assertEquals(RavBombardRig.DIG_STROKES, lifts, "each stroke of the dig is a tripod step");
        assertTrue(spread(rig) > walking * 1.05f, "dug in, the claws stand wider");
        for (int t = 1; t <= ticks; t++) rig.advance(0f, 0f, 0f, 0f, 0f, 1f - t / (float) ticks, DT);
        for (int t = 0; t < 30; t++) rig.advance(0f, 0f, 0f, 0f, 0f, 0f, DT);
        assertEquals(walking, spread(rig), walking * 0.02f, "uprooted, they are back in their walking stance");
    }

    /** How far the walking claws stand from the body on average, cells. */
    private static float spread(RavBombardRig rig) {
        float sum = 0f;
        int n = 0;
        for (int i = 0; i < RavBombardRig.LEGS; i++) {
            if (RavBombardRig.legSet(i) < 0) continue;
            sum += (float) Math.hypot(rig.clawX(i), rig.clawY(i));
            n++;
        }
        return sum / n;
    }

    @Test
    void aStandingBombardDoesNotStep() {
        RavBombardRig rig = new RavBombardRig(7L);
        for (int t = 0; t < 60; t++) rig.advance(2f, 3f, 40f, 0f, 0f, 0f, DT);
        assertEquals(null, rig.lastLiftOff());
    }
}
