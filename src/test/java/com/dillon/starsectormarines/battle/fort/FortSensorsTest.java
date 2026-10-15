package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.battery.FortressBatteryService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a fort's radar is, asked of {@link FortSensors} directly: whom it watches for, when it stops, how far it reaches
 * and what stands between it and the ground. No battle is played; a fort is assembled from the parts that tag it.
 */
class FortSensorsTest {

    private static final long BATTERY = 1L, BUNKER = 2L, POWER = 3L;

    @BeforeAll
    static void data() {
        InstalledVanillaData.install();
    }

    private static Fort fort(Faction holder, boolean withBunker, boolean withPower) {
        FortressBatteryService batteries = new FortressBatteryService();
        FortressBattery battery = batteries.register(BATTERY,
                TurretCatalogRegistry.requireStructure(TurretCatalogRegistry.FORTRESS_BATTERY_STRUCTURE_ID), 40f, 40f, holder);
        FortService service = new FortService();
        service.tagPlatform(0, battery);
        if (withBunker) service.tagAspect(0, FortAspect.FIRE_CONTROL, BUNKER, holder);
        if (withPower) service.tagAspect(0, FortAspect.POWER, POWER, holder);
        return service.forts().get(0);
    }

    private static void take(FireControl control, Faction taker) {
        for (int i = 0; i < 4000 && control.holder() != taker; i++) control.tick(0.1f, false, taker);
        assertEquals(taker, control.holder());
    }

    @Test
    void theRadarWatchesForWhoeverHoldsTheBunker() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        assertEquals(Faction.DEFENDER, FortSensors.observer(fort));
        assertTrue(FortSensors.observesFor(fort, Faction.DEFENDER));
        assertFalse(FortSensors.observesFor(fort, Faction.MARINE), "a radar does not spot for the side it is pointed at");
    }

    @Test
    void aFortTakenWatchesForTheTaker() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        take(fort.battery().fireControl(), Faction.MARINE);
        assertTrue(FortSensors.observesFor(fort, Faction.MARINE));
        assertFalse(FortSensors.observesFor(fort, Faction.DEFENDER));
    }

    @Test
    void theRadarOutlivesTheGunsAndStillChangesSidesWithTheBunker() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.battery().markDestroyed();
        assertTrue(FortSensors.observesFor(fort, Faction.DEFENDER), "the dish is on the bunker, not the platform");
        take(fort.battery().fireControl(), Faction.MARINE);
        assertTrue(FortSensors.observesFor(fort, Faction.MARINE));
        assertFalse(FortSensors.observesFor(fort, Faction.DEFENDER));
    }

    @Test
    void aContestedBunkerWatchesForNobody() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.battery().fireControl().tick(0.1f, false, Faction.MARINE);
        assertTrue(fort.battery().fireControl().contested());
        assertNull(FortSensors.observer(fort));
        assertEquals(0f, FortSensors.range(fort));
    }

    @Test
    void aWreckedBunkerStopsTheRadarForGood() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        fort.place(FortAspect.FIRE_CONTROL).markWrecked();
        assertNull(FortSensors.observer(fort));
        take(fort.battery().fireControl(), Faction.MARINE);
        assertNull(FortSensors.observer(fort), "taking the platform's control does not raise a wrecked dish");
    }

    @Test
    void aFortWithNoBunkerHasNoRadar() {
        assertNull(FortSensors.observer(fort(Faction.DEFENDER, false, true)));
    }

    @Test
    void withoutThePowerTheDishDoesNotTurnAndTheFortSeesByEye() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        assertEquals(FortSensors.RADAR_RANGE_CELLS, FortSensors.range(fort));
        fort.place(FortAspect.POWER).markWrecked();
        assertEquals(FortSensors.EYE_RANGE_CELLS, FortSensors.range(fort));
        assertTrue(FortSensors.EYE_RANGE_CELLS < FortSensors.RADAR_RANGE_CELLS);
    }

    @Test
    void aTakenBunkerStillGoesDimWhilePowerIsTheOtherSidesToWithhold() {
        Fort fort = fort(Faction.DEFENDER, true, true);
        take(fort.battery().fireControl(), Faction.MARINE);
        assertEquals(FortSensors.EYE_RANGE_CELLS, FortSensors.range(fort), "the plant is still the garrison's");
        take(fort.place(FortAspect.POWER).control(), Faction.MARINE);
        assertEquals(FortSensors.RADAR_RANGE_CELLS, FortSensors.range(fort));
    }

    // ------------------------------------------------------------------------------------------ the line

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(60, 30);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    @Test
    void aNeighboursWallBlocksTheLineButTheFortsOwnWorksDoNot() {
        NavigationGrid grid = openGrid();
        for (int y = 10; y < 20; y++) grid.setWalkable(30, y, false);
        assertFalse(FortSensors.lineOpen(grid, 10, 15, 50, 15, null), "a wall across the line blocks it as for a rifleman");
        assertTrue(FortSensors.lineOpen(grid, 10, 15, 50, 15, (x, y) -> x == 30),
                "the fort looks over its own works, which the mask names");
        assertTrue(FortSensors.lineOpen(grid, 10, 15, 50, 1, null), "a line that clears the wall is open");
    }
}
