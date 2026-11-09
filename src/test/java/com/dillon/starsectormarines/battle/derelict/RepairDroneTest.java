package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.RepairDrone.Charge;
import com.dillon.starsectormarines.battle.derelict.RepairDrone.Goal;
import com.dillon.starsectormarines.battle.derelict.RepairDrone.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RepairDroneTest {

    private static final List<Charge> CHARGES = List.of(new Charge(1L, 10f, 10f, 0.8f),
            new Charge(2L, 20f, 10f, 0.4f), new Charge(3L, 30f, 10f, 0.99f));

    @Test
    void itTendsTheMostWornChargeAndLeavesTheWholeAlone() {
        assertEquals(2L, RepairDrone.pick(CHARGES).entityId());
        assertNull(RepairDrone.pick(List.of(new Charge(3L, 30f, 10f, 0.99f))));
    }

    @Test
    void itFliesToItsChargeAndMendsWithinReach() {
        RepairDrone drone = new RepairDrone();
        Goal far = drone.decide(0f, 10f, 0f, 10f, true, false, CHARGES, 0);
        assertEquals(Kind.GO, far.kind());
        assertEquals(20f, far.x());
        assertEquals(Kind.MEND, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, 15).kind());
    }

    @Test
    void underFireItStandsDownHomeForAWhile() {
        RepairDrone drone = new RepairDrone();
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, true, true, CHARGES, 100).kind());
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, 100
                + RepairDrone.STAND_DOWN_TICKS - 1).kind());
        assertEquals(Kind.MEND, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, 100
                + RepairDrone.STAND_DOWN_TICKS).kind());
    }

    @Test
    void withItsNodeDownOrItsStockSpentItStaysHome() {
        RepairDrone drone = new RepairDrone();
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, false, false, CHARGES, 0).kind());
        float drawn = drone.draw(1000f);
        assertEquals(RepairDrone.STOCK, drawn, 1e-3f);
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, 0).kind());
        drone.refund(5f);
        assertEquals(5f, drone.stock(), 1e-4f);
    }
}
