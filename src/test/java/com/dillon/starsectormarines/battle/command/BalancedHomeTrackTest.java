package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalancedHomeTrackTest {

    private static final boolean[] ALL = {true, true, true};

    @Test
    void aLandingInOneLaneSpreadsAcrossAllThree() {
        int[] load = new int[3];
        for (int squad = 0; squad < 9; squad++) {
            load[ConquestCommand.leastLoadedTrack(load, 1, ALL)]++;
        }
        assertEquals(3, load[0]);
        assertEquals(3, load[1]);
        assertEquals(3, load[2]);
    }

    @Test
    void theNaturalLaneWinsATie() {
        assertEquals(1, ConquestCommand.leastLoadedTrack(new int[] {2, 2, 2}, 1, ALL));
    }

    @Test
    void anEmptierLaneBeatsTheNaturalOne() {
        assertEquals(2, ConquestCommand.leastLoadedTrack(new int[] {4, 3, 0}, 1, ALL));
    }

    @Test
    void theNearerLaneWinsBetweenEquallyEmptyOnes() {
        assertEquals(1, ConquestCommand.leastLoadedTrack(new int[] {0, 0, 5}, 2, ALL));
    }

    @Test
    void aLaneWithNothingToTakeIsNobodysHome() {
        boolean[] middleOnly = {false, true, false};
        int[] load = new int[3];
        for (int squad = 0; squad < 9; squad++) {
            load[ConquestCommand.leastLoadedTrack(load, 0, middleOnly)]++;
        }
        assertEquals(0, load[0], "nobody homed to an empty strip, even one landed in");
        assertEquals(9, load[1]);
        assertEquals(0, load[2]);
    }

    @Test
    void theLanesWithWorkShareTheForce() {
        boolean[] outer = {true, false, true};
        int[] load = new int[3];
        for (int squad = 0; squad < 8; squad++) {
            load[ConquestCommand.leastLoadedTrack(load, 1, outer)]++;
        }
        assertEquals(4, load[0]);
        assertEquals(0, load[1]);
        assertEquals(4, load[2]);
    }

    @Test
    void withWorkOnNoLaneEveryLaneIsEligible() {
        assertEquals(2, ConquestCommand.leastLoadedTrack(new int[] {4, 3, 0}, 1, new boolean[3]));
    }
}
