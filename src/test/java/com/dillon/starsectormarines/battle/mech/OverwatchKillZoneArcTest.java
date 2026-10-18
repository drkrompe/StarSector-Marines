package com.dillon.starsectormarines.battle.mech;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import org.junit.jupiter.api.Test;

/**
 * The walk a long-range support mech takes away from a threat it can already shoot must leave that
 * threat inside the torso envelope: straight back is the rear blind wedge, an oblique is not.
 */
class OverwatchKillZoneArcTest {

    @Test
    void straightBackTurnsTheBackOnTheThreat() {
        // Threat 16 cells east; the walk goes 8 cells west.
        assertTrue(OverwatchKillZone.turnsBack(10f, 10f, 2f, 10f, 26f, 10f));
    }

    @Test
    void anObliqueWithinTheArcDoesNot() {
        // 110 degrees off the threat bearing: 8 cells at that angle.
        double a = Math.toRadians(110);
        float toX = 10f + (float) (8 * Math.cos(a));
        float toY = 10f + (float) (8 * Math.sin(a));
        assertFalse(OverwatchKillZone.turnsBack(10f, 10f, toX, toY, 26f, 10f));
        // 130 degrees is past the margin.
        double b = Math.toRadians(130);
        assertTrue(OverwatchKillZone.turnsBack(10f, 10f,
                10f + (float) (8 * Math.cos(b)), 10f + (float) (8 * Math.sin(b)), 26f, 10f));
    }

    @Test
    void standingStillNeverTurnsTheBack() {
        assertFalse(OverwatchKillZone.turnsBack(10f, 10f, 10.5f, 10.4f, 26f, 10f));
    }

    @Test
    void theArcKeepingWalkLeavesTheThreatInsideTheTorsoEnvelope() {
        assertTrue(OverwatchKillZone.ARC_KEEPING_WALK_DEGREES
                < LayeredMechAppearance.MAX_TORSO_TWIST_DEGREES);
    }
}
