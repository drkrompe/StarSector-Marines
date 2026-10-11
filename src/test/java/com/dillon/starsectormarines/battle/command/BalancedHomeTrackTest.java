package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalancedHomeTrackTest {

    @Test
    void aLandingInOneLaneSpreadsAcrossAllThree() {
        int[] load = new int[3];
        for (int squad = 0; squad < 9; squad++) {
            load[ConquestCommand.leastLoadedTrack(load, 1)]++;
        }
        assertEquals(3, load[0]);
        assertEquals(3, load[1]);
        assertEquals(3, load[2]);
    }

    @Test
    void theNaturalLaneWinsATie() {
        assertEquals(1, ConquestCommand.leastLoadedTrack(new int[] {2, 2, 2}, 1));
    }

    @Test
    void anEmptierLaneBeatsTheNaturalOne() {
        assertEquals(2, ConquestCommand.leastLoadedTrack(new int[] {4, 3, 0}, 1));
    }

    @Test
    void theNearerLaneWinsBetweenEquallyEmptyOnes() {
        assertEquals(1, ConquestCommand.leastLoadedTrack(new int[] {0, 0, 5}, 2));
    }
}
