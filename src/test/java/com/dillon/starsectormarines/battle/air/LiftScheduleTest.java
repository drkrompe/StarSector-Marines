package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.LiftSchedule.DropOff;
import com.dillon.starsectormarines.battle.air.LiftSchedule.Lift;
import com.dillon.starsectormarines.battle.air.LiftSchedule.Phase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The lift timer is a reading of the transport, so what it reads is the rule:
 * a re-arming craft counts down its re-arm and its descent, a craft leaving
 * with sorties behind it is already the next lift, and berths side by side are
 * one place to the player.
 */
class LiftScheduleTest {

    /** Lands at (10,0) from a descent point 20 cells off, at 10 cells a second. */
    private static ShuttleMission ferry(ShuttleState state, int cycle, int cycles) {
        ShuttleMission m = new ShuttleMission(10f, 0f, 30f, 0f, 30f, 0f, 4f, 6);
        m.state = state;
        m.currentCycle = cycle;
        m.totalCycles = cycles;
        m.rearmDelay = 8f;
        return m;
    }

    @Test
    void aRearmingLiftCountsDownItsDelayAndItsDescent() {
        Lift lift = LiftSchedule.lift(ferry(ShuttleState.PENDING, 0, 3), 30f, 0f, 10f);
        assertEquals(Phase.INBOUND, lift.phase());
        assertEquals(4f + 2f + LiftSchedule.SETTLE_SECONDS, lift.secondsToTouchdown(), 1e-4);
        assertEquals(2, lift.liftsToCome());
    }

    @Test
    void aLiftLeavingWithSortiesBehindItIsTheNextLift() {
        ShuttleMission m = ferry(ShuttleState.DEPARTING, 1, 3);
        Lift lift = LiftSchedule.lift(m, 20f, 0f, 10f);
        assertEquals(Phase.INBOUND, lift.phase());
        assertEquals(1f + 8f + 2f + LiftSchedule.SETTLE_SECONDS, lift.secondsToTouchdown(), 1e-4);
        assertEquals(0, lift.liftsToCome());
        m.currentCycle = 2;
        assertNull(LiftSchedule.lift(m, 20f, 0f, 10f), "the last sortie leaving brings nobody");
    }

    @Test
    void onlyInfantryDeliveriesAreLifts() {
        ShuttleMission gunship = ferry(ShuttleState.PENDING, 0, 1);
        gunship.gunship = true;
        assertNull(LiftSchedule.lift(gunship, 30f, 0f, 10f));
    }

    @Test
    void neighbouringBerthsAreOneDropOffShowingItsSoonestLift() {
        Lift late = new Lift(0f, 0f, Phase.INBOUND, 9f, 6, 2, 20f);
        Lift soon = new Lift(6f, 0f, Phase.INBOUND, 3f, 6, 1, 20f);
        Lift elsewhere = new Lift(80f, 0f, Phase.INBOUND, 1f, 6, 0, 20f);
        List<DropOff> points = LiftSchedule.group(List.of(late, soon, elsewhere));
        assertEquals(2, points.size());
        DropOff pair = points.get(0);
        assertEquals(soon, pair.next());
        assertEquals(3f, pair.x(), 1e-4);
        assertEquals(6, pair.marines(), "the later lift is too far behind to be the same wave");
        assertEquals(3 + 2 - 1, pair.liftsToCome());
    }

    @Test
    void aPairComingDownTogetherIsOneWave() {
        Lift first = new Lift(0f, 0f, Phase.INBOUND, 4f, 6, 2, 20f);
        Lift second = new Lift(5f, 0f, Phase.INBOUND, 5f, 6, 2, 20f);
        DropOff point = LiftSchedule.group(List.of(first, second)).get(0);
        assertEquals(12, point.marines());
        assertEquals(4, point.liftsToCome());
    }

    @Test
    void aLiftOnThePadOutranksOneStillInbound() {
        Lift inbound = new Lift(0f, 0f, Phase.INBOUND, 8f, 6, 0, 20f);
        Lift down = new Lift(4f, 0f, Phase.UNLOADING, 0f, 3, 0, 20f);
        assertEquals(down, LiftSchedule.group(List.of(inbound, down)).get(0).next());
    }
}
