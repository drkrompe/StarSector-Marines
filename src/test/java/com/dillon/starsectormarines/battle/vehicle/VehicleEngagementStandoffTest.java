package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechDuelBand;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Band;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A stand-off hull's band: beyond the target's reach where it can be, the edge of its own where it cannot. */
class VehicleEngagementStandoffTest {

    @Test
    void anOutReachingStandOffKeepsTheDuelBandsStandOff() {
        Band chosen = new Band(MechDuelBand.Stance.STANDOFF, 37.5f, 39f);
        assertSame(chosen, VehicleEngagement.standoff(chosen, 40f));
    }

    @Test
    void anOutRangedStandOffHullHoldsAtTheEdgeOfItsOwnReachNotTheDuelBand() {
        Band duel = new Band(MechDuelBand.Stance.DUEL, 28.8f, 36.8f);
        Band edge = VehicleEngagement.standoff(duel, 40f);
        assertEquals(MechDuelBand.Stance.STANDOFF, edge.stance());
        assertEquals(40f * VehicleEngagement.EDGE_BAND_SHARE, edge.min(), 1e-4f);
        assertEquals(39f, edge.max(), 1e-4f);
    }
}
