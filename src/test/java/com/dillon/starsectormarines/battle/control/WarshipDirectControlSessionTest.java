package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.PilotShipFixture;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipSystem;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The warship as a carrier of the one control session: entry, intent, the number keys, and handback. */
class WarshipDirectControlSessionTest {
    private static final int W = 200;
    private static final int H = 120;

    private final NavigationGrid grid = new NavigationGrid(W, H);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(W, H), null);
    private final WarshipSystem warships = new WarshipSystem();
    private boolean complete;
    private final BattleControl battle = (BattleControl) Proxy.newProxyInstance(
            BattleControl.class.getClassLoader(), new Class<?>[]{BattleControl.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getGrid" -> grid;
                case "squadOf" -> null;
                default -> throw new AssertionError("Unexpected battle call " + method.getName());
            });
    private final DirectControlSession session = new DirectControlSession(battle, roster,
            id -> false, () -> complete, warships, (x, y) -> true);
    private Warship ship;

    @BeforeEach
    void fit() throws Exception {
        PilotShipFixture.install();
        ship = PilotShipFixture.ship(100f, 60f);
        warships.add(ship);
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    @Test
    void aFriendlyHullOverTheBattleMayBeEnteredAndOnlyOneCarrierAtATime() throws Exception {
        assertTrue(session.canEnterWarship(ship));
        assertTrue(session.enterWarship(ship));
        assertTrue(session.active());
        assertTrue(session.warshipControl());
        assertSame(ship, session.controlledWarship());
        assertTrue(ship.piloted());
        assertEquals(0L, session.activeUnitId(), "a hull has no entity identity");
        EntitySpec spec = new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, 3, 5);
        spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID));
        long marine = roster.spawn(spec);
        assertFalse(session.enter(marine), "a body cannot be entered while the helm is held");
        Warship other = PilotShipFixture.ship(50f, 50f);
        warships.add(other);
        assertFalse(session.enterWarship(other));
    }

    @Test
    void anEnemyHullAHullNotOverTheBattleAndAFinishedBattleRefuse() throws Exception {
        Warship stranger = PilotShipFixture.ship(10f, 10f);
        assertFalse(session.canEnterWarship(stranger), "not one of the battle's ships");
        Warship hostile = Warship.fit("test", "test_Standard", Faction.DEFENDER, 10f, 10f, 0f);
        warships.add(hostile);
        assertFalse(session.canEnterWarship(hostile));
        complete = true;
        assertFalse(session.canEnterWarship(ship));
    }

    @Test
    void theTickMapsOneIntentToFlightAndToLaidGround() {
        session.enterWarship(ship);
        session.submit(new ManualIntent(1f, 0f, 40f, 30f, true, false, false));
        session.tick();
        assertTrue(ship.pilotUnderWay());
        assertNotNull(ship.laidArea(Warship.FireGroup.GUNS));
        assertNull(ship.laidArea(Warship.FireGroup.MISSILES));
        session.submit(new ManualIntent(0f, 0f, 150f, 90f, false, false, true));
        session.tick();
        assertFalse(ship.pilotUnderWay());
        assertEquals(150f, ship.laidArea(Warship.FireGroup.MISSILES).x());
        assertEquals(40f, ship.laidArea(Warship.FireGroup.GUNS).x(), "the gun area stands when the left button is let go");
    }

    @Test
    void groundTheRuleRefusesIsNotLaid() {
        session.layableGround((x, y) -> false);
        session.enterWarship(ship);
        session.submit(new ManualIntent(0f, 0f, 40f, 30f, true, false, true));
        session.tick();
        assertTrue(ship.areas().isEmpty());
    }

    @Test
    void anIntentWithNoCursorHasNoTriggers() {
        ManualIntent blind = new ManualIntent(0f, 0f, Float.NaN, Float.NaN, true, false, true);
        assertFalse(blind.firing());
        assertFalse(blind.secondary());
    }

    @Test
    void suspendingInputStopsTheDriveDemandWithoutDroppingGround() {
        session.enterWarship(ship);
        session.submit(new ManualIntent(1f, 1f, 40f, 30f, true));
        session.tick();
        assertTrue(ship.pilotUnderWay());
        session.suspendInput();
        assertFalse(ship.pilotUnderWay());
        assertFalse(session.intent().firing());
        assertNotNull(ship.laidArea(Warship.FireGroup.GUNS), "ground already laid is an order, not a held key");
    }

    @Test
    void theNumberKeysAreHoldFireDirectorPresetAndPolicy() {
        session.enterWarship(ship);
        session.submit(new ManualIntent(0f, 0f, 40f, 30f, true));
        session.tick();
        var preset = ship.missilePreset();
        assertTrue(session.canSelectWeapon(3));
        assertFalse(session.canSelectWeapon(4));

        assertTrue(session.selectWeapon(1));
        assertTrue(ship.isDirected());
        assertTrue(session.selectWeapon(2));
        assertEquals(preset.next(), ship.missilePreset());
        assertTrue(session.selectWeapon(3));
        assertEquals(Warship.GroundFire.HEAVY, ship.groundFire());
        assertTrue(session.selectWeapon(0));
        assertTrue(ship.areas().isEmpty());
        assertFalse(ship.isDirected());
        assertTrue(session.active(), "hold fire is not a handback");
    }

    @Test
    void theShieldKeyIsRefusedByAHullWithoutOne() {
        session.enterWarship(ship);
        assertFalse(session.canUseAbility(DirectControlAbility.SHIELD));
        assertFalse(session.requestAbility(DirectControlAbility.SHIELD,
                new PointFireAim(1f, 1f)));
        assertFalse(session.canUseAbility(DirectControlAbility.SMOKE));
    }

    @Test
    void exitGivesTheShipBackToItselfWithNoStaleOrder() {
        ship.stationTo(120f, 70f);
        ship.direct(8f, true);
        session.enterWarship(ship);
        session.submit(new ManualIntent(1f, 0f, 40f, 30f, true, false, true));
        session.tick();
        session.exit();
        assertFalse(session.active());
        assertFalse(ship.piloted());
        assertTrue(ship.isDirected());
        assertTrue(ship.areas().isEmpty());
        assertFalse(ship.pilotUnderWay());
        assertEquals(ManualIntent.NEUTRAL, session.intent());
        assertTrue(session.enterWarship(ship), "and may be entered again");
    }

    @Test
    void aDestroyedOrDepartingHullReleasesControlAtValidation() throws Exception {
        session.enterWarship(ship);
        ship.takeDamage(1e9f, 1e6f);
        session.validate();
        assertFalse(session.active());

        Warship second = PilotShipFixture.ship(30f, 30f);
        warships.add(second);
        assertTrue(session.enterWarship(second));
        second.departForward(W, H);
        session.validate();
        assertFalse(session.active());
    }
}
