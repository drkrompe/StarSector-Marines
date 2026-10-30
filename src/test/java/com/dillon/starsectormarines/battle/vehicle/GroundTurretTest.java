package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.mech.MechFlux;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hull gun's loader and flux bank, on a bare turret. */
class GroundTurretTest {

    @Test
    void theLoaderPutsOnePackBackEachCadenceFiringOrNotAndNeverPastTheMagazine() {
        GroundTurret turret = new GroundTurret(15);
        turret.loaderSeconds = 18f;
        turret.ammo = 0;
        turret.advanceLoader(17.9f, 5);
        assertEquals(0, turret.ammo, "nothing until a whole cadence has run");
        turret.advanceLoader(0.1f, 5);
        assertEquals(5, turret.ammo, "one pack, not the whole magazine");
        turret.advanceLoader(36f, 5);
        assertEquals(15, turret.ammo);
        turret.advanceLoader(100f, 5);
        assertEquals(15, turret.ammo, "the magazine is the ceiling");
        assertEquals(0f, turret.loaderProgress, "a full magazine banks no work toward the next pack");

        turret.ammo = 13;
        turret.advanceLoader(18f, 5);
        assertEquals(15, turret.ammo, "a pack tops the magazine up rather than overfilling it");

        GroundTurret unloaded = new GroundTurret(15);
        unloaded.ammo = 0;
        unloaded.advanceLoader(1000f, 5);
        assertEquals(0, unloaded.ammo, "a gun with no loader keeps what it was set down with");
    }

    @Test
    void kitsSetTheLoaderAndTheFluxTheGunOverloadsAt() {
        TurretMountDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount;
        GroundTurret stock = GroundTurret.fitted(lrm, lrm.ammoCapacity, VehicleKit.Effect.NONE, null);
        assertEquals(lrm.loaderSeconds, stock.loaderSeconds);
        assertEquals(lrm.flux.control().capacity(), stock.fluxCapacity(lrm));

        VehicleKit.Effect kits = VehicleKit.Effect.of(List.of(VehicleKit.ACCELERATED_LOADER, VehicleKit.CAPACITOR_PACK));
        GroundTurret fitted = GroundTurret.fitted(lrm, lrm.ammoCapacity, kits, null);
        assertEquals(lrm.loaderSeconds * .75f, fitted.loaderSeconds, 1e-5f);
        assertEquals(lrm.flux.control().capacity() * 1.5f, fitted.fluxCapacity(lrm), 1e-4f);

        int stockRounds = roundsToOverload(stock, lrm);
        int fittedRounds = roundsToOverload(fitted, lrm);
        assertTrue(fittedRounds > stockRounds, "a capacitor pack holds more rounds before it overloads");
    }

    @Test
    void theHullReservoirIsTheHullsFrameAndEveryReaderReadsIt() {
        HullReservoir.Frame stock = HullReservoir.frame(VehicleType.LRM_CARRIER, List.of());
        HullReservoir.Frame packed = HullReservoir.frame(VehicleType.LRM_CARRIER, List.of(VehicleKit.CAPACITOR_PACK));
        assertEquals(stock.capacity() * 1.5f, packed.capacity(), 1e-4f);
        assertEquals(stock.dissipationPerSecond(), packed.dissipationPerSecond());
        assertNull(HullReservoir.frame(VehicleType.SUPPLY_TRUCK, List.of()), "no gun, no reservoir");

        TurretMountDef lrm = VehicleType.LRM_CARRIER.turretStructure().mount;
        GroundTurret turret = new GroundTurret(lrm.ammoCapacity);
        turret.reservoir = new MechFlux(140f, 7f);
        turret.reservoir.commit(20f);
        assertEquals(140f, turret.fluxCapacity(lrm));
        assertEquals(20f, turret.fluxValue());
        assertFalse(turret.fluxVenting());
    }

    @Test
    void everyGunAHullCarriesBuildsFlux() {
        for (VehicleType type : VehicleType.values()) {
            if (!type.hasTurretWeapon()) continue;
            assertTrue(type.turretStructure().mount.flux != null, type + "'s gun builds no flux");
            for (VehicleMount mount : type.secondaryMounts()) {
                assertTrue(mount.structure().mount.flux != null, type + "'s " + mount.structureId() + " builds no flux");
            }
        }
    }

    private static int roundsToOverload(GroundTurret turret, TurretMountDef mount) {
        int rounds = 0;
        while (!turret.overloaded) {
            assertFalse(rounds > 1000);
            GroundVehicleTurretSystem.addFlux(turret, mount);
            rounds++;
        }
        return rounds;
    }
}
