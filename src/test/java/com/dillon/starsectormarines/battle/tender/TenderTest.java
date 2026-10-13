package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenderTest {

    private Tender tender;
    private NavigationGrid grid;

    @BeforeEach
    void fit() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        tender = Tender.fit(Faction.MARINE, 20f, 20f, 0f);
        grid = new NavigationGrid(60, 60);
        for (int x = 0; x < 60; x++) {
            for (int y = 0; y < 60; y++) grid.setWalkableFloor(x, y);
        }
        grid.setTag(40, 40, CellTag.WALKABLE, false);
    }

    @Test
    void yardCommandTargetsAndQueuesTheExistingGroundedSource() throws Exception {
        Tender yard = Tender.grounded(Faction.MARINE, 20f, 20f, 25.5f, 20.5f, -90f);
        BattleControl view = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                new Class<?>[]{BattleControl.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getGrid")) return grid;
                    throw new UnsupportedOperationException(method.getName());
                });
        YardConstruction power = new YardConstruction(yard);
        assertFalse(power.canTarget(20, 20, view));
        assertTrue(power.canTarget(32, 20, view));
        power.resolve(32, 20, new CommandPowerService(), view);
        assertEquals(BuildOption.VULCAN_TURRET, yard.current().option());
        assertFalse(power.canTarget(32, 20, view));
        assertEquals(3, yard.builderReserve());
        assertEquals(0f, power.cpCost);
    }

    @Test
    void groundedCompletedPlotStaysReservedAfterOrderSweep() throws Exception {
        Tender yard = Tender.grounded(Faction.MARINE, 20f, 20f, 25.5f, 20.5f, -90f);
        ConstructionOrder order = yard.lay(BuildOption.VULCAN_TURRET, 32.5f, 20.5f, grid);
        order.begin();
        order.startWork();
        assertTrue(order.work(100f, 3, 3, false));
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 32.5f, 20.5f, grid));
        yard.sweep();
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 32.5f, 20.5f, grid));
    }

    @Test
    void lowHoverBuildReturnsMissionTravelAfterCompletionOrCancellation() throws Exception {
        Tender hover = Tender.lowHover(Faction.MARINE, 20f, 20f, 8f, 8f);
        for (boolean cancel : new boolean[]{false, true}) {
            ConstructionOrder order = hover.lay(BuildOption.VULCAN_TURRET, cancel ? 30.5f : 28.5f,
                    cancel ? 29.5f : 20.5f, grid);
            assertNotNull(order);
            hover.ship().remoteOrder(Warship.RemoteOrder.BUILD, 20f, 20f);
            TenderSystem.releaseCompletedBuildHelm(hover);
            assertEquals(Warship.RemoteOrder.BUILD, hover.ship().remoteOrder());
            if (cancel) hover.cancelAll();
            else { order.begin(); order.startWork(); assertTrue(order.work(100f, 3, 3, false)); }
            hover.sweep();
            TenderSystem.releaseCompletedBuildHelm(hover);
            assertEquals(Warship.RemoteOrder.NONE, hover.ship().remoteOrder());
        }
        for (Warship.RemoteOrder override : new Warship.RemoteOrder[]{Warship.RemoteOrder.HOLD, Warship.RemoteOrder.MOVE}) {
            hover.ship().remoteOrder(override, 22f, 22f);
            TenderSystem.releaseCompletedBuildHelm(hover);
            assertEquals(override, hover.ship().remoteOrder());
        }
    }

    @Test
    void groundedConstructionUsesExteriorBayAndRefusesHullCrossingsAndDistantSites() throws Exception {
        Tender yard = Tender.grounded(Faction.MARINE, 20f, 20f, 25.5f, 20.5f, -90f);
        assertEquals(25.5f, yard.bayX());
        assertEquals(20.5f, yard.bayY());
        assertFalse(yard.defenceOut());
        assertNotNull(yard.lay(BuildOption.VULCAN_TURRET, 32.5f, 20.5f, grid));
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 20.5f, 20.5f, grid));
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 45.5f, 20.5f, grid));
        grid.setTag(29, 20, CellTag.WALKABLE, false);
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 35.5f, 20.5f, grid));
        yard.takeHelm();
        assertFalse(yard.piloted());
        yard.loseGroundHost();
        assertFalse(yard.anchorAlive());
        assertNull(yard.lay(BuildOption.VULCAN_TURRET, 32.5f, 24.5f, grid));
    }

    @Test
    void theComplementIsTheHullsBuiltInWingSplitBetweenBuildAndDefence() {
        assertEquals(6, Tender.complement());
        assertEquals(3, tender.builderComplement());
        assertEquals(3, tender.defenceComplement());
        assertEquals(tender.builderComplement(), tender.builderReserve());
        assertEquals(tender.defenceComplement(), tender.defenceReserve());
    }

    @Test
    void theHullIsTheShepherdWithNoGunsOfItsOwn() {
        assertEquals("shepherd", tender.ship().hullId);
        assertTrue(tender.ship().mounts().isEmpty());
        assertNotEquals(0f, tender.ship().widthCells);
    }

    @Test
    void dronesLeaveThroughTheHullsLaunchBayNotItsCentre() {
        float offset = (float) Math.hypot(tender.bayX() - tender.ship().x, tender.bayY() - tender.ship().y);
        assertTrue(offset > 0.1f, "the bay is off the centre of gravity, was " + offset);
        assertTrue(offset < tender.ship().lengthCells, "the bay is on the hull");
    }

    @Test
    void theAnchorFollowsTheHullAndDiesWithIt() {
        assertEquals(20f, tender.anchorX(), 1e-6f);
        tender.ship().x = 33f;
        assertEquals(33f, tender.anchorX(), 1e-6f);
        assertTrue(tender.anchorAlive());
        tender.ship().takeDamage(1.0e9f, 1.0e9f);
        assertTrue(tender.ship().destroyed());
        assertFalse(tender.anchorAlive());
    }

    @Test
    void buildingIsFreeByDefaultSoNoNumberOfSitesRunsOut() {
        assertFalse(tender.metered());
        assertEquals(Tender.UNLIMITED, tender.stock());
        for (int i = 0; i < 12; i++) {
            ConstructionOrder order = tender.lay(BuildOption.VULCAN_TURRET, 5.5f + 4 * (i % 6), 5.5f + 4 * (i / 6), grid);
            assertNotNull(order, "site " + i);
        }
        assertEquals(Tender.UNLIMITED, tender.stock(), "an unmetered stock is never drawn down");
        assertFalse(tender.statusLine().contains("charges"), tender.statusLine());
        assertTrue(tender.statusLine().contains("builds free"), tender.statusLine());
    }

    @Test
    void layingASiteOnAMeteredTenderDrawsAChargeAndCancellingGivesItBack() {
        tender.stock(3);
        assertTrue(tender.metered());
        ConstructionOrder order = tender.lay(BuildOption.VULCAN_TURRET, 30.2f, 31.7f, grid);
        assertNotNull(order);
        assertEquals(30.5f, order.siteX(), 1e-6f, "a site is the centre of its cell");
        assertEquals(31.5f, order.siteY(), 1e-6f);
        assertEquals(2, tender.stock());
        assertTrue(tender.statusLine().contains("charges 2"), tender.statusLine());
        tender.cancel(order);
        assertEquals(3, tender.stock());
        tender.cancel(order);
        assertEquals(3, tender.stock(), "a second cancel gives nothing back");
    }

    @Test
    void aMeteredStockThatCannotPayRefusesTheSite() {
        tender.stock(0);
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 30f, grid));
        tender.stock(Tender.UNLIMITED);
        assertNotNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 30f, grid), "lifting the cap lifts the refusal");
    }

    @Test
    void aSiteIsRefusedWhenTheGroundIsShutOrASiteIsAlreadyThere() {
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, -3f, 30f, grid), "off the map");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 99f, grid), "off the map");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 40.5f, 40.5f, grid), "shut ground");
        assertNotNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 30f, grid));
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30.9f, 30.9f, grid), "the same cell");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 31.5f, 30.5f, grid), "closer than the spacing");
        assertNotNull(tender.lay(BuildOption.VULCAN_TURRET, 33.5f, 30.5f, grid));
    }

    @Test
    void aRetiredOrderIsSweptOffTheLineAndAMeteredChargeComesBack() {
        tender.stock(3);
        ConstructionOrder a = tender.lay(BuildOption.VULCAN_TURRET, 10f, 10f, grid);
        ConstructionOrder b = tender.lay(BuildOption.VULCAN_TURRET, 20f, 10f, grid);
        assertEquals(a, tender.current());
        tender.retire(a, "blocked");
        tender.sweep();
        assertEquals(b, tender.current());
        assertEquals(0, tender.built());
        assertEquals(2, tender.stock(), "the failed order's charge came back");
    }

    @Test
    void theShepherdKeepsItsOwnShieldHitPointsAndArmourAndIsOnFlux() {
        Warship ship = tender.ship();
        assertTrue(ship.hasShield(), "the vanilla Shepherd has a shield");
        assertEquals("omni", ship.shieldType());
        assertEquals(90f, ship.shieldArcDegrees(), 1e-3f, "the hull table's arc");
        assertEquals(1200f, ship.maxHitPoints, 1e-3f);
        assertEquals(200f, ship.armour, 1e-3f);
        assertTrue(ship.shieldAuto(), "the shield raises itself when threatened");
        assertTrue(ship.mounts().isEmpty());
    }

    @Test
    void aFleetMemberShepherdFitsAsATenderAndAnythingElseFallsBackToTheStockHull() throws Exception {
        Tender fitted = Tender.fit(member("shepherd", "shepherd"), Faction.MARINE, 10f, 10f, 0f);
        assertEquals("shepherd", fitted.ship().hullId);
        Tender skin = Tender.fit(member("shepherd_skin", "shepherd"), Faction.MARINE, 10f, 10f, 0f);
        assertEquals("shepherd", skin.ship().hullId, "a skin fits on its base hull");
        Tender other = Tender.fit(member("buffalo", "buffalo"), Faction.MARINE, 10f, 10f, 0f);
        assertEquals("shepherd", other.ship().hullId, "a hull that is not a Shepherd is not a tender");
        Tender none = Tender.fit((FleetMemberAPI) null, Faction.MARINE, 10f, 10f, 0f);
        assertEquals("shepherd", none.ship().hullId);
        assertTrue(fitted.ship().mounts().isEmpty());
    }

    private static FleetMemberAPI member(String hullId, String baseHullId) {
        ShipHullSpecAPI spec = (ShipHullSpecAPI) Proxy.newProxyInstance(
                TenderTest.class.getClassLoader(), new Class<?>[] {ShipHullSpecAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullId" -> hullId;
                    case "getBaseHullId" -> baseHullId;
                    case "getBuiltInWeapons" -> new HashMap<String, String>();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        ShipVariantAPI variant = (ShipVariantAPI) Proxy.newProxyInstance(
                TenderTest.class.getClassLoader(), new Class<?>[] {ShipVariantAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHullSpec" -> spec;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (FleetMemberAPI) Proxy.newProxyInstance(
                TenderTest.class.getClassLoader(), new Class<?>[] {FleetMemberAPI.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getVariant")) return variant;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
