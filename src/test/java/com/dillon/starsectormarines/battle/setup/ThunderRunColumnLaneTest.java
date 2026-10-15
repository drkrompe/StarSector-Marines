package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThunderRunColumnLaneTest {
    @Test void aLaneWeavesInsideTheBandAndArrivesStraightOnItsGoal() {
        VehicleType hauler = VehicleType.CARGO_CARRIER;
        float[][] lane = ThunderRunColumnDriver.lane(20f, 75f, 214f, 75f, 1.1f, 50f, 1f, hauler);
        float[] xs = lane[0], ys = lane[1];
        assertEquals(214f, xs[xs.length - 1]);
        assertEquals(75f, ys[ys.length - 1], 1e-4f);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) assertTrue(xs[i] > xs[i - 1], "Always forward");
            lo = Math.min(lo, ys[i]); hi = Math.max(hi, ys[i]);
            assertTrue(ys[i] - hauler.visualWidthCells / 2 >= ThunderRunColumnDriver.BAND_TOP - 1e-4f);
            assertTrue(ys[i] + hauler.visualWidthCells / 2 <= ThunderRunColumnDriver.BAND_BOTTOM + 1e-4f);
        }
        assertTrue(hi - lo > 1.5f, "Not a ruler-straight line");
        assertEquals(75f, ys[ys.length - 2], .5f, "Settled before the goal");
    }

    @Test void aLaneChangeIsGentleEnoughForTheChassis() {
        float[][] lane = ThunderRunColumnDriver.lane(100f, 75f, 200f, 77.4f, 0f, 50f, 0f, VehicleType.CARGO_CARRIER);
        for (int i = 1; i < lane[0].length; i++) {
            float slope = Math.abs(lane[1][i] - lane[1][i - 1]) / (lane[0][i] - lane[0][i - 1]);
            assertTrue(slope < .3f, "Lateral drift per cell travelled stays a merge, not a swerve: " + slope);
        }
    }

    @Test void aShortHopIsJustTheTwoEnds() {
        float[][] lane = ThunderRunColumnDriver.lane(100f, 75f, 106f, 75f, 1f, 50f, 0f, VehicleType.CARGO_CARRIER);
        assertEquals(2, lane[0].length);
    }
}
