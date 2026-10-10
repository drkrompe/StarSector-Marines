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
        assertTrue(policy.reserveCommitted());
        assertEquals(1, policy.militiaTarget());
        assertEquals(2, policy.assaultTarget(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD)));
        var reversal = List.of(MARINE_HELD, DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD);
        policy.observe(reversal);
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
}
