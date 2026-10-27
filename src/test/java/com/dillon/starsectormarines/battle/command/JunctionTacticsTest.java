package com.dillon.starsectormarines.battle.command;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState.*;
import static org.junit.jupiter.api.Assertions.*;

class JunctionTacticsTest {
    @Test void contestedIsNotASecuredPosition() {
        var policy = new JunctionTactics();
        var states = List.of(MARINE_HELD, CONTESTED, DEFENDER_HELD, DEFENDER_HELD);
        policy.observe(states);
        assertFalse(policy.reserveCommitted());
        assertEquals(0, policy.militiaTarget());
        assertEquals(1, policy.assaultTarget(states));
    }
    @Test void firstGainRemainsTheHoldAndCounterattackTargetAfterReversal() {
        var policy = new JunctionTactics();
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD));
        assertTrue(policy.reserveCommitted(), "the first gain sends the reserve in");
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, CONTESTED, DEFENDER_HELD));
        assertFalse(policy.roadColumnCommitted(), "the Rill road column waits for the second gain itself");
        assertEquals(1, policy.militiaTarget());
        assertEquals(2, policy.assaultTarget(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD)));
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, DEFENDER_HELD));
        assertTrue(policy.roadColumnCommitted());
        assertEquals(2, policy.secondSecured());
        var reversal = List.of(MARINE_HELD, DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD);
        policy.observe(reversal);
        assertEquals(2, policy.secondSecured());
        assertEquals(1, policy.firstSecured());
        assertEquals(1, policy.militiaTarget());
        assertEquals(1, policy.assaultTarget(reversal));
    }
    @Test void playerCanTakeAnotherSiteFirstAndLostBeachheadIsARealObjective() {
        var policy = new JunctionTactics();
        policy.observe(List.of(MARINE_HELD, DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD));
        assertEquals(2, policy.militiaTarget());
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, DEFENDER_HELD));
        assertEquals(2, policy.firstSecured());
        assertEquals(0, policy.assaultTarget(List.of(DEFENDER_HELD, MARINE_HELD, MARINE_HELD, MARINE_HELD)));
    }
    @Test void aCompanySittingOnItsGainsIsHitOnTheClock() {
        var policy = new JunctionTactics();
        var sitting = List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD);
        policy.observe(sitting, 100);
        assertTrue(policy.reserveCommitted());
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, CONTESTED, DEFENDER_HELD), 100 + JunctionTactics.QUIET_TICKS - 1);
        assertFalse(policy.roadColumnCommitted());
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, CONTESTED, DEFENDER_HELD), 100 + JunctionTactics.QUIET_TICKS);
        assertTrue(policy.roadColumnCommitted(), "the second blow follows the first on the clock");
        assertEquals(2, policy.roadTarget(), "it strikes the site the company is fighting for");
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, MARINE_HELD), 100 + 2 * JunctionTactics.QUIET_TICKS);
        assertEquals(2, policy.roadTarget(), "a launched blow keeps its target");
    }
}
