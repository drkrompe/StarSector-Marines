package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomingLeadTest {

    private static MissileFlightSpec spec(MissileFlightSpec.Guidance guidance, float turn) {
        return new MissileFlightSpec(guidance, 0f, 300f, 0f, 0f, turn, 10f, 0f, 50f, null, null, 0f, 0f,
                false, 0f, 0f, null);
    }

    @Test
    void anUnguidedOrPoorlyGuidedMissileDoesNotLead() {
        assertEquals(0f, HomingLead.seconds(spec(MissileFlightSpec.Guidance.UNGUIDED, 240f), 40f));
        assertEquals(0f, HomingLead.seconds(spec(MissileFlightSpec.Guidance.POORLY_GUIDED, 240f), 40f));
    }

    @Test
    void aSharperSeekerLeadsFurther() {
        float dull = HomingLead.seconds(spec(MissileFlightSpec.Guidance.HOMING, 60f), 40f);
        float sharp = HomingLead.seconds(spec(MissileFlightSpec.Guidance.HOMING, 240f), 40f);
        assertTrue(sharp > dull && dull > 0f);
    }

    @Test
    void theAimPointMovesAlongTheTargetsVelocity() {
        float[] aim = HomingLead.apply(10f, 20f, 2f, -1f, 3f);
        assertEquals(16f, aim[0]);
        assertEquals(17f, aim[1]);
    }
}
