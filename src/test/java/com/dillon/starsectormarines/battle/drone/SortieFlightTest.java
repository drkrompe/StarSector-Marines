package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortieFlightTest {

    @Test
    void theExitPointLiesExitCellsBeyondTheMouthOnTheMouthsBearing() {
        float[] p = SortieFlight.exitPoint(10f, 10f, 0f, 0.5f);
        assertEquals(10f, p[0], 1e-4f);
        assertEquals(10f + SortieFlight.EXIT_CELLS, p[1], 1e-4f);
    }

    @Test
    void phasesFanTheExitBearingEitherSideOfTheMouth() {
        assertEquals(-SortieFlight.FAN_DEGREES, SortieFlight.exitBearing(0f, 0f), 1e-4f);
        assertEquals(0f, SortieFlight.exitBearing(0f, 0.5f), 1e-4f);
        assertEquals(SortieFlight.FAN_DEGREES, SortieFlight.exitBearing(0f, 1f), 1e-4f);
        assertEquals(100f - SortieFlight.FAN_DEGREES, SortieFlight.exitBearing(100f, 0f), 1e-4f);
    }

    @Test
    void theApproachPointIsOutsideTheMouthAndTurnsWithIt() {
        float[] ahead = SortieFlight.approachPoint(0f, 0f, 0f);
        float[] left = SortieFlight.approachPoint(0f, 0f, 90f);
        assertEquals(SortieFlight.APPROACH_CELLS, ahead[1], 1e-4f);
        assertEquals(-SortieFlight.APPROACH_CELLS, left[0], 1e-3f);
        assertEquals(0f, left[1], 1e-3f);
    }

    @Test
    void theApproachIsFurtherOutThanTheDockingRadius() {
        assertTrue(SortieFlight.APPROACH_CELLS > SortieFlight.DOCK_CELLS + SortieFlight.REACHED_CELLS);
    }

    @Test
    void aWaveIsALineWithOneGapBetweenNeighbours() {
        assertEquals(0f, SortieFlight.stagger(0, 0.8f), 1e-6f);
        assertEquals(0.8f, SortieFlight.stagger(1, 0.8f), 1e-6f);
        assertEquals(1.6f, SortieFlight.stagger(2, 0.8f), 1e-6f);
        assertEquals(0f, SortieFlight.stagger(-3, 0.8f), 1e-6f);
    }
}
