package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.sim.HullContactSystem;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingHullsTest {

    private static final VehicleType TANK = VehicleType.BREAKTHROUGH_TANK;

    /** A hull at y=20 facing north, the planning hull starting well clear of it at y=4. */
    private static StandingHulls clearStart() {
        StandingHulls hulls = new StandingHulls(new Pose(20f, 4f, 0f), TANK);
        hulls.add(20f, 20f, 0f, TANK.visualLengthCells, TANK.visualWidthCells);
        return hulls;
    }

    @Test
    void aPoseIsJudgedWithThePaddingAndTheSkinToSpare() {
        StandingHulls hulls = clearStart();
        float touching = TANK.visualLengthCells;
        assertFalse(hulls.blocks(20f, 20f - touching - 2f, 0f));
        assertTrue(hulls.blocks(20f, 20f - touching - .3f, 0f), "bare footprints apart, but within padding and skin");
    }

    @Test
    void aHullAlreadyCloseIsJudgedOnTheBareFootprintsSoThereIsAWayOut() {
        float gap = TANK.visualLengthCells + .2f;
        StandingHulls hulls = new StandingHulls(new Pose(20f, 20f - gap, 0f), TANK);
        hulls.add(20f, 20f, 0f, TANK.visualLengthCells, TANK.visualWidthCells);
        assertEquals(1, hulls.count());
        assertFalse(hulls.blocks(20f, 20f - gap, 0f), "where it starts");
        assertFalse(hulls.blocks(20f, 20f - gap - 1f, 0f));
        assertTrue(hulls.blocks(20f, 20f - gap + .5f, 0f), "further in");
    }

    @Test
    void aHullThePlanningHullIsAlreadyInsideIsLeftOut() {
        StandingHulls hulls = new StandingHulls(new Pose(20f, 19f, 0f), TANK);
        hulls.add(20f, 20f, 0f, TANK.visualLengthCells, TANK.visualWidthCells);
        assertEquals(0, hulls.count());
    }

    @Test
    void aPlanCrossesAHullBetweenItsPoses() {
        StandingHulls hulls = clearStart();
        Trajectory through = new Trajectory(new float[]{20f, 20f}, new float[]{4f, 40f}, new float[]{0f, 0f});
        Trajectory beside = new Trajectory(new float[]{10f, 10f}, new float[]{4f, 40f}, new float[]{0f, 0f});
        assertTrue(hulls.crossedBy(through));
        assertFalse(hulls.crossedBy(beside));
    }

    @Test
    void aDestinationIsTakenWhereAHullOfThisSizeWouldNotFit() {
        StandingHulls hulls = clearStart();
        assertTrue(hulls.take(20f, 20f, 0f));
        assertTrue(hulls.take(20f, 20f - TANK.visualLengthCells - .5f, 0f), "within the taken skin");
        assertFalse(hulls.take(20f, 20f - TANK.visualLengthCells - 2f, 0f));
    }

    @Test
    void aHullBrakedForAStandingHullIsHeldAndAFollowerOfAMovingOneIsNot() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(40, 40), null);
        ConvoyService convoy = roster.convoy();
        long follower = hull(convoy, 20.5f, 10f), ahead = hull(convoy, 20.5f, 10f + TANK.visualLengthCells + .1f);
        HullContactSystem contact = new HullContactSystem(roster, grid);

        convoy.body(follower).speed = 2f;
        contact.tick();
        assertTrue(convoy.control(follower).hullHold > 0f);
        assertEquals(0f, convoy.control(ahead).hullHold);

        convoy.body(follower).speed = 2f;
        convoy.body(ahead).speed = 1f;
        contact.tick();
        assertEquals(0f, convoy.control(follower).hullHold, "taking the pace of a moving hull ends the hold");
    }

    private static long hull(ConvoyService convoy, float x, float y) {
        VehicleMission mission = new VehicleMission(new float[]{x, x}, new float[]{y, y + 1f},
                new float[]{x, x}, new float[]{y + 1f, y}, 0f, 0);
        mission.state = VehicleState.DEPLOYED;
        long id = convoy.spawn(TANK, Faction.DEFENDER, mission);
        convoy.body(id).teleport(x, y, 0f);
        convoy.body(id).speed = 0f;
        return id;
    }
}
