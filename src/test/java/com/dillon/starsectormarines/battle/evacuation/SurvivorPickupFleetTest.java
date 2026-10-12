package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.evacuation.SurvivorPickupFleet.Carrier;
import com.dillon.starsectormarines.battle.evacuation.SurvivorPickupFleet.Waiting;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SurvivorPickupFleetTest {
    private static ShuttleMission mission(float x) {
        return new ShuttleMission(x, 20.5f, 60, 20, 60, 20, 0, 6);
    }
    private static Carrier register(SurvivorPickupFleet fleet, boolean mech) {
        var delivery = mission(20.5f);
        delivery.state = ShuttleState.GONE;
        var c = new Carrier(delivery, ShuttleType.AEROSHUTTLE,
                FittedBoat.standard(ShuttleType.AEROSHUTTLE), mech);
        fleet.register(c);
        return c;
    }
    private static ShuttleMission launch(Carrier c) {
        var pickup = mission(20.5f);
        pickup.awaitingEvacuees = true;
        pickup.pickup = new PickupManifest(c.capacity());
        return pickup;
    }
    @Test void bothCommittedBoatsFlyTogetherButNeverDuplicateAnActiveTrip() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var b = register(fleet, false);
        var launched = new ArrayList<Carrier>();
        fleet.dispatch(12, 0, c -> { launched.add(c); return launch(c); });
        assertEquals(List.of(a, b), launched);
        assertNotSame(a.frame, b.frame);
        fleet.dispatch(12, 0, c -> fail("Aircraft is still away"));
        assertEquals(6, a.capacity()); assertEquals(6, b.capacity());
    }
    @Test void inboundSeatsCoverDemandAndACompletedBoatCanFlyAnotherLoad() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var b = register(fleet, false);
        fleet.dispatch(6, 0, SurvivorPickupFleetTest::launch);
        assertNotNull(a.pickup); assertNull(b.pickup);
        a.pickup.pickup.recover();
        var previous = a.pickup;
        fleet.dispatch(7, 0, SurvivorPickupFleetTest::launch);
        assertNotSame(previous, a.pickup); assertNotNull(b.pickup);
    }
    @Test void oneBoatReturnsForTheSurvivorsItsFirstTripCouldNotCarry() {
        var fleet = new SurvivorPickupFleet();
        var boat = register(fleet, false);
        fleet.dispatch(12, 0, SurvivorPickupFleetTest::launch);
        var first = boat.pickup;
        for (int i = 1; i <= 6; i++)
            assertTrue(first.pickup.board(new PickupManifest.Passenger(i, Faction.MARINE, "soldier-" + i, null)));
        first.awaitingEvacuees = false;
        first.state = ShuttleState.DEPARTING;
        fleet.dispatch(6, 0, c -> fail("The boat has not returned yet"));
        first.pickup.recover();
        fleet.dispatch(6, 0, SurvivorPickupFleetTest::launch);
        assertNotSame(first, boat.pickup);
        assertEquals(6, first.pickup.passengers().size());
        assertEquals(PickupManifest.State.RECOVERED, first.pickup.state());
        assertTrue(boat.pickup.pickup.passengers().isEmpty());
    }
    @Test void aDestroyedDeliveryDoesNotSuppressAnotherBoatOfTheSamePattern() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var b = register(fleet, false);
        a.delivery.destroyed = true;
        fleet.dispatch(12, 0, SurvivorPickupFleetTest::launch);
        assertEquals(List.of(b), fleet.carriers()); assertNull(a.pickup); assertNotNull(b.pickup);
        b.pickup.pickup.lose();
        fleet.dispatch(6, 0, c -> fail("Lost boat cannot return"));
        assertTrue(fleet.carriers().isEmpty());
    }
    @Test void deliveryMustFinishAndMechLiftHasIndependentDemand() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var heavy = register(fleet, true);
        a.delivery.state = ShuttleState.DEPARTING;
        fleet.dispatch(12, 1, SurvivorPickupFleetTest::launch);
        assertNull(a.pickup); assertNotNull(heavy.pickup); assertEquals(1, heavy.capacity());
        a.delivery.state = ShuttleState.GONE;
        fleet.dispatch(12, 1, SurvivorPickupFleetTest::launch);
        assertNotNull(a.pickup);
    }
    @Test void separateRampsReserveSeatsAndBoardOnIndependentClocks() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var b = register(fleet, false); var heavy = register(fleet, true);
        fleet.dispatch(12, 1, c -> {
            var p = launch(c);
            p.state = ShuttleState.LANDED;
            return p;
        });
        // All marines prefer the first ramp; capacity still divides the queue across both boats.
        var people = new ArrayList<Waiting>();
        for (int i = 1; i <= 12; i++) people.add(new Waiting(i, false, 20.5f, 20.5f));
        people.add(new Waiting(13, true, 20.5f, 20.5f));
        fleet.assignRamps(people);
        assertSame(a, fleet.ramp(1)); assertSame(b, fleet.ramp(7)); assertSame(heavy, fleet.ramp(13));
        assertTrue(a.readyToBoard(100)); a.boarded(100);
        assertFalse(a.readyToBoard(101)); assertTrue(b.readyToBoard(100));
        assertTrue(a.readyToBoard(124));
        a.pickup.pickup.board(new PickupManifest.Passenger(1, Faction.MARINE, "one", null));
        people.remove(0);
        fleet.assignRamps(people);
        assertNull(fleet.ramp(1)); assertSame(b, fleet.ramp(7));
        a.pickup.awaitingEvacuees = false;
        fleet.assignRamps(people);
        assertNull(fleet.ramp(2)); assertSame(b, fleet.ramp(7));
    }
    @Test void noSafeFreeRampDefersOnlyThatBoat() {
        var fleet = new SurvivorPickupFleet();
        var a = register(fleet, false); var b = register(fleet, false);
        fleet.dispatch(12, 0, c -> c == a ? launch(c) : null);
        assertNotNull(a.pickup); assertNull(b.pickup);
        fleet.dispatch(12, 0, SurvivorPickupFleetTest::launch);
        assertNotNull(b.pickup);
    }
}
