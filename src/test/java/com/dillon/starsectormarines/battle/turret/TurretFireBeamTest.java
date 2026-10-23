package com.dillon.starsectormarines.battle.turret;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A beam that opens off target lands short and walks in: full reach on target, the shortest at the widest arc. */
class TurretFireBeamTest {

    @Test
    void reachWalksInAsTheTurretComesOnTarget() {
        assertEquals(1f, TurretFireSystem.beamReachFraction(0f), 1e-6f);
        assertEquals(TurretFireSystem.BEAM_SHORT_FRACTION,
                TurretFireSystem.beamReachFraction(TurretAim.BEAM_OPEN_ARC_DEG), 1e-6f);
        assertEquals(TurretFireSystem.BEAM_SHORT_FRACTION, TurretFireSystem.beamReachFraction(-90f), 1e-6f);
        float prev = 2f;
        for (float off = 0f; off <= TurretAim.BEAM_OPEN_ARC_DEG; off += 2f) {
            float r = TurretFireSystem.beamReachFraction(off);
            assertTrue(r < prev, "reach shortens monotonically off target");
            prev = r;
        }
    }
}
