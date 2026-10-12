package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the pilot's hands become the ship's own orders, and what is given back at handback. */
class WarshipPilotTest {

    private static final int W = 200;
    private static final int H = 120;
    private static final float DT = 1f / 30f;

    @BeforeEach
    void install() {
        PilotShipFixture.install();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    private static Warship.Mount mount(Warship ship, Warship.FireGroup group, boolean pointDefence) {
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence() == pointDefence && (pointDefence || m.group() == group)) return m;
        }
        throw new AssertionError("no such mount");
    }

    private static WarshipPilot.Hands hands(float aimX, float aimY, boolean guns, boolean missiles) {
        return new WarshipPilot.Hands(0f, 0f, aimX, aimY, guns, missiles);
    }

    @Test
    void theGunTriggerLaysTheGunAreaAndTheMissileTriggerTheMissileArea() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        WarshipPilot.apply(ship, hands(40f, 30f, true, false), W, H, (x, y) -> true);
        assertNotNull(ship.laidArea(Warship.FireGroup.GUNS));
        assertNull(ship.laidArea(Warship.FireGroup.MISSILES));
        WarshipOrder.FireOnArea guns = ship.laidArea(Warship.FireGroup.GUNS);
        assertEquals(40f, guns.x());
        assertEquals(30f, guns.y());
        assertEquals(WarshipPilot.AREA_RADIUS_CELLS, guns.radius());
        assertTrue(guns.bringGunsToBear());

        WarshipPilot.apply(ship, hands(150f, 90f, false, true), W, H, (x, y) -> true);
        assertEquals(150f, ship.laidArea(Warship.FireGroup.MISSILES).x());
        assertEquals(40f, ship.laidArea(Warship.FireGroup.GUNS).x(), "the other battery's ground stands");
        assertEquals(2, ship.areas().size());
    }

    @Test
    void groundStaysLaidWhenTheTriggerIsLetGoAndFollowsTheCursorWhileHeld() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        WarshipPilot.apply(ship, hands(40f, 30f, true, false), W, H, (x, y) -> true);
        WarshipPilot.apply(ship, hands(45f, 33f, true, false), W, H, (x, y) -> true);
        assertEquals(45f, ship.laidArea(Warship.FireGroup.GUNS).x(), "held, it walks with the cursor");
        WarshipPilot.apply(ship, hands(90f, 90f, false, false), W, H, (x, y) -> true);
        assertEquals(45f, ship.laidArea(Warship.FireGroup.GUNS).x(), "let go, it stays where it was laid");
    }

    @Test
    void groundThePlayerHasNotSeenOrOffTheMapIsNeverLaid() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        WarshipPilot.apply(ship, hands(40f, 30f, true, true), W, H, (x, y) -> false);
        assertTrue(ship.areas().isEmpty(), "fog is not scouted by shelling it");
        WarshipPilot.apply(ship, hands(-3f, 30f, true, true), W, H, (x, y) -> true);
        WarshipPilot.apply(ship, hands(40f, H + 1f, true, true), W, H, (x, y) -> true);
        WarshipPilot.apply(ship, hands(Float.NaN, Float.NaN, true, true), W, H, (x, y) -> true);
        assertTrue(ship.areas().isEmpty());
    }

    @Test
    void eachBatteryIsDealtItsOwnLaidAreaAndTheRestKeepTheShipsOwn() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        Warship.Mount gun = mount(ship, Warship.FireGroup.GUNS, false);
        Warship.Mount missile = mount(ship, Warship.FireGroup.MISSILES, false);
        ship.order(new WarshipOrder.FireOnArea(10f, 10f, 5f, false));
        ship.takeHelm();
        ship.order(new WarshipOrder.FireOnArea(10f, 10f, 5f, false));
        WarshipPilot.apply(ship, hands(40f, 30f, true, false), W, H, (x, y) -> true);
        assertEquals(List.of(ship.laidArea(Warship.FireGroup.GUNS)), ship.areasFor(gun));
        assertEquals(10f, ship.areasFor(missile).get(0).x(), "a battery with nothing laid keeps the ship's ground");
        assertTrue(ship.holdsGround(gun, 40f, 30f));
        assertFalse(ship.holdsGround(gun, 10f, 10f), "a laid area stands in for the held one for its own battery");
        assertTrue(ship.holdsGround(missile, 10f, 10f));
    }

    @Test
    void holdFireTakesBackEveryAreaAndTheDirector() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.pilotDirector(true);
        WarshipPilot.apply(ship, hands(40f, 30f, true, true), W, H, (x, y) -> true);
        assertTrue(WarshipPilot.command(ship, WarshipPilot.Command.HOLD_FIRE));
        assertTrue(ship.areas().isEmpty());
        assertFalse(ship.isDirected());
    }

    @Test
    void theDirectorKeyTogglesTheShipsOwnTargeting() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        assertFalse(ship.isDirected());
        WarshipPilot.command(ship, WarshipPilot.Command.TOGGLE_DIRECTOR);
        assertTrue(ship.isDirected());
        WarshipPilot.command(ship, WarshipPilot.Command.TOGGLE_DIRECTOR);
        assertFalse(ship.isDirected());
    }

    @Test
    void presetAndGroundFirePolicyStepThroughTheirValues() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        MissilePreset first = ship.missilePreset();
        WarshipPilot.command(ship, WarshipPilot.Command.CYCLE_MISSILE_PRESET);
        assertEquals(first.next(), ship.missilePreset());
        assertEquals(Warship.GroundFire.ALL, ship.groundFire());
        WarshipPilot.command(ship, WarshipPilot.Command.CYCLE_GROUND_FIRE);
        assertEquals(Warship.GroundFire.HEAVY, ship.groundFire());
        WarshipPilot.command(ship, WarshipPilot.Command.CYCLE_GROUND_FIRE);
        assertEquals(Warship.GroundFire.HOLD, ship.groundFire());
        WarshipPilot.command(ship, WarshipPilot.Command.CYCLE_GROUND_FIRE);
        assertEquals(Warship.GroundFire.ALL, ship.groundFire());
    }

    @Test
    void aHullWithoutAShieldRefusesTheShieldKey() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        assertFalse(WarshipPilot.command(ship, WarshipPilot.Command.TOGGLE_SHIELD));
    }

    @Test
    void onlyALiveFriendlyHullThatIsNotLeavingMayBePiloted() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        assertTrue(WarshipPilot.eligible(ship, Faction.MARINE));
        assertFalse(WarshipPilot.eligible(ship, Faction.DEFENDER));
        assertFalse(WarshipPilot.eligible(null, Faction.MARINE));
        ship.beginDeparture();
        assertFalse(WarshipPilot.eligible(ship, Faction.MARINE));
        Warship wreck = PilotShipFixture.ship(100f, 60f);
        wreck.takeDamage(1e9f, 1e6f);
        assertFalse(WarshipPilot.eligible(wreck, Faction.MARINE));
    }

    @Test
    void aClickPicksTheHullOnlyUnderItsFootprint() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        assertSame(ship, WarshipPilot.pick(List.of(ship), 100f, 60f, Faction.MARINE));
        assertNull(WarshipPilot.pick(List.of(ship), 100f + ship.widthCells + ship.lengthCells, 60f, Faction.MARINE));
        assertNull(WarshipPilot.pick(List.of(ship), 100f, 60f, Faction.DEFENDER), "an enemy hull is a target, not a unit to command");
    }

    // ---- handback ------------------------------------------------------------------------------

    @Test
    void handbackRestoresTheDirectorTheGroundAndTheStationAndLeavesNoPilotOrder() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.stationTo(120f, 70f);
        ship.direct(8f, true);
        ship.order(new WarshipOrder.FireOnArea(30f, 30f, 6f, false));
        ship.groundFire(Warship.GroundFire.HEAVY);
        List<WarshipOrder.FireOnArea> before = List.copyOf(ship.areas());

        ship.takeHelm();
        assertTrue(ship.piloted());
        WarshipPilot.command(ship, WarshipPilot.Command.HOLD_FIRE);
        WarshipPilot.apply(ship, new WarshipPilot.Hands(1f, 0f, 40f, 30f, true, true), W, H, (x, y) -> true);
        ship.groundFire(Warship.GroundFire.HOLD);
        assertFalse(ship.isDirected());
        ship.releaseHelm();

        assertFalse(ship.piloted());
        assertTrue(ship.isDirected(), "the director is back");
        assertEquals(before, ship.areas(), "the ground it held is back, and none of the pilot's");
        assertNull(ship.laidArea(Warship.FireGroup.GUNS));
        assertNull(ship.laidArea(Warship.FireGroup.MISSILES));
        assertEquals(Warship.GroundFire.HEAVY, ship.groundFire());
        assertEquals(120f, ship.stationX());
        assertEquals(70f, ship.stationY());
        assertTrue(ship.hasStation());
        assertFalse(ship.pilotUnderWay(), "no stale drive demand survives the handback");
        assertTrue(ship.manualOrder(), "the hand order that stood before the pilot stands again");
    }

    @Test
    void handbackToAShipWithNoOrdersLeavesItHoldingNothing() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        WarshipPilot.apply(ship, hands(40f, 30f, true, true), W, H, (x, y) -> true);
        ship.releaseHelm();
        assertTrue(ship.areas().isEmpty());
        assertFalse(ship.hasStation());
        assertFalse(ship.isDirected());
    }

    @Test
    void takingTheHelmTwiceKeepsTheFirstSnapshot() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.direct(8f, true);
        ship.takeHelm();
        ship.pilotDirector(false);
        ship.takeHelm();
        ship.releaseHelm();
        assertTrue(ship.isDirected());
    }

    @Test
    void departureEndsPilotingAtOnce() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.beginDeparture();
        assertFalse(ship.piloted());
        assertTrue(ship.areas().isEmpty());
    }

    // ---- flying --------------------------------------------------------------------------------

    @Test
    void theHullAcceleratesAtItsOwnRateAndNeverExceedsItsSpeed() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.pilotMove(1f, 0f);
        WarshipHelm.drive(ship, DT, W, H);
        assertEquals(ship.acceleration * DT, ship.speed(), 1e-5f, "one tick of its own acceleration, no jump");
        assertEquals(100f + ship.acceleration * DT * DT, ship.x, 1e-5f);
        float peak = 0f;
        for (int i = 0; i < 300; i++) {
            WarshipHelm.drive(ship, DT, W, H);
            peak = Math.max(peak, ship.speed());
        }
        assertEquals(ship.maxSpeed, peak, 1e-3f, "it reaches the table speed and goes no faster");
    }

    @Test
    void aDiagonalIsNoFasterThanAStraightLine() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.pilotMove(1f, 1f);
        for (int i = 0; i < 300; i++) WarshipHelm.drive(ship, DT, W, H);
        assertEquals(ship.maxSpeed, ship.speed(), 1e-3f);
    }

    @Test
    void releasingTheKeysBrakesAtTheHullsDecelerationRatherThanStopping() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.pilotMove(0f, 1f);
        for (int i = 0; i < 600; i++) WarshipHelm.drive(ship, DT, W, H);
        float cruising = ship.speed();
        ship.pilotMove(0f, 0f);
        WarshipHelm.drive(ship, DT, W, H);
        assertEquals(cruising - ship.deceleration * DT, ship.speed(), 1e-4f);
        for (int i = 0; i < 3000; i++) WarshipHelm.drive(ship, DT, W, H);
        assertEquals(0f, ship.speed(), 1e-5f);
    }

    @Test
    void theMapEdgeStopsTheHull() throws Exception {
        Warship ship = PilotShipFixture.ship(1f, 60f);
        ship.takeHelm();
        ship.pilotMove(-1f, 0f);
        for (int i = 0; i < 3000; i++) WarshipHelm.drive(ship, DT, W, H);
        assertEquals(0f, ship.x);
        assertTrue(ship.velocityX >= 0f, "the velocity into the edge is gone, not saved up");
        ship.pilotMove(0f, 1f);
        for (int i = 0; i < 30000; i++) WarshipHelm.drive(ship, DT, W, H);
        assertEquals(H, ship.y, 1e-4f);
    }

    @Test
    void theHullPointsAlongItsTravelAndHoldsItsHeadingWhenStopped() throws Exception {
        Warship ship = PilotShipFixture.ship(100f, 60f);
        ship.takeHelm();
        ship.pilotMove(1f, 0f);
        for (int i = 0; i < 60; i++) WarshipHelm.drive(ship, DT, W, H);
        WarshipHelm.steer(ship);
        assertEquals(AirBody.facingToward(1f, 0f), ship.desiredHeading, 1e-3f);
        ship.pilotMove(0f, 0f);
        for (int i = 0; i < 6000; i++) WarshipHelm.drive(ship, DT, W, H);
        float held = ship.desiredHeading;
        WarshipHelm.steer(ship);
        assertEquals(held, ship.desiredHeading, 1e-3f, "stopped with nothing laid, the heading is kept");
    }

}
