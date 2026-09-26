package com.dillon.starsectormarines.battle.nav;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationClearanceTest {
    @Test
    void routeInstallsResolvedOccupancyAndCompletedArrivalSurvivesClear() {
        NavigationGrid grid = corridor(2);
        try (var navigation = new NavigationService(grid, new CellTopology(12, 8), false)) {
            UnitRosterService roster = roster(navigation);
            long mech = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 2, 3)
                    .mechVariant(MechVariant.BULWARK).atPosition(2f, 4f));
            assertEquals(PathRequestStatus.READY, navigation.requestPath(mech, 9, 3));
            assertEquals(2f, roster.world().x(mech), "assignment must not move the body");
            assertNotNull(roster.movement().continuousRoute(mech));
            assertTrue(roster.movement().pathTargetsCell(mech, 9, 3));
            assertEquals(4f, roster.movement().destinationY(mech));
            assertTrue(navigation.isCellOccupied(9, 4), "occupancy uses the physical endpoint projection");
            roster.entityWorld().setFloat(mech, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, MechLocomotion.continuousFacing(1f, 0f));
            assertEquals(MovementService.MotionResult.ARRIVED, roster.movement().advanceAlongPath(
                    roster.world(), mech, 10f, grid, .6f));
            navigation.clearPath(mech);
            assertTrue(Paths.isEmpty(roster.world().path(mech)));
            assertTrue(roster.movement().atCell(mech, 9, 3));
            assertFalse(roster.movement().pathTargetsCell(mech, 9, 3));
            roster.world().setPos(mech, 8f, 4f);
            assertFalse(roster.movement().atCell(mech, 9, 3));
        }
    }

    @Test
    void changedTerrainCannotReuseACompletedEndpointProof() {
        NavigationGrid grid = corridor(2);
        try (var navigation = new NavigationService(grid, new CellTopology(12, 8), false)) {
            UnitRosterService roster = roster(navigation);
            long mech = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 2, 3)
                    .mechVariant(MechVariant.BULWARK).atPosition(2f, 4f));
            assertEquals(PathRequestStatus.READY, navigation.requestPath(mech, 9, 3));
            roster.entityWorld().setFloat(mech, roster.components().MECH_LOCOMOTION,
                    BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, MechLocomotion.continuousFacing(1f, 0f));
            assertEquals(MovementService.MotionResult.ARRIVED, roster.movement().advanceAlongPath(
                    roster.world(), mech, 10f, grid, .6f));
            navigation.clearPath(mech);
            grid.setWalkable(9, 3, false);
            assertFalse(roster.movement().atCell(mech, 9, 3), "a buried endpoint no longer proves arrival");
            assertEquals(PathRequestStatus.FAILED, navigation.requestPath(mech, 9, 3));
            assertNull(roster.movement().continuousRoute(mech));
            assertTrue(Paths.isEmpty(roster.world().path(mech)));
        }
    }

    @Test
    void oldIntegerPathCannotAuthorizeAnImpossibleChassisRoute() {
        NavigationGrid grid = corridor(1);
        // A room on each side has a legal starting/ending envelope.
        for (int y = 1; y < 7; y++) for (int x : new int[]{1, 2, 3, 8, 9, 10}) grid.setWalkableFloor(x, y);
        try (var navigation = new NavigationService(grid, new CellTopology(12, 8), false)) {
            UnitRosterService roster = roster(navigation);
            long mech = roster.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 2, 3)
                    .mechVariant(MechVariant.BULWARK));
            int[] pointPath = GridPathfinder.findPath(grid, 2, 3, 9, 3);
            assertFalse(Paths.isEmpty(pointPath));
            navigation.setPath(mech, pointPath);
            assertTrue(Paths.isEmpty(roster.world().path(mech)));
            assertNull(roster.movement().continuousRoute(mech));
            assertEquals(PathRequestStatus.FAILED, navigation.requestPath(mech, 9, 3));
        }
    }

    private static UnitRosterService roster(NavigationService navigation) {
        var roster = new UnitRosterService(navigation.getUnitIndex(), null);
        roster.setNavigationGrid(navigation.getGrid());
        navigation.setRoster(roster);
        navigation.setOccupancyDeltaSink(navigation::applyOccupancyDeltaInline);
        return roster;
    }

    private static NavigationGrid corridor(int width) {
        var grid = new NavigationGrid(12, 8);
        for (int y = 3; y < 3 + width; y++) for (int x = 1; x < 11; x++) grid.setWalkableFloor(x, y);
        return grid;
    }
}
