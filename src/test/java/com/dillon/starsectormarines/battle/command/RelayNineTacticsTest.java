package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState.*;
import static org.junit.jupiter.api.Assertions.*;

class RelayNineTacticsTest {
    @Test void contestDoesNotReleaseTheReserveOrCreateAHold() {
        var policy = new RelayNineTactics();
        var states = List.of(MARINE_HELD, CONTESTED, DEFENDER_HELD, DEFENDER_HELD);
        policy.observe(states);
        assertEquals(-1, policy.firstRoadLoss());
        assertEquals(0, policy.militiaTarget(0));
        assertEquals(1, policy.assaultTarget(states));
    }
    @Test void twoDetachmentsKeepDifferentGainsThroughReversals() {
        var policy = new RelayNineTactics();
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD));
        assertEquals(1, policy.militiaTarget(0));
        assertEquals(1, policy.militiaTarget(1));
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, DEFENDER_HELD));
        var lost = List.of(MARINE_HELD, DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD);
        policy.observe(lost);
        assertEquals(1, policy.militiaTarget(0));
        assertEquals(2, policy.militiaTarget(1));
        assertEquals(1, policy.assaultTarget(lost));
        assertEquals(2, policy.counterTarget(lost));
        assertEquals(1, policy.firstRoadLoss());
    }
    @Test void gateFirstChangesTheHoldAndReserveWithoutChangingVictoryOrder() {
        var policy = new RelayNineTactics();
        var gate = List.of(MARINE_HELD, DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD);
        policy.observe(gate);
        assertEquals(2, policy.firstRoadLoss());
        assertEquals(2, policy.militiaTarget(0));
        assertEquals(1, policy.assaultTarget(gate));
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, MARINE_HELD, DEFENDER_HELD));
        assertEquals(1, policy.militiaTarget(1));
    }
    @Test void militiaCanContestTheRearWhileCompanyContinuesItsPush() {
        var policy = new RelayNineTactics();
        policy.observe(List.of(MARINE_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD));
        var contest = List.of(MARINE_HELD, CONTESTED, DEFENDER_HELD, DEFENDER_HELD);
        policy.observe(contest);
        assertEquals(2, policy.assaultTarget(contest));
        assertEquals(1, policy.militiaTarget(0));
        assertEquals(1, policy.counterTarget(contest));
        assertEquals(0, policy.assaultTarget(List.of(DEFENDER_HELD, MARINE_HELD, DEFENDER_HELD, DEFENDER_HELD)));
    }
}
