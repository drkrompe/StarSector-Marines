package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.tender.TenderSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tender as a carrier of the one control session: the same seam a capital uses, with a tender's hands. */
class TenderDirectControlSessionTest {
    private static final int W = 80;
    private static final int H = 80;

    private final NavigationGrid grid = new NavigationGrid(W, H);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(W, H), null);
    private final WarshipSystem warships = new WarshipSystem();
    private final TenderSystem tenders = new TenderSystem();
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "squadOf" -> null;
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> false, () -> false, warships, tenders, (x, y) -> true);
    private Tender tender;

    @BeforeEach
    void fit() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) grid.setWalkableFloor(x, y);
        }
        tender = Tender.fit(Faction.MARINE, 30f, 30f, 0f);
        warships.add(tender.ship());
        tenders.track(tender);
    }

    @Test
    void aTenderHullIsEnteredThroughTheWarshipSeamAndFlownAsATender() {
        assertTrue(session.canEnterWarship(tender.ship()));
        assertTrue(session.enterWarship(tender.ship()));
        assertSame(tender, session.controlledTender());
        assertSame(tender.ship(), session.controlledWarship());
        assertTrue(tender.piloted());
        assertTrue(tender.ship().piloted());
    }

    @Test
    void theLeftButtonLaysASiteAndTheRightButtonTogglesTheDronesOnThePress() {
        session.enterWarship(tender.ship());
        session.submit(new ManualIntent(1f, 0f, 45.5f, 40.5f, true, false, false));
        session.tick();
        assertTrue(tender.ship().pilotUnderWay());
        assertNotNull(tender.current());
        assertEquals(45.5f, tender.current().siteX(), 1e-6f);
        session.submit(new ManualIntent(0f, 0f, 45.5f, 40.5f, false, false, true));
        session.tick();
        assertFalse(tender.defenceOut());
        session.tick();
        assertFalse(tender.defenceOut(), "held, it does not flip back");
        session.submit(new ManualIntent(0f, 0f, 45.5f, 40.5f, false, false, false));
        session.tick();
        session.submit(new ManualIntent(0f, 0f, 45.5f, 40.5f, false, false, true));
        session.tick();
        assertTrue(tender.defenceOut());
    }

    @Test
    void groundTheFogRuleHidesIsNotBuiltOn() {
        session.layableGround((x, y) -> false);
        session.enterWarship(tender.ship());
        session.submit(new ManualIntent(0f, 0f, 45.5f, 40.5f, true, false, false));
        session.tick();
        assertNull(tender.current());
        assertEquals(TenderPilot.Notice.UNSEEN, session.tenderPad().notice());
    }

    @Test
    void theNumberKeysSelectOptionsAndRefuseSlotsNothingFills() {
        session.enterWarship(tender.ship());
        assertTrue(session.canSelectWeapon(0));
        assertEquals(BuildOption.VULCAN_TURRET, session.selectedBuildOption());
        for (int slot = BuildOption.ALL.size(); slot < TenderPilot.OPTION_SLOTS; slot++) {
            assertFalse(session.canSelectWeapon(slot), "slot " + (slot + 1));
            assertFalse(session.selectWeapon(slot));
        }
        assertTrue(session.selectWeapon(4), "key 5 is the interceptor pod");
        assertEquals(BuildOption.POINT_DEFENCE_POD, session.selectedBuildOption());
        assertFalse(session.canSelectWeapon(TenderPilot.OPTION_SLOTS));
        assertTrue(session.selectWeapon(0));
        assertEquals(0, session.selectedWeapon());
        assertTrue(session.active(), "choosing is not a handback");
    }

    @Test
    void theCancelKeyWithdrawsEverySiteAndOnlyATenderHasIt() {
        session.enterWarship(tender.ship());
        session.submit(new ManualIntent(0f, 0f, 45.5f, 40.5f, true, false, false));
        session.tick();
        assertNotNull(tender.current());
        assertTrue(session.canUseAbility(DirectControlAbility.CANCEL_BUILDS));
        assertTrue(session.requestAbility(DirectControlAbility.CANCEL_BUILDS, new PointFireAim(1f, 1f)));
        assertEquals(0, TenderPilot.liveSites(tender));
        assertFalse(session.canUseAbility(DirectControlAbility.SMOKE));
        assertTrue(session.canUseAbility(DirectControlAbility.SHIELD), "the Shepherd has a shield, raised with E");
    }

    @Test
    void theShieldKeyTogglesTheTendersShield() {
        session.enterWarship(tender.ship());
        assertTrue(tender.ship().shieldAuto());
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(1f, 1f)));
        assertFalse(tender.ship().shieldAuto(), "the pilot has taken the shield off its automatic mode");
        assertTrue(session.requestAbility(DirectControlAbility.SHIELD, new PointFireAim(1f, 1f)),
                "the key is a toggle and stays available");
    }

    @Test
    void exitHandsTheShipBackWithNoStalePilotOrders() {
        tender.ship().stationTo(50f, 50f);
        session.enterWarship(tender.ship());
        session.submit(new ManualIntent(1f, 0f, 45.5f, 40.5f, true, false, true));
        session.tick();
        session.exit();
        assertFalse(session.active());
        assertNull(session.controlledTender());
        assertFalse(tender.piloted());
        assertFalse(tender.ship().piloted());
        assertFalse(tender.ship().pilotUnderWay());
        assertEquals(0, TenderPilot.liveSites(tender));
        assertTrue(tender.defenceOut());
        assertEquals(ManualIntent.NEUTRAL, session.intent());
        assertEquals(TenderPilot.Notice.NONE, session.tenderPad().notice());
        assertTrue(session.enterWarship(tender.ship()), "and may be entered again");
    }

    @Test
    void aDestroyedTenderReleasesControlAtValidation() {
        session.enterWarship(tender.ship());
        tender.ship().takeDamage(1.0e9f, 1.0e9f);
        session.validate();
        assertFalse(session.active());
        assertNull(session.controlledTender());
    }
}
