package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MachineSystems;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.HullLocations;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One machine, one part catalog: a part fits on what the chassis offers, never on whether it is a mech or a vehicle. */
class MachinePartRequirementsTest {

    @Test
    void aPartsRequirementsAreMetByCapabilitiesNotByTheKindOfMachine() {
        MachineCapabilities unarmed = new MachineCapabilities(false, false);
        MachineCapabilities beam = new MachineCapabilities(true, false);
        MachineCapabilities fed = new MachineCapabilities(true, true);

        assertNull(MechInternal.SPALL_LINER.unmet(unarmed), "a liner goes on anything");
        assertEquals(MechInternal.Requirement.FED_WEAPON, MechInternal.READY_RACK.unmet(beam));
        assertNull(MechInternal.READY_RACK.unmet(fed));
        assertEquals(MechInternal.Requirement.ARMED, MechInternal.FACTORY_CAPACITOR_BANK.unmet(unarmed));
        assertNull(MechInternal.STANDARD_LOADER.unmet(unarmed));
    }

    @Test
    void aHullOffersAFedWeaponOnlyWhenItsGunDrawsOnAmmunition() {
        assertFalse(VehicleRack.takes(VehicleType.SUPPLY_TRUCK, MechInternal.READY_RACK), "no gun to feed");
        assertTrue(VehicleRack.takes(VehicleType.SUPPLY_TRUCK, MechInternal.SPALL_LINER));
        for (VehicleType beltFed : List.of(VehicleType.HEAVY_APC, VehicleType.MILITIA_TECHNICAL,
                VehicleType.DOZER_BREACHER)) {
            assertTrue(VehicleRack.takes(beltFed, MechInternal.READY_RACK), "a belt-fed gun's belt deepens: " + beltFed);
        }
    }

    @Test
    void aCompartmentsLinerAndReadyRackBecomeTheFramesSystems() {
        assertEquals(MachineSystems.NONE, MechCompartment.systems(VehicleRack.stockFit(VehicleType.HEAVY_APC)));
        MachineSystems both = MechCompartment.systems(List.of(MechInternal.SPALL_LINER, MechInternal.READY_RACK));
        assertEquals(1.2f, both.structureFactor(), 1e-6f);
        assertEquals(1.5f, both.ammoFactor(), 1e-6f);

        MechFrameBudget.Fit lined = MechFrameBudget.Fit.stock(MechVariant.WARDEN)
                .withInternal(MechInternal.Role.LINER, MechInternal.SPALL_LINER);
        assertEquals(1.2f, lined.frame().systems().structureFactor(), 1e-6f, "the mech's frame carries its liner");
        assertEquals(1, MechCompartment.limit(MechInternal.Role.LINER));
        assertEquals(1, MechCompartment.limit(MechInternal.Role.READY_RACK));
    }

    @Test
    void aSensorPairNeedsADroneHostWithNoBayOfItsOwnOnEitherMachine() {
        assertTrue(VehicleRack.takes(VehicleType.LRM_CARRIER, MechInternal.SENSOR_PAIR));
        assertFalse(VehicleRack.takes(VehicleType.HOVER_SCOUT, MechInternal.SENSOR_PAIR), "it has a sensor pair already");
        assertFalse(VehicleRack.takes(VehicleType.DRONE_SKIMMER, MechInternal.SENSOR_PAIR), "it has a bay");
        for (MechVariant chassis : MechVariant.values()) {
            assertTrue(chassis.hostsDrones() && chassis.hostsPointDefence(), "every chassis hosts both: " + chassis);
        }
        assertNull(MechInternal.SENSOR_PAIR.unmet(new MachineCapabilities(true, false, true, false)));
        assertEquals(MechInternal.Requirement.DRONE_HOST,
                MechInternal.SENSOR_PAIR.unmet(new MachineCapabilities(true, false, false, false)));
        assertEquals(2, MechCompartment.systems(List.of(MechInternal.SENSOR_PAIR)).droneBay().size());
    }

    @Test
    void activeProtectionNeedsAGunAndFlanksForItsLaunchersOnEitherMachine() {
        assertTrue(VehicleRack.takes(VehicleType.BREAKTHROUGH_TANK, MechInternal.ACTIVE_PROTECTION));
        assertTrue(VehicleRack.takes(VehicleType.LASER_TANK, MechInternal.ACTIVE_PROTECTION));
        for (VehicleType other : List.of(VehicleType.DOZER_BREACHER, VehicleType.HEAVY_APC, VehicleType.LRM_CARRIER,
                VehicleType.MILITIA_TECHNICAL, VehicleType.SUPPLY_TRUCK, VehicleType.HOVER_SCOUT)) {
            assertFalse(VehicleRack.takes(other, MechInternal.ACTIVE_PROTECTION), "not a gun tank: " + other);
        }
        assertEquals(MechInternal.Requirement.ARMED,
                MechInternal.ACTIVE_PROTECTION.unmet(new MachineCapabilities(false, false, true, true)));
        assertNull(MechInternal.ACTIVE_PROTECTION.unmet(new MachineCapabilities(true, false, true, true)),
                "an armed mech chassis with launcher flanks takes it");
        MachineSystems aps = MechCompartment.systems(List.of(MechInternal.ACTIVE_PROTECTION));
        assertTrue(aps.activeProtection());
        assertEquals(.94f, aps.speedFactor(), 1e-6f, "the launchers cost speed");
    }

    @Test
    void aHullsCompartmentIsTwoThreeOrFourRowsByWeight() {
        assertEquals(2, VehicleKit.slots(VehicleType.SUPPLY_TRUCK), "a Light hull has two rows");
        assertEquals(3, VehicleKit.slots(VehicleType.HEAVY_APC), "a Medium hull three");
        assertEquals(4, VehicleKit.slots(VehicleType.BREAKTHROUGH_TANK), "a Heavy hull four");
        List<MechInternal> full = new ArrayList<>(VehicleRack.stockFit(VehicleType.FIRE_SUPPORT_APC));
        full.add(MechInternal.SPALL_LINER);
        assertTrue(VehicleRack.fits(VehicleType.FIRE_SUPPORT_APC, List.of(), full));
        full.add(MechInternal.SENSOR_PAIR);
        assertFalse(VehicleRack.fits(VehicleType.FIRE_SUPPORT_APC, List.of(), full), "bank, loader and liner fill it");
    }

    @Test
    void aLinerInAHullsCompartmentGivesItAFifthMoreStructureAsOnAMech() {
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;
        HullLocations bare = HullLocations.of(tank, null);
        HullLocations lined = HullLocations.of(tank, null,
                VehicleKit.Effect.NONE.with(MechCompartment.systems(List.of(MechInternal.SPALL_LINER))));
        assertEquals(bare.maxDrive() * 1.2f, lined.maxDrive(), 1e-3f, "the drive's share of the larger structure");
    }
}
