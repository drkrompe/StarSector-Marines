package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReliefAssaultTest {
    @Test void missingOrLatePlayerCannotPostponeTheCommittedAssault() {
        assertEquals(28 * 30, ReliefAssault.releaseTick(0, -1, -1));
        assertEquals(55 * 30, ReliefAssault.releaseTick(1, -1, -1));
        assertEquals(28 * 30, ReliefAssault.releaseTick(0, 90 * 30, -1));
        assertEquals(55 * 30, ReliefAssault.releaseTick(1, 90 * 30, 120 * 30));
    }
    @Test void landingAndReachingTheLineAccelerateDifferentGroupsWithinTheirWindows() {
        assertEquals(18 * 30, ReliefAssault.releaseTick(0, 10 * 30, -1));
        assertEquals(26 * 30, ReliefAssault.releaseTick(0, 20 * 30, -1));
        assertEquals(55 * 30, ReliefAssault.releaseTick(1, 10 * 30, -1));
        assertEquals(38 * 30, ReliefAssault.releaseTick(1, 10 * 30, 20 * 30));
        assertEquals(48 * 30, ReliefAssault.releaseTick(1, 10 * 30, 40 * 30));
    }
}
