package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReliefAssaultTest {
    @Test void missingOrLatePlayerCannotPostponeTheCommittedAssault() {
        assertEquals(60 * 30, ReliefAssault.releaseTick(0, -1, -1));
        assertEquals(95 * 30, ReliefAssault.releaseTick(1, -1, -1));
        assertEquals(60 * 30, ReliefAssault.releaseTick(0, 90 * 30, -1));
        assertEquals(95 * 30, ReliefAssault.releaseTick(1, 90 * 30, 120 * 30));
    }
    @Test void landingAndReachingTheLineAccelerateDifferentGroupsWithinTheirWindows() {
        assertEquals(45 * 30, ReliefAssault.releaseTick(0, 10 * 30, -1));
        assertEquals(51 * 30, ReliefAssault.releaseTick(0, 45 * 30, -1));
        assertEquals(95 * 30, ReliefAssault.releaseTick(1, 10 * 30, -1));
        assertEquals(75 * 30, ReliefAssault.releaseTick(1, 10 * 30, 20 * 30));
        assertEquals(83 * 30, ReliefAssault.releaseTick(1, 10 * 30, 75 * 30));
    }
}
