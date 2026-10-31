package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.HullReservoir;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleRackTest {

    private static VehicleType unarmed() {
        for (VehicleType type : VehicleType.values()) if (!type.hasTurretWeapon()) return type;
        throw new AssertionError("no unarmed hull");
    }

    @Test
    void aHeavyRackIsTwoBaysAndAFullOneSpillsIntoTheSecond() {
        VehicleType heavy = VehicleType.BREAKTHROUGH_TANK;
        List<VehicleRack.Placement> placed = VehicleRack.pack(heavy, List.of(), List.of(MechInternal.FACTORY_CAPACITOR_BANK,
                MechInternal.SPALL_LINER, MechInternal.SENSOR_PAIR, MechInternal.ACTIVE_PROTECTION));
        assertNotNull(placed);
        assertEquals(SocketId.AUXILIARY, placed.get(0).grid());
        assertTrue(placed.stream().anyMatch(p -> p.grid() == SocketId.AUXILIARY_2));
        assertFalse(VehicleRack.fits(heavy, List.of(), List.of(MechInternal.FACTORY_CAPACITOR_BANK,
                MechInternal.AUXILIARY_VENT_ARRAY, MechInternal.SPALL_LINER, MechInternal.SENSOR_PAIR,
                MechInternal.ACTIVE_PROTECTION)));
    }

    @Test
    void theMilitaryBankFillsOneHeavyBayWhole() {
        VehicleType laser = VehicleType.LASER_TANK;
        List<VehicleRack.Placement> placed = VehicleRack.pack(laser, List.of(), List.of(MechInternal.MILITARY_CAPACITOR_BANK));
        assertNotNull(placed);
        VehicleRack.Placement bank = placed.get(0);
        assertEquals(SocketId.AUXILIARY, bank.grid());
        assertEquals(2, bank.columns());
        assertEquals(2, bank.rows());
        // It cannot go on a Light hull's single 2x2 bay beside another part.
        assertFalse(VehicleRack.fits(VehicleType.MILITIA_TECHNICAL, List.of(),
                List.of(MechInternal.MILITARY_CAPACITOR_BANK, MechInternal.SPALL_LINER)));
    }

    @Test
    void theFrameIsTheBankPlusEveryVentAndNoBankIsNoFrame() {
        HullReservoir.Frame frame = VehicleRack.frame(
                List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.AUXILIARY_VENT_ARRAY));
        assertEquals(MechInternal.FACTORY_CAPACITOR_BANK.fluxCapacity, frame.capacity(), 1e-6f);
        assertEquals(MechInternal.FACTORY_CAPACITOR_BANK.dissipation + MechInternal.AUXILIARY_VENT_ARRAY.dissipation,
                frame.dissipationPerSecond(), 1e-6f);
        assertNull(VehicleRack.frame(List.of(MechInternal.AUXILIARY_VENT_ARRAY)));
    }

    @Test
    void theBattleSideStockFramesAreTheBanksTheyStandFor() {
        assertEquals(MechInternal.FACTORY_CAPACITOR_BANK.fluxCapacity, HullReservoir.FACTORY_BANK.capacity(), 1e-6f);
        assertEquals(MechInternal.FACTORY_CAPACITOR_BANK.dissipation, HullReservoir.FACTORY_BANK.dissipationPerSecond(), 1e-6f);
        assertEquals(MechInternal.MILITARY_CAPACITOR_BANK.fluxCapacity, HullReservoir.MILITARY_BANK.capacity(), 1e-6f);
        assertEquals(MechInternal.MILITARY_CAPACITOR_BANK.dissipation, HullReservoir.MILITARY_BANK.dissipationPerSecond(), 1e-6f);
        assertEquals(MechInternal.DOUBLE_CAPACITOR_BANK.fluxCapacity, HullReservoir.DOUBLE_BANK.capacity(), 1e-6f);
        assertEquals(MechInternal.DOUBLE_CAPACITOR_BANK.dissipation, HullReservoir.DOUBLE_BANK.dissipationPerSecond(), 1e-6f);
        assertEquals(MechInternal.AUXILIARY_VENT_ARRAY.dissipation, HullReservoir.VENT_ARRAY_DISSIPATION, 1e-6f);
    }

    @Test
    void everyArmedHullsStockFitPacksAndIsItsStockFrame() {
        for (VehicleType type : VehicleType.values()) {
            List<MechInternal> fit = VehicleRack.stockFit(type);
            assertNotNull(VehicleRack.pack(type, List.of(), fit), type + "'s stock fit packs");
            assertEquals(HullReservoir.stockFrame(type), VehicleRack.frame(fit), type + "'s stock frame");
        }
    }

    @Test
    void anArmedHullNeedsABankAndAnUnarmedOneTakesNone() {
        assertFalse(VehicleRack.complete(VehicleType.MILITIA_TECHNICAL, List.of()));
        assertTrue(VehicleRack.complete(VehicleType.MILITIA_TECHNICAL, List.of(MechInternal.FACTORY_CAPACITOR_BANK)));
        VehicleType truck = unarmed();
        assertTrue(VehicleRack.complete(truck, List.of()));
        assertNull(VehicleRack.stockBank(truck));
        assertFalse(VehicleRack.takes(truck, MechInternal.FACTORY_CAPACITOR_BANK));
    }

    @Test
    void aBoughtHullComesWithItsBankAndABankSwapReturnsTheOldOneToStores() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        assertTrue(bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank));
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.STANDARD_LOADER), tank.rackInternals());
        assertEquals(0, bay.availableInternal(MechInternal.FACTORY_CAPACITOR_BANK.id));

        bay.addInternal(MechInternal.DOUBLE_CAPACITOR_BANK.id, 1);
        assertTrue(bay.fitRackPart(tank.id(), MechInternal.DOUBLE_CAPACITOR_BANK));
        assertEquals(List.of(MechInternal.STANDARD_LOADER, MechInternal.DOUBLE_CAPACITOR_BANK), tank.rackInternals());
        assertEquals(1, bay.availableInternal(MechInternal.FACTORY_CAPACITOR_BANK.id));
    }

    @Test
    void theLoaderIsACompartmentPartEveryArmedHullIsBuiltWithAndEmptyMeansNone() {
        VehicleType light = VehicleType.MILITIA_TECHNICAL;
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.STANDARD_LOADER), VehicleRack.stockFit(light));
        assertFalse(VehicleRack.fits(light, List.of(),
                        List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.STANDARD_LOADER,
                                MechInternal.AUXILIARY_VENT_ARRAY)),
                "a Light hull is full with bank and loader, as a Hound is");
        assertFalse(VehicleRack.fits(VehicleType.HEAVY_APC, List.of(),
                List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.STANDARD_LOADER,
                        MechInternal.ACCELERATED_LOADER)), "one loader a compartment");
        assertTrue(VehicleRack.takes(unarmed(), MechInternal.ACCELERATED_LOADER), "a loader needs nothing of the hull");
        assertTrue(VehicleRack.stockFit(unarmed()).isEmpty(), "an unarmed hull is built with nothing to feed");
        assertEquals(MissileReplenisherComponent.STANDARD, VehicleRack.loader(VehicleRack.stockFit(light)));
        assertEquals(MissileReplenisherComponent.NONE, VehicleRack.loader(List.of(MechInternal.FACTORY_CAPACITOR_BANK)));

        CampaignVehicle hull = new CampaignVehicle("v1", "Mule", VehicleType.HEAVY_APC);
        assertTrue(hull.canFitRack(MechInternal.ACCELERATED_LOADER), "a loader goes in place of the loader fitted");
        assertTrue(hull.fitRack(MechInternal.ACCELERATED_LOADER));
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.ACCELERATED_LOADER), hull.rackInternals());
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED, hull.freezeForDeployment().loader());
    }

    @Test
    void aVehicleStrippedOfItsBankStaysOffTheDrop() {
        MechBay bay = new MechBay();
        CampaignVehicle tank = new CampaignVehicle("v1", "Breaker", VehicleType.BREAKTHROUGH_TANK);
        bay.addVehicle(MechBay.STARTER_SQUAD_ID, tank);
        bay.selectActiveSquad(MechBay.STARTER_SQUAD_ID);
        assertEquals(1, bay.activeVehicleDeployment().size());
        assertTrue(bay.stripRackPart(tank.id(), MechInternal.FACTORY_CAPACITOR_BANK));
        assertFalse(tank.complete());
        assertEquals(0, bay.activeVehicleDeployment().size());
    }
}
