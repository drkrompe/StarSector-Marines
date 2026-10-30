package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.PilotShipFixture;
import com.dillon.starsectormarines.battle.warship.Warship;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShipCommandServiceTest {
    @BeforeEach void install() { PilotShipFixture.install(); }
    @AfterEach void restore() { PilotShipFixture.restore(); }

    @Test void remoteTravelSurvivesPilotingAndAutoRestoresOriginalRoute() throws Exception {
        Warship ship = PilotShipFixture.ship(20, 20);
        ship.flyRoute(new float[][]{{30, 30}, {40, 40}}, true);
        ShipCommandService service = new ShipCommandService();
        NavigationGrid grid = new NavigationGrid(60, 60);
        service.requestMove(ship, 50, 50);
        assertEquals(30f, ship.stationX());
        service.drain(List.of(ship), List.of(), Faction.MARINE, grid, (x, y) -> true);
        assertEquals(50f, ship.stationX());
        assertFalse(ship.isPassingThrough());
        ship.takeHelm();
        ship.releaseHelm();
        assertEquals(50f, ship.stationX());
        service.requestAutonomous(ship);
        service.drain(List.of(ship), List.of(), Faction.MARINE, grid, (x, y) -> true);
        assertEquals(30f, ship.stationX());
        assertTrue(ship.isPassingThrough());
        assertEquals(Warship.RemoteOrder.NONE, ship.remoteOrder());
    }

    @Test void lowHoverHullFliesOverBlockedGroundToTheOrderedPoint() throws Exception {
        Warship ship = PilotShipFixture.ship(10, 30);
        ship.lowHoverFootprint(4, 4);
        NavigationGrid grid = new NavigationGrid(60, 60);
        for (int y = 0; y < 60; y++) for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        for (int y = 0; y < 60; y++) grid.setWalkable(30, y, false);
        ShipCommandService service = new ShipCommandService();
        service.requestMove(ship, 50, 30);
        service.drain(List.of(ship), List.of(), Faction.MARINE, grid, (x, y) -> true);
        assertEquals(ShipCommandService.Notice.MOVING, service.notice(ship));
        assertEquals(50f, ship.stationX());
    }

    @Test void hiddenAndUnregisteredRequestsDoNotMutateTravel() throws Exception {
        Warship ship = PilotShipFixture.ship(20, 20);
        ship.stationTo(25, 25);
        ShipCommandService service = new ShipCommandService();
        NavigationGrid grid = new NavigationGrid(60, 60);
        service.requestMove(ship, 50, 50);
        service.drain(List.of(ship), List.of(), Faction.MARINE, grid, (x, y) -> false);
        assertEquals(ShipCommandService.Notice.UNSEEN, service.notice(ship));
        assertEquals(25f, ship.stationX());
        service.requestHold(ship);
        service.drain(List.of(), List.of(), Faction.MARINE, grid, (x, y) -> true);
        assertEquals(ShipCommandService.Notice.UNAVAILABLE, service.notice(ship));
        assertEquals(25f, ship.stationX());
    }

    @Test void pilotAndSpentVisitRefuseOrders() throws Exception {
        Warship ship = PilotShipFixture.ship(20, 20);
        ship.takeHelm();
        assertEquals(ShipCommandService.Notice.PILOTED, ShipCommandService.eligibility(ship, null, Faction.MARINE));
        ship.releaseHelm();
        ship.stayFor(0);
        assertEquals(ShipCommandService.Notice.UNAVAILABLE, ShipCommandService.eligibility(ship, null, Faction.MARINE));
        assertEquals(ShipCommandService.Notice.UNAVAILABLE, ShipCommandService.eligibility(ship, null, Faction.ALLY));
    }

    @Test void queuedOrderDoesNotSurviveAnInterveningPilotSession() throws Exception {
        Warship ship = PilotShipFixture.ship(20, 20);
        ship.stationTo(25, 25);
        ShipCommandService service = new ShipCommandService();
        service.requestMove(ship, 50, 50);
        ship.takeHelm();
        ship.releaseHelm();
        service.drain(List.of(ship), List.of(), Faction.MARINE, new NavigationGrid(60, 60), (x, y) -> true);
        assertEquals(25f, ship.stationX());
        assertEquals(ShipCommandService.Notice.PILOTED, service.notice(ship));
    }
}
