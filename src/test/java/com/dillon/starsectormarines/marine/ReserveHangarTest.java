package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.BoatDeckViewModel;
import com.dillon.starsectormarines.ops.ReserveRow;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reserve hangar takes whole machines off their berths and gantries and puts the same machines back. */
class ReserveHangarTest {

    @Test void aStoredBoatLeavesHerBerthVacantAndComesBackAsHerself() {
        BoatDeck deck = deck(ShuttleType.VALKYRIE, 2);
        CampaignBoat boat = deck.boats().get(0);
        var hangar = new ReserveHangar();

        assertEquals(ReserveHangar.Status.HANGAR_FULL, hangar.storeBoat(deck, boat.id(), 0), "no refit, no room");
        assertSame(boat, deck.boats().get(0));
        assertEquals(ReserveHangar.Status.STORED, hangar.storeBoat(deck, boat.id(), 2));
        assertNull(deck.boatById(boat.id()));
        assertEquals(List.of(0), deck.vacantBerths());
        assertEquals(0, deck.spares(BoatFitting.STANDARD_PLATING.id()), "nothing is stripped: her fit goes with her");

        assertEquals(ReserveHangar.Status.RECALLED, hangar.recallBoat(deck, boat.id()));
        assertSame(boat, deck.boats().get(0), "the same boat, name, fit and repair clock");
        assertEquals(0, hangar.stored());
        assertEquals(ReserveHangar.Status.UNKNOWN_MACHINE, hangar.recallBoat(deck, boat.id()));
    }

    @Test void aBoatComesBackOnlyToAFreeBerthOfHerPattern() {
        BoatDeck valkyries = deck(ShuttleType.VALKYRIE, 1);
        CampaignBoat boat = valkyries.boats().get(0);
        var hangar = new ReserveHangar();
        hangar.storeBoat(valkyries, boat.id(), 2);

        BoatDeck hermes = deck(ShuttleType.HERMES, 1);
        hermes.vacate(hermes.boats().get(0).id());
        assertEquals(-1, ReserveHangar.vacantBerthFor(hermes, boat));
        assertEquals(ReserveHangar.Status.NO_VACANT_PLACE, hangar.recallBoat(hermes, boat.id()), "her pattern is not what these bays hold");

        valkyries.berth(0, BoatDeck.stockBoat("stand_in", "Stand-in", ShuttleType.VALKYRIE));
        assertEquals(ReserveHangar.Status.NO_VACANT_PLACE, hangar.recallBoat(valkyries, boat.id()), "her berth was filled meanwhile");
        assertEquals(1, hangar.stored());
    }

    @Test void aStoredMechKeepsItsPartsOwnedButNeverSparesAndCapacityCountsEveryKind() {
        var bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        mech.rename("Old Faithful");
        Map<String, Integer> available = availableWeapons(bay);
        var hangar = new ReserveHangar();

        assertEquals(ReserveHangar.Status.STORED, hangar.storeMachine(bay, mech.id(), 2));
        assertNull(bay.mechById(mech.id()));
        assertEquals(available, availableWeapons(bay), "its fitted weapons are not loose in stores");

        var vehicle = new CampaignVehicle("support_vehicle_09", "Mule 09", VehicleType.HEAVY_APC);
        bay.addVehicle(MechBay.STARTER_SQUAD_ID, vehicle);
        bay.addKit(VehicleKit.SPALL_LINER.id, 1);
        assertTrue(bay.fitKit(vehicle.id(), VehicleKit.SPALL_LINER));
        assertEquals(ReserveHangar.Status.STORED, hangar.storeMachine(bay, vehicle.id(), 2));
        assertEquals(0, bay.availableKit(VehicleKit.SPALL_LINER.id), "a stored vehicle's kit is not a spare");
        var third = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        assertEquals(ReserveHangar.Status.HANGAR_FULL, hangar.storeMachine(bay, third.id(), 2), "mechs and vehicles share the room");

        assertEquals(ReserveHangar.Status.RECALLED, hangar.recallMachine(bay, mech.id(), MechBay.STARTER_SQUAD_ID, 4));
        assertSame(mech, bay.mechById(mech.id()));
        assertEquals("Old Faithful", mech.displayName());
        assertEquals(available.size(), availableWeapons(bay).size());
        for (MechWeaponComponent weapon : MechWeaponComponent.values())
            assertTrue(bay.ownedWeapon(weapon.id) >= bay.installedWeapon(weapon.id), weapon.id + ": owned again once fitted aboard");
        assertEquals(ReserveHangar.Status.RECALLED, hangar.recallMachine(bay, vehicle.id(), MechBay.STARTER_SQUAD_ID, 4));
        assertEquals(1, bay.ownedKit(VehicleKit.SPALL_LINER.id));
        assertEquals(0, bay.availableKit(VehicleKit.SPALL_LINER.id));
    }

    @Test void aMachineComesBackOnlyToAWorkingVacantGantry() {
        var bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        var hangar = new ReserveHangar();
        hangar.storeMachine(bay, mech.id(), 4);
        int occupied = bay.squadById(MechBay.STARTER_SQUAD_ID).occupied();

        assertEquals(ReserveHangar.Status.NO_VACANT_PLACE, hangar.recallMachine(bay, mech.id(), MechBay.STARTER_SQUAD_ID, occupied),
                "the lance's working gantries are all taken");
        assertEquals(ReserveHangar.Status.NO_VACANT_PLACE, hangar.recallMachine(bay, mech.id(), "no_such_lance", 4));
        assertEquals(1, hangar.stored());
        assertEquals(ReserveHangar.Status.RECALLED, hangar.recallMachine(bay, mech.id(), MechBay.STARTER_SQUAD_ID, occupied + 1));
    }

    @Test void theBoatDecksStoreTakesASecondPressAndRecallStandsHerBackUp() {
        BoatDeck deck = deck(ShuttleType.HERMES, 1);
        CampaignBoat boat = deck.boats().get(0);
        var hangar = new ReserveHangar();
        var model = new BoatDeckViewModel(new Reactor(), deck, new TestPurse(0), "Second Wind");
        model.reserve(hangar, () -> 2);
        Map<String, Object> props = new HashMap<>();
        model.putReserveProps(props);
        model.selectBoatAction(0).run();
        Runnable store = (Runnable) props.get("storeAction");

        store.run();
        assertSame(boat, deck.boats().get(0), "the first press only arms STORE");
        assertTrue(signal(props, "storeClasses").contains("armed"));
        store.run();
        assertEquals(List.of(0), deck.vacantBerths());
        assertEquals("hidden", signal(props, "storeClasses"), "the room is back on the deck overview");

        @SuppressWarnings("unchecked")
        List<ReserveRow> rows = ((Signal<List<ReserveRow>>) props.get("reserveRows")).get();
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).recallable());
        rows.get(0).recall().run();
        assertSame(boat, deck.boats().get(0));
        assertFalse(signal(props, "reserveClasses").equals("hidden"), "the strip stays while the refit has room");
    }

    /** A deck of {@code berths} berths of one pattern, each holding a stock boat. */
    private static BoatDeck deck(ShuttleType pattern, int berths) {
        var deck = new BoatDeck();
        deck.reconcile("ship", pattern, berths);
        for (int berth : deck.vacantBerths())
            deck.berth(berth, BoatDeck.stockBoat(pattern.name().toLowerCase() + "_" + berth, "Boat " + berth, pattern));
        return deck;
    }

    private static Map<String, Integer> availableWeapons(MechBay bay) {
        Map<String, Integer> available = new HashMap<>();
        for (MechWeaponComponent weapon : MechWeaponComponent.values())
            if (bay.availableWeapon(weapon.id) > 0) available.put(weapon.id, bay.availableWeapon(weapon.id));
        return available;
    }

    @SuppressWarnings("unchecked")
    private static String signal(Map<String, Object> props, String key) {
        return ((Signal<String>) props.get(key)).get();
    }
}
