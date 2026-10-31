package com.dillon.starsectormarines.battle.airsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The standing patrol goes round its stations, skipping any that want no fighter. */
class StandingPatrolTest {

    @Test
    void goesRoundTheStationsThatWantAFighter() {
        boolean[] wanted = {true, false, true, true};
        assertEquals(0, StandingPatrol.next(wanted, -1));
        assertEquals(2, StandingPatrol.next(wanted, 0), "past the covered one");
        assertEquals(3, StandingPatrol.next(wanted, 2));
        assertEquals(0, StandingPatrol.next(wanted, 3), "and round again");
        assertEquals(-1, StandingPatrol.next(new boolean[]{false, false}, 0));
    }
}
