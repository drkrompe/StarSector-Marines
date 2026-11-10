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

    private static final List<RepairDrone.Fallen> WRECKS = List.of(new RepairDrone.Fallen(7L, 5f, 10f),
            new RepairDrone.Fallen(8L, 40f, 10f));

    @Test
    void anUrgentChargeComesBeforeAWreckAndAWreckBeforeLightWear() {
        RepairDrone drone = new RepairDrone();
        Goal urgent = drone.decide(0f, 10f, 0f, 10f, true, false, CHARGES, WRECKS, null, 0);
        assertEquals(2L, urgent.entityId(), "the charge at 0.4 is urgent");
        List<Charge> light = List.of(new Charge(1L, 10f, 10f, 0.8f));
        Goal raise = drone.decide(0f, 10f, 0f, 10f, true, false, light, WRECKS, null, 15);
        assertEquals(7L, raise.entityId(), "the nearest wreck");
        assertEquals(Kind.GO, raise.kind());
        assertEquals(Kind.RAISE, drone.decide(5f, 10f, 0f, 10f, true, false, light, WRECKS, null, 30).kind());
        assertEquals(1L, drone.decide(0f, 10f, 0f, 10f, true, false, light, List.of(), null, 45).entityId());
    }

    @Test
    void aRaiseUnderWayIsFinishedBeforeAnythingNew() {
        RepairDrone drone = new RepairDrone();
        drone.decide(5f, 10f, 0f, 10f, true, false, List.of(), WRECKS, null, 0);
        Goal still = drone.decide(5f, 10f, 0f, 10f, true, false, CHARGES, WRECKS, null, 15);
        assertEquals(Kind.RAISE, still.kind(), "the urgent charge waits for the raise");
        assertEquals(7L, still.entityId());
    }

    @Test
    void dryItReloadsAtItsBayAndWithNoBayLeftItGoesHome() {
        RepairDrone drone = new RepairDrone();
        drone.draw(1000f);
        RepairDrone.Bay bay = new RepairDrone.Bay(0f, 20f, 200f);
        assertEquals(Kind.GO, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, WRECKS, bay, 0).kind());
        assertEquals(Kind.RELOAD, drone.decide(0.5f, 20f, 0f, 10f, true, false, CHARGES, WRECKS, bay, 15).kind());
        ServiceBay stocked = new ServiceBay(200f);
        drone.reload(stocked);
        assertEquals(200f, drone.stock(), 1e-4f, "the bay gives what it has");
        assertEquals(0f, stocked.stock(), 1e-4f);
        drone.draw(1000f);
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, WRECKS, null, 30).kind());
        assertEquals(Kind.HOME, drone.decide(19f, 10f, 0f, 10f, true, false, CHARGES, WRECKS,
                new RepairDrone.Bay(0f, 20f, 0f), 45).kind(), "an empty bay gives nothing");
    }

    @Test
    void aRaiseDrawsItsOwnRateFromTheLoad() {
        RepairDrone drone = new RepairDrone();
        assertEquals(Wreck.RAISE_PER_SECOND * 2f, drone.drawRaise(2f), 1e-4f);
        ServiceBay bay = new ServiceBay();
        drone.reload(bay);
        assertEquals(RepairDrone.STOCK, drone.stock(), 1e-4f, "a reload tops up to a full load");
        assertEquals(ServiceBay.STOCK - Wreck.RAISE_PER_SECOND * 2f, bay.stock(), 1e-3f);
    }
}
