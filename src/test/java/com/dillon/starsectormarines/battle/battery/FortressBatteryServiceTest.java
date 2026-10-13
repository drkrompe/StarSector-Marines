package com.dillon.starsectormarines.battle.battery;

import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.VanillaWeapon;
import com.dillon.starsectormarines.battle.warship.WarshipScale;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What registering a battery records, and when the fleet is held at standoff by it. */
class FortressBatteryServiceTest {

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static StructureDef battery() {
        return TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID);
    }

    @Test
    void theCatalogBatteryNamesAShipGradeLoadoutAndAFireControlRadius() {
        StructureDef def = battery();
        assertTrue(def.isBattery());
        assertNotNull(BatteryLoadout.byId(def.batteryLoadoutId));
        assertTrue(def.fireControlRadius > def.footprintCellsX / 2f, "the point is reachable from outside the footprint");
        assertFalse(TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.VULCAN_STRUCTURE_ID).isBattery());
    }

    @Test
    void loadoutsAreStationModuleGunsAtTheGamesOwnFigures() {
        assertEquals(3, BatteryLoadout.CITADEL.guns().size());
        assertEquals(2, BatteryLoadout.HELLBORE_PAIR.guns().size());
        for (VanillaWeapon w : BatteryLoadout.CITADEL.guns()) {
            assertTrue(w.damagePerShot() >= 200f, w.id() + " is a large gun");
            assertTrue(WarshipScale.cells(w.rangePx()) > 30f, w.id() + " reaches across a battlefield's width");
        }
    }

    @Test
    void theEnvelopeIsTheLongestGunsReach() {
        FortressBatteryService service = new FortressBatteryService();
        FortressBattery b = service.register(1L, battery(), 50f, 50f, Faction.DEFENDER);
        float longest = 0f;
        for (FortressBattery.Gun gun : b.guns()) longest = Math.max(longest, WarshipScale.cells(gun.weapon.rangePx()));
        assertEquals(longest, b.envelopeCells(), 1e-4f);
        assertTrue(b.envelopeCells() > 0f);
    }

    @Test
    void aBatteryHostileToTheCompanyWithholdsTheBarrageUntilTakenOrDestroyed() {
        FortressBatteryService service = new FortressBatteryService();
        FortressBattery b = service.register(1L, battery(), 50f, 50f, Faction.DEFENDER);
        assertEquals(FortressBatteryService.LOCK_REASON, service.orbitalBarrageLock(Faction.MARINE));

        // Contested still withholds: nobody has been rid of it yet.
        b.fireControl().tick(1f, false, Faction.MARINE);
        assertEquals(FortressBatteryService.LOCK_REASON, service.orbitalBarrageLock(Faction.MARINE));

        // Taken: it is the company's battery now.
        for (int i = 0; i < 20; i++) b.fireControl().tick(1f, false, Faction.MARINE);
        assertEquals(Faction.MARINE, b.owner());
        assertNull(service.orbitalBarrageLock(Faction.MARINE));
        // And it is now what holds the other side's fleet off.
        assertEquals(FortressBatteryService.LOCK_REASON, service.orbitalBarrageLock(Faction.DEFENDER));
    }

    @Test
    void destroyingTheBatteryLiftsTheLockForGood() {
        FortressBatteryService service = new FortressBatteryService();
        FortressBattery b = service.register(1L, battery(), 50f, 50f, Faction.DEFENDER);
        b.markDestroyed();
        assertNull(service.orbitalBarrageLock(Faction.MARINE));
        assertFalse(b.operational());
    }

    @Test
    void aBatteryOfTheCompanysOwnSideHoldsNobodyOff() {
        FortressBatteryService service = new FortressBatteryService();
        service.register(1L, battery(), 50f, 50f, Faction.MARINE);
        assertNull(service.orbitalBarrageLock(Faction.MARINE));
    }
}
