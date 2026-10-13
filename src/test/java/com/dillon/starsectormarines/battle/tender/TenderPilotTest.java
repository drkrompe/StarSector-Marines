package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.control.ShipCommandService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.tender.TenderPilot.Hands;
import com.dillon.starsectormarines.battle.tender.TenderPilot.Notice;
import com.dillon.starsectormarines.battle.tender.TenderPilot.Pad;
import com.dillon.starsectormarines.battle.tender.TenderPilot.Placement;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pilot's hands on a tender: intent to order, eligibility, handback. */
class TenderPilotTest {
    @Test
    void groundedHullOutsideAirRosterKeepsItsGroundedRefusal() throws Exception {
        Tender grounded = Tender.grounded(Faction.MARINE, 20, 20, 22, 20, 0);
        ShipCommandService service = new ShipCommandService();
        service.requestMove(grounded.ship(), 30, 30);
        service.drain(List.of(), List.of(grounded), Faction.MARINE, grid, SEEN);
        assertEquals(ShipCommandService.Notice.GROUNDED, service.notice(grounded.ship()));
        assertEquals(20f, grounded.ship().x);
        service.drain(List.of(), List.of(grounded), Faction.MARINE, grid, SEEN);
        assertEquals(ShipCommandService.Notice.GROUNDED, service.notice(grounded.ship()));
        service.requestBuild(grounded, BuildOption.ALL.get(0), 30, 30);
        service.drain(List.of(), List.of(grounded), Faction.MARINE, grid, SEEN);
        assertEquals(ShipCommandService.Notice.GROUNDED, service.notice(grounded.ship()));
        assertTrue(grounded.orders().isEmpty());
        grounded.loseGroundHost();
        service.requestMove(grounded.ship(), 30, 30);
        service.drain(List.of(), List.of(grounded), Faction.MARINE, grid, SEEN);
        assertEquals(ShipCommandService.Notice.UNAVAILABLE, service.notice(grounded.ship()));
    }

    @Test
    void handbackKeepsSitesAcceptedBeforeThePilotTookOver() {
        tender.releaseHelm();
        ConstructionOrder remote = tender.lay(BuildOption.ALL.get(0), 25, 25, grid);
        assertNotNull(remote);
        tender.takeHelm();
        ConstructionOrder pilot = tender.lay(BuildOption.ALL.get(0), 30, 30, grid);
        assertNotNull(pilot);
        tender.releaseHelm();
        assertFalse(remote.phase().terminal());
        assertTrue(pilot.phase().terminal());
    }

    private static final BiPredicate<Integer, Integer> SEEN = (x, y) -> true;
    private static final BiPredicate<Integer, Integer> DARK = (x, y) -> false;

    private Tender tender;
    private NavigationGrid grid;
    private Pad pad;

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
        pad = new Pad();
        tender.takeHelm();
    }

    private static Hands left(float x, float y) {
        return new Hands(0f, 0f, x, y, true, false);
    }

    private static Hands none() {
        return new Hands(0f, 0f, Float.NaN, Float.NaN, false, false);
    }

    private void apply(Hands hands, BiPredicate<Integer, Integer> revealed) {
        TenderPilot.apply(tender, hands, pad, TenderPilot.option(0), grid, revealed);
    }

    @Test
    void theNumberKeysOfferTheRealOptionListAndNothingMore() {
        assertSame(BuildOption.VULCAN_TURRET, TenderPilot.option(0));
        for (int slot = BuildOption.ALL.size(); slot < TenderPilot.OPTION_SLOTS; slot++) {
            assertNull(TenderPilot.option(slot), "slot " + (slot + 1) + " has no option and says so");
        }
        assertSame(BuildOption.POINT_DEFENCE_POD, TenderPilot.option(4), "key 5");
        assertNull(TenderPilot.option(-1));
        assertNull(TenderPilot.option(TenderPilot.OPTION_SLOTS));
    }

    @Test
    void theLeftButtonLaysTheSelectedOptionAtTheCursorCellOncePerCell() {
        apply(left(30.2f, 31.7f), SEEN);
        assertEquals(1, TenderPilot.liveSites(tender));
        ConstructionOrder site = tender.current();
        assertSame(BuildOption.VULCAN_TURRET, site.option());
        assertEquals(30.5f, site.siteX(), 1e-6f);
        assertEquals(31.5f, site.siteY(), 1e-6f);
        assertEquals(Notice.LAID, pad.notice());
        apply(left(30.9f, 31.1f), SEEN);
        apply(left(30.9f, 31.1f), SEEN);
        assertEquals(1, TenderPilot.liveSites(tender), "holding on one cell lays one site, not one per tick");
        apply(left(35.5f, 31.5f), SEEN);
        assertEquals(2, TenderPilot.liveSites(tender), "dragging to a fresh cell lays the next");
    }

    @Test
    void groundThePlayerHasNotSeenIsNeverBuiltOn() {
        apply(left(30.5f, 30.5f), DARK);
        assertEquals(0, tender.orders().size());
        assertEquals(Notice.UNSEEN, pad.notice());
        assertEquals(Placement.UNSEEN, TenderPilot.placement(tender, 30.5f, 30.5f, grid, DARK));
        assertEquals(Placement.OPEN, TenderPilot.placement(tender, 30.5f, 30.5f, grid, SEEN));
    }

    @Test
    void shutGroundOffMapAndCrowdedCellsAreRefusedAsTheBuildOrderRefusesThem() {
        apply(left(40.5f, 40.5f), SEEN);
        assertEquals(0, tender.orders().size());
        assertEquals(Notice.BLOCKED, pad.notice());
        assertEquals(Placement.BLOCKED, TenderPilot.placement(tender, 40.5f, 40.5f, grid, SEEN));
        assertEquals(Placement.OFF_MAP, TenderPilot.placement(tender, -4f, 10f, grid, SEEN));
        assertEquals(Placement.OFF_MAP, TenderPilot.placement(tender, Float.NaN, 10f, grid, SEEN));
        apply(none(), SEEN);
        apply(left(10.5f, 10.5f), SEEN);
        apply(none(), SEEN);
        apply(left(11.5f, 10.5f), SEEN);
        assertEquals(1, TenderPilot.liveSites(tender), "within the site spacing of the first");
        assertEquals(Placement.BLOCKED, TenderPilot.placement(tender, 11.5f, 10.5f, grid, SEEN));
    }

    @Test
    void aHeldButtonCannotFloodTheLineAndReleasingRearmsTheSameCell() {
        for (int i = 0; i < TenderPilot.MAX_SITES + 3; i++) apply(left(10.5f + 4 * i, 10.5f), SEEN);
        assertEquals(TenderPilot.MAX_SITES, TenderPilot.liveSites(tender));
        assertEquals(Notice.FULL, pad.notice());
        TenderPilot.cancelBuilds(tender);
        assertEquals(0, TenderPilot.liveSites(tender));
        float lastX = 10.5f + 4 * (TenderPilot.MAX_SITES + 2);
        apply(left(lastX, 10.5f), SEEN);
        assertEquals(0, TenderPilot.liveSites(tender), "the cell is still held, so nothing re-lays");
        apply(none(), SEEN);
        apply(left(lastX, 10.5f), SEEN);
        assertEquals(1, TenderPilot.liveSites(tender), "a fresh press may build there again");
    }

    @Test
    void anEmptySlotLaysNothingAndSaysSo() {
        TenderPilot.apply(tender, left(30.5f, 30.5f), pad, TenderPilot.option(TenderPilot.OPTION_SLOTS), grid, SEEN);
        assertEquals(0, tender.orders().size());
        assertEquals(Notice.BLOCKED, pad.notice());
    }

    @Test
    void aMeteredTenderThatCannotPayLaysNothingAndTheNoticeNamesTheCharges() {
        tender.stock(0);
        apply(left(30.5f, 30.5f), SEEN);
        assertEquals(0, tender.orders().size());
        assertEquals(Notice.NO_CHARGES, pad.notice());
    }

    @Test
    void theRightButtonToggleOnThePressNotOnTheHold() {
        assertTrue(tender.defenceOut());
        Hands press = new Hands(0f, 0f, 5f, 5f, false, true);
        apply(press, SEEN);
        assertFalse(tender.defenceOut(), "pressed while deployed: recalled");
        apply(press, SEEN);
        apply(press, SEEN);
        assertFalse(tender.defenceOut(), "holding does not flip it back");
        apply(none(), SEEN);
        apply(press, SEEN);
        assertTrue(tender.defenceOut(), "pressed while docked: deployed");
    }

    @Test
    void theWasdAxesFlyTheHullAndLettingGoStopsTheDemand() {
        apply(new Hands(1f, 0f, Float.NaN, Float.NaN, false, false), SEEN);
        assertTrue(tender.ship().pilotUnderWay());
        apply(none(), SEEN);
        assertFalse(tender.ship().pilotUnderWay());
    }

    @Test
    void noticesFadeAfterAFewSeconds() {
        apply(left(30.5f, 30.5f), SEEN);
        assertEquals(Notice.LAID, pad.notice());
        for (int i = 0; i < TenderPilot.NOTICE_TICKS; i++) apply(none(), SEEN);
        assertEquals(Notice.NONE, pad.notice());
    }

    @Test
    void onlyALiveFriendlyHullThatIsNotLeavingMayBePiloted() throws Exception {
        assertTrue(TenderPilot.eligible(tender, Faction.MARINE));
        assertFalse(TenderPilot.eligible(tender, Faction.DEFENDER), "not the player's");
        assertFalse(TenderPilot.eligible(null, Faction.MARINE));
        Tender hostile = Tender.fit(Faction.DEFENDER, 10f, 10f, 0f);
        assertFalse(TenderPilot.eligible(hostile, Faction.MARINE));
        tender.ship().takeDamage(1.0e9f, 1.0e9f);
        assertFalse(TenderPilot.eligible(tender, Faction.MARINE), "a wreck");
    }

    @Test
    void handingTheHelmBackDropsEverySiteAndRestoresTheDrones() {
        tender.releaseHelm();
        assertFalse(tender.piloted());
        tender.takeHelm();
        assertTrue(tender.piloted());
        assertTrue(tender.defenceOut());
        apply(left(30.5f, 30.5f), SEEN);
        apply(new Hands(0f, 0f, 5f, 5f, false, true), SEEN);
        assertFalse(tender.defenceOut());
        assertNotNull(tender.current());
        tender.releaseHelm();
        assertFalse(tender.piloted());
        assertEquals(0, TenderPilot.liveSites(tender), "a hand order does not outlive the hand");
        assertTrue(tender.defenceOut(), "the drones are as the pilot found them");
        assertFalse(tender.ship().piloted());
        assertFalse(tender.ship().pilotUnderWay());
    }

    @Test
    void aTenderPilotedFarFromItsSiteIsOutOfReachOfItsBuilders() {
        ConstructionOrder site = tender.lay(BuildOption.VULCAN_TURRET, 50.5f, 50.5f, grid);
        tender.ship().x = 50f - TenderSystem.PILOT_LEASH_CELLS + 3f;
        tender.ship().y = 50f;
        assertFalse(TenderSystem.outOfReach(tender, site));
        tender.ship().x = 50f - TenderSystem.PILOT_LEASH_CELLS - 3f;
        assertTrue(TenderSystem.outOfReach(tender, site));
        tender.releaseHelm();
        assertFalse(TenderSystem.outOfReach(tender, site), "an unpiloted tender keeps its own station");
    }
}
