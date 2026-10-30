package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
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

    private static List<VehicleKit> suiting(VehicleType type, int count) {
        List<VehicleKit> kits = new ArrayList<>();
        for (VehicleKit kit : VehicleKit.values()) if (kits.size() < count && kit.suits(type)) kits.add(kit);
        assertEquals(count, kits.size(), "test needs " + count + " kits suiting " + type);
        return kits;
    }

    private static VehicleType unarmed() {
        for (VehicleType type : VehicleType.values()) if (!type.hasTurretWeapon()) return type;
        throw new AssertionError("no unarmed hull");
    }

    @Test
    void aHeavyRackIsTwoBaysAndAFullOneSpillsIntoTheSecond() {
        VehicleType heavy = VehicleType.BREAKTHROUGH_TANK;
        List<VehicleKit> kits = suiting(heavy, 3);
        List<VehicleRack.Placement> placed = VehicleRack.pack(heavy, kits, List.of(MechInternal.FACTORY_CAPACITOR_BANK));
        assertNotNull(placed);
        assertEquals(SocketId.AUXILIARY, placed.get(0).grid());
        assertTrue(placed.stream().anyMatch(p -> p.grid() == SocketId.AUXILIARY_2));
        assertFalse(VehicleRack.fits(heavy, suiting(heavy, 3),
                List.of(MechInternal.FACTORY_CAPACITOR_BANK, MechInternal.AUXILIARY_VENT_ARRAY)));
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
        // It cannot go on a Light hull's single 2x2 bay beside a kit.
        assertFalse(VehicleRack.fits(VehicleType.MILITIA_TECHNICAL, suiting(VehicleType.MILITIA_TECHNICAL, 1),
                List.of(MechInternal.MILITARY_CAPACITOR_BANK)));
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
        assertEquals(List.of(MechInternal.FACTORY_CAPACITOR_BANK), tank.rackInternals());
        assertEquals(0, bay.availableInternal(MechInternal.FACTORY_CAPACITOR_BANK.id));

        bay.addInternal(MechInternal.DOUBLE_CAPACITOR_BANK.id, 1);
        assertTrue(bay.fitRackPart(tank.id(), MechInternal.DOUBLE_CAPACITOR_BANK));
        assertEquals(List.of(MechInternal.DOUBLE_CAPACITOR_BANK), tank.rackInternals());
        assertEquals(1, bay.availableInternal(MechInternal.FACTORY_CAPACITOR_BANK.id));
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
