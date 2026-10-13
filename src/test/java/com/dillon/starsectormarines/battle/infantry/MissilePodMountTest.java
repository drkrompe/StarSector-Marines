package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.MissilePodMountGeometry;
import com.dillon.starsectormarines.marine.MissilePodMountSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissilePodMountTest {
    @Test
    void travelBearingUsesNextWaypointEvenBeforeVelocityPublication() {
        UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(10, 10), null);
        long id = roster.spawn(new EntitySpec("wearer", Faction.MARINE, UnitType.MARINE, 5, 5));
        MovementService movement = roster.movement();
        movement.setPathRef(id, new int[]{5, 5, 6, 5});
        movement.beginTick(0.033f);
        assertEquals(0f, movement.velX(id));
        assertEquals(-90f, IntegralSystemSystem.podTravelBearing(id, movement, 5.5f, 5.5f), 1e-6f);
        movement.setPathRef(id, new int[]{5, 5, 5, 6});
        assertEquals(0f, IntegralSystemSystem.podTravelBearing(id, movement, 5.5f, 5.5f), 1e-6f);
        movement.setPathRef(id, new int[0]);
        assertTrue(Float.isNaN(IntegralSystemSystem.podTravelBearing(id, movement, 5.5f, 5.5f)));
    }

    @Test
    void hardpointFollowsBodyWhileMouthFollowsIndependentTargetBearing() {
        MissilePodMountSpec mount = new MissilePodMountSpec(-0.25f, 0.05f, 0.15f);
        MissilePodMountGeometry.Pose north = MissilePodMountGeometry.resolve(5f, 5f, 0f, -90f, mount);
        assertEquals(4.75f, north.mountX(), 1e-6f);
        assertEquals(5.05f, north.mountY(), 1e-6f);
        assertEquals(4.90f, north.muzzleX(), 1e-6f);
        assertEquals(5.05f, north.muzzleY(), 1e-6f);
        MissilePodMountGeometry.Pose east = MissilePodMountGeometry.resolve(5f, 5f, -90f, 0f, mount);
        assertEquals(5.05f, east.mountX(), 1e-6f);
        assertEquals(5.25f, east.mountY(), 1e-6f);
        assertEquals(5.05f, east.muzzleX(), 1e-6f);
        assertEquals(5.40f, east.muzzleY(), 1e-6f);
        assertEquals(-90f, MissilePodMountGeometry.bearing(1f, 0f));
    }

    @Test
    void missingMountRetainsCenteredLegacyDelivery() {
        MissilePodMountGeometry.Pose pose = MissilePodMountGeometry.resolve(3f, 4f, 0f, 60f, null);
        assertEquals(3f, pose.muzzleX());
        assertEquals(4f, pose.muzzleY());
    }

    @Test
    void mouthCannotCrossOrOccupyWallAndCannotLeaveMap() {
        NavigationGrid grid = new NavigationGrid(5, 5);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) grid.setWalkableFloor(x, y);
        grid.setWalkable(2, 2, false);
        assertFalse(IntegralSystemSystem.legalPodOrigin(grid, 1.5f, 2.5f,
                new MissilePodMountGeometry.Pose(3.5f, 2.5f, 3.5f, 2.5f, -90f)));
        assertFalse(IntegralSystemSystem.legalPodOrigin(grid, 1.5f, 2.5f,
                new MissilePodMountGeometry.Pose(2.5f, 2.5f, 2.5f, 2.5f, -90f)));
        assertFalse(IntegralSystemSystem.legalPodOrigin(grid, 0.1f, 1.5f,
                new MissilePodMountGeometry.Pose(-0.1f, 1.5f, -0.1f, 1.5f, 90f)));
        assertTrue(IntegralSystemSystem.legalPodOrigin(grid, 1.5f, 1.5f,
                new MissilePodMountGeometry.Pose(1.2f, 1.5f, 1.2f, 1.7f, 0f)));
    }

    @Test
    void physicalSpecRejectsNonFiniteOrNegativeReach() {
        assertThrows(IllegalArgumentException.class, () -> new MissilePodMountSpec(Float.NaN, 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new MissilePodMountSpec(0f, 0f, -1f));
    }
}
