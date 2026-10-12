package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void layingASiteDrawsAChargeAndCancellingGivesItBack() {
        ConstructionOrder order = tender.lay(BuildOption.VULCAN_TURRET, 30.2f, 31.7f, grid);
        assertNotNull(order);
        assertEquals(30.5f, order.siteX(), 1e-6f, "a site is the centre of its cell");
        assertEquals(31.5f, order.siteY(), 1e-6f);
        assertEquals(Tender.DEFAULT_STOCK - 1, tender.stock());
        tender.cancel(order);
        assertEquals(Tender.DEFAULT_STOCK, tender.stock());
        tender.cancel(order);
        assertEquals(Tender.DEFAULT_STOCK, tender.stock(), "a second cancel gives nothing back");
    }

    @Test
    void aSiteIsRefusedWhenTheStockCannotPayTheGroundIsShutOrASiteIsAlreadyThere() {
        tender.stock(0);
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 30f, grid));
        tender.stock(2);
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, -3f, 30f, grid), "off the map");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 99f, grid), "off the map");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 40.5f, 40.5f, grid), "shut ground");
        assertNotNull(tender.lay(BuildOption.VULCAN_TURRET, 30f, 30f, grid));
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 30.9f, 30.9f, grid), "the same cell");
        assertNull(tender.lay(BuildOption.VULCAN_TURRET, 31.5f, 30.5f, grid), "closer than the spacing");
        assertNotNull(tender.lay(BuildOption.VULCAN_TURRET, 33.5f, 30.5f, grid));
        assertEquals(0, tender.stock());
    }

    @Test
    void aRetiredOrderIsSweptOffTheLineAndItsChargeComesBack() {
        ConstructionOrder a = tender.lay(BuildOption.VULCAN_TURRET, 10f, 10f, grid);
        ConstructionOrder b = tender.lay(BuildOption.VULCAN_TURRET, 20f, 10f, grid);
        assertEquals(a, tender.current());
        tender.retire(a, "blocked");
        tender.sweep();
        assertEquals(b, tender.current());
        assertEquals(0, tender.built());
        assertEquals(Tender.DEFAULT_STOCK - 1, tender.stock(), "the failed order's charge came back");
    }
}
