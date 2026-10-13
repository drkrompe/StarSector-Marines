package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order's lifecycle on a ship: who may begin one, what it costs, and what is given back. */
class WarshipLowPassTest {

    private static final int W = 400;
    private static final int H = 300;
    private static final float DT = 1f / 30f;

    @BeforeEach
    void install() {
        PilotShipFixture.install();
    }

    @AfterEach
    void restore() {
        PilotShipFixture.restore();
    }

    private static LowPass plan(Warship ship, float bx, float by) {
        return LowPass.plan(ship.x, ship.y, ship.x, ship.y, bx, by, W, H);
    }

    @Test
    void aLiveShipWithUnboundedStayMayPassAndItsLineBecomesTheOrder() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        LowPass plan = plan(ship, 200f, 100f);
        assertTrue(ship.canLowPass(plan));
        assertTrue(ship.beginLowPass(plan));
        assertTrue(ship.lowPassing());
        assertSame(plan, ship.lowPass());
        assertFalse(ship.beginLowPass(plan(ship, 200f, 150f)), "one pass at a time");
    }

    @Test
    void aShortLineIsNotAPlanAndNothingChanges() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        assertNull(plan(ship, 60f, 100f));
        assertFalse(ship.canLowPass(null));
        assertFalse(ship.lowPassing());
    }

    @Test
    void aDepartingShipMayNotPass() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        ship.beginDeparture();
        assertFalse(ship.canLowPass(plan(ship, 200f, 100f)));
    }

    @Test
    void aShipWithTooLittleStationTimeLeftIsRefusedAndOneWithEnoughIsNot() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        LowPass plan = plan(ship, 200f, 100f);
        float cost = plan.estimatedCost(ship.x, ship.y, ship.maxSpeed, ship.acceleration);
        ship.stayFor(cost - 0.5f);
        assertFalse(ship.canLowPass(plan));
        ship.stayFor(cost + 0.5f);
        assertTrue(ship.canLowPass(plan));
    }

    @Test
    void thePassIsChargedTwoSecondsOfStationTimeForEverySecondItFliesAndNeverDepartsMidRun() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        LowPass plan = plan(ship, 200f, 100f);
        ship.stayFor(10f);
        // A ship that could not afford the whole pass is refused; force the begin to test the charge itself.
        ship.stayFor(plan.estimatedCost(ship.x, ship.y, ship.maxSpeed, ship.acceleration) + 10f);
        float before = ship.stationSecondsRemaining();
        assertTrue(ship.beginLowPass(plan));
        for (int i = 0; i < 30; i++) assertFalse(ship.spendStationTime(DT));
        assertEquals(before - 2f * 30 * DT, ship.stationSecondsRemaining(), 1e-3f);
    }

    @Test
    void anExhaustedStayDepartsOnlyAfterThePassHasFinished() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        LowPass plan = plan(ship, 200f, 100f);
        ship.stayFor(plan.estimatedCost(ship.x, ship.y, ship.maxSpeed, ship.acceleration) + 0.1f);
        assertTrue(ship.beginLowPass(plan));
        for (int i = 0; i < 3000; i++) assertFalse(ship.spendStationTime(DT), "never mid-pass");
        assertEquals(0f, ship.stationSecondsRemaining(), 1e-4f);
        plan.advance(DT, 199.5f, 100f);
        ship.finishLowPass();
        assertTrue(ship.spendStationTime(DT), "due to leave once the run is over");
    }

    @Test
    void itFiresThroughALaidCircleOnEveryBatteryAndGivesTheOldGroundBackAtTheEnd() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        WarshipOrder.FireOnArea old = WarshipOrder.FireOnArea.saturating(10f, 10f, 5f, false);
        ship.layArea(Warship.FireGroup.GUNS, old);
        assertTrue(ship.beginLowPass(plan(ship, 200f, 100f)));
        ship.layPassFire(80f, 100f);
        WarshipOrder.FireOnArea guns = ship.laidArea(Warship.FireGroup.GUNS);
        WarshipOrder.FireOnArea missiles = ship.laidArea(Warship.FireGroup.MISSILES);
        assertEquals(80f, guns.x());
        assertEquals(LowPass.CORRIDOR_HALF_WIDTH_CELLS, guns.radius());
        assertNotNull(missiles);
        assertFalse(guns.bringGunsToBear(), "the pass keeps its heading along the line");
        ship.finishLowPass();
        assertSame(old, ship.laidArea(Warship.FireGroup.GUNS));
        assertNull(ship.laidArea(Warship.FireGroup.MISSILES));
    }

    @Test
    void holdFireSilencesThePassGunsButNotItsFlight() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        ship.takeHelm();
        assertTrue(ship.beginLowPass(plan(ship, 200f, 100f)));
        ship.layPassFire(80f, 100f);
        assertNotNull(ship.laidArea(Warship.FireGroup.GUNS));
        assertTrue(WarshipPilot.command(ship, WarshipPilot.Command.HOLD_FIRE));
        assertNull(ship.laidArea(Warship.FireGroup.GUNS));
        assertTrue(ship.lowPassSilenced());
        assertTrue(ship.lowPassing());
    }

    @Test
    void thePilotsTriggersLayNothingWhileThePassFlies() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        ship.takeHelm();
        assertTrue(ship.beginLowPass(plan(ship, 200f, 100f)));
        WarshipPilot.apply(ship, new WarshipPilot.Hands(0f, 0f, 150f, 150f, true, true), W, H, (x, y) -> true);
        assertNull(ship.laidArea(Warship.FireGroup.GUNS));
    }

    @Test
    void thePilotsPassRunsFromTheHullToTheCursorAndNeedsGroundTheyHaveSeen() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        ship.takeHelm();
        WarshipPilot.Hands cursor = new WarshipPilot.Hands(0f, 0f, 180f, 100f, false, false);
        assertFalse(WarshipPilot.lowPass(ship, cursor, W, H, (x, y) -> false), "unseen ground");
        assertFalse(WarshipPilot.lowPass(ship, WarshipPilot.Hands.NONE, W, H, (x, y) -> true), "no cursor");
        assertFalse(WarshipPilot.lowPass(ship,
                new WarshipPilot.Hands(0f, 0f, 55f, 100f, false, false), W, H, (x, y) -> true), "too short");
        assertTrue(WarshipPilot.lowPass(ship, cursor, W, H, (x, y) -> true));
        LowPass pass = ship.lowPass();
        assertEquals(50f, pass.startX(), 1e-4f);
        assertEquals(180f, pass.endX(), 1e-4f);
        assertTrue(ship.piloted(), "the pilot keeps the helm");
        assertFalse(WarshipPilot.lowPass(ship, cursor, W, H, (x, y) -> true), "already passing");
    }

    @Test
    void aFinishedPassKeepsItsWakeUntilItIsSpentThenIsDropped() throws Exception {
        Warship ship = PilotShipFixture.ship(50f, 100f);
        LowPass plan = plan(ship, 200f, 100f);
        assertTrue(ship.beginLowPass(plan));
        plan.advance(DT, 199.5f, 100f);
        ship.finishLowPass();
        assertFalse(ship.lowPassing());
        assertSame(plan, ship.lowPass(), "the trail still shows");
        assertTrue(ship.canLowPass(plan(ship, 200f, 150f)), "and a second pass may follow");
        ship.clearLowPass();
        assertNull(ship.lowPass());
    }
}
