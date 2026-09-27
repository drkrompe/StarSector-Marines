package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuietPatrolRouteTest {
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
    private final boolean previousCardinal = GridPathfinder.USE_CARDINAL_NAVIGATION;
    private final String previousControl = System.getProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY);

    @AfterEach
    void restore() {
        TickInnerProfile.setCurrent(previousProfile);
        GridPathfinder.USE_CARDINAL_NAVIGATION = previousCardinal;
        if (previousControl == null) System.clearProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY);
        else System.setProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY, previousControl);
    }

    @Test
    void ordinaryRoutesMatchTheSameInputLegacyControlInBothMovementModes() {
        NavigationGrid grid = openGrid(24, 20);
        for (boolean cardinal : new boolean[]{false, true}) {
            GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
            int[] expected = GridPathfinder.findPath(grid, 3, 4, 17, 13, new byte[24 * 20]);
            TickInnerProfile profile = bindProfile();
            int[] actual = QuietPatrolRoute.find(grid, 3, 4, 17, 13, cardinal);
            assertArrayEquals(expected, actual);
            assertLegal(grid, actual);
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_SEARCH));
            assertEquals(profile.pathfindExpandedNodes(), profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_EXPANDED));
            assertTrue(profile.pathfindExpandedNodes() <= QuietPatrolRoute.MAX_EXPANDED_NODES);
        }
    }

    @Test
    void distantDoglegStopsAtTheExpansionAllowanceWithoutAFullMapComponentProof() {
        NavigationGrid grid = openGrid(130, 100);
        for (int y = 2; y < 100; y++) grid.setWalkable(65, y, false);
        TickInnerProfile profile = bindProfile();
        assertTrue(Paths.isEmpty(QuietPatrolRoute.find(grid, 4, 90, 115, 90, true)));
        assertEquals(QuietPatrolRoute.MAX_EXPANDED_NODES, profile.pathfindExpandedNodes());
    }

    @Test
    void disconnectedAndCornerBlockedGoalsNeverReceivePartialRoutes() {
        NavigationGrid grid = openGrid(4, 4);
        grid.setWalkable(1, 0, false);
        grid.setWalkable(0, 1, false);
        assertTrue(Paths.isEmpty(QuietPatrolRoute.find(grid, 0, 0, 1, 1, false)));
        assertTrue(Paths.isEmpty(QuietPatrolRoute.find(grid, 0, 0, 1, 1, true)));
    }

    @Test
    void refusingAnOptionalWaypointSurvivesRepeatedCallbacksAndNewSources() {
        System.clearProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY);
        Fixture fixture = new Fixture(dogleg(), 25, 50, 36, 50);
        TickInnerProfile profile = bindProfile();
        assertEquals(ActionStatus.RUNNING, fixture.advance(true));
        assertTrue(Paths.isEmpty(fixture.roster.world().path(fixture.member)));
        assertEquals(-1, fixture.squad.patrolWaypointX);
        assertEquals(PatrolMotion.DWELL_SECONDS, fixture.squad.patrolDwellTimer);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_SEARCH));
        for (int i = 0; i < 40; i++) fixture.advance(true);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_SEARCH));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_REFUSAL));
        assertEquals(40, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_BACKOFF));
        assertEquals(0, fixture.moves);
    }

    @Test
    void refusalCannotEraseANewerWaypointOrRenewTheBackoff() {
        Squad squad = new Squad(27, Faction.DEFENDER);
        squad.patrolWaypointX = 8;
        squad.patrolWaypointY = 9;
        assertFalse(QuietPatrolRoute.refuse(squad, 7, 9));
        assertEquals(8, squad.patrolWaypointX);
        assertTrue(QuietPatrolRoute.refuse(squad, 8, 9));
        squad.patrolDwellTimer = 2f;
        assertFalse(QuietPatrolRoute.refuse(squad, 8, 9));
        assertEquals(2f, squad.patrolDwellTimer);
    }

    @Test
    void legacySwitchAndNonOptionalAndRequiredRoutesStillTakeTheLongWay() {
        NavigationGrid grid = dogleg();
        for (boolean optional : new boolean[]{false, true}) {
            System.setProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY, optional ? "false" : "true");
            Fixture fixture = new Fixture(grid, 25, 50, 36, 50);
            fixture.advance(optional);
            assertFalse(Paths.isEmpty(fixture.roster.world().path(fixture.member)));
            assertEquals(1, fixture.moves);
            assertEquals(36, fixture.squad.patrolWaypointX);
        }
        System.setProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY, "true");
        Fixture required = new Fixture(grid, 25, 50, 36, 50);
        assertTrue(PatrolMotion.moveToward(required.member, required.sim, 36, 50));
        assertFalse(Paths.isEmpty(required.roster.world().path(required.member)));
        assertEquals(1, required.moves);
    }

    @Test
    void throttledObsoletePathHoldsWithoutRefusingTheQuietWaypoint() {
        System.setProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY, "true");
        Fixture fixture = new Fixture(openGrid(20, 20), 2, 3, 15, 3);
        fixture.install(new int[]{2, 3, 2, 4, 2, 5});
        TickInnerProfile profile = bindProfile();
        fixture.advance(true);
        assertEquals(0, fixture.moves);
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.GUARD_PATROL_SEARCH));
        assertTrue(Paths.isEmpty(fixture.roster.world().path(fixture.member)));
        assertEquals(15, fixture.squad.patrolWaypointX);
        assertEquals(0, fixture.squad.patrolDwellTimer);
    }

    @Test
    void freshResultWaitsOneTickAndCannotPublishAfterASiblingRefusal() {
        System.setProperty(PatrolMotion.BOUND_GUARD_PATROL_PROPERTY, "true");
        Fixture success = new Fixture(openGrid(20, 20), 2, 3, 15, 3);
        success.advance(true);
        assertFalse(Paths.isEmpty(success.roster.world().path(success.member)));
        assertEquals(0, success.moves);
        success.advance(true);
        assertEquals(1, success.moves);

        Fixture stale = new Fixture(openGrid(20, 20), 2, 3, 15, 3);
        stale.beforeGridRead = () -> QuietPatrolRoute.refuse(stale.squad, 15, 3);
        stale.advance(true);
        assertTrue(Paths.isEmpty(stale.roster.world().path(stale.member)));
        assertEquals(0, stale.moves);
        assertEquals(-1, stale.squad.patrolWaypointX);
    }

    private static TickInnerProfile bindProfile() {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        return profile;
    }

    private static NavigationGrid dogleg() {
        NavigationGrid grid = openGrid(60, 70);
        for (int y = 2; y < 70; y++) grid.setWalkable(30, y, false);
        return grid;
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static void assertLegal(NavigationGrid grid, int[] path) {
        assertFalse(Paths.isEmpty(path));
        for (int i = 1; i < Paths.cellCount(path); i++) {
            assertTrue(grid.canTraverseCellStep(Paths.cellX(path, i - 1), Paths.cellY(path, i - 1),
                    Paths.cellX(path, i), Paths.cellY(path, i)));
        }
    }

    private static final class Fixture {
        final UnitRosterService roster;
        final long member;
        final Squad squad = new Squad(27, Faction.DEFENDER);
        final BattleControl sim;
        int moves;
        Runnable beforeGridRead = () -> { };

        Fixture(NavigationGrid grid, int fromX, int fromY, int goalX, int goalY) {
            roster = new UnitRosterService(new UnitSpatialIndex(grid.getWidth(), grid.getHeight()), null);
            member = roster.spawn(new EntitySpec("guard", Faction.DEFENDER, UnitType.MARINE, fromX, fromY));
            squad.leaderId = member;
            squad.aliveMembers = 1;
            squad.centroidX = fromX + 0.5f;
            squad.centroidY = fromY + 0.5f;
            squad.patrolWaypointX = goalX;
            squad.patrolWaypointY = goalY;
            byte[] occupancy = new byte[grid.getWidth() * grid.getHeight()];
            sim = (BattleControl) Proxy.newProxyInstance(BattleControl.class.getClassLoader(),
                    new Class<?>[]{BattleControl.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "world" -> roster.world();
                        case "movement" -> roster.movement();
                        case "squad" -> roster.squad();
                        case "resolveUnit" -> (long) args[0] == member ? member : 0L;
                        case "isRiding" -> false;
                        case "getGrid" -> { beforeGridRead.run(); yield grid; }
                        case "getOccupancyMap" -> occupancy;
                        case "setPath" -> { install((int[]) args[1]); yield null; }
                        case "clearPath" -> { install(GridPathfinder.EMPTY_PATH); yield null; }
                        case "advanceMovement" -> { moves++; yield null; }
                        default -> throw new AssertionError("Unexpected query: " + method.getName());
                    });
        }

        void install(int[] path) {
            roster.movement().setPathRef(member, path);
            roster.movement().setPathIdx(member, path.length <= 2 ? 0 : 1);
            if (!Paths.isEmpty(path)) roster.movement().markRepath(member);
        }

        ActionStatus advance(boolean optional) {
            PatrolMotion.WaypointSource source = new PatrolMotion.WaypointSource() {
                @Override public boolean optionalLocalRouting() { return optional; }
                @Override public int[] next(long ignored, Squad ignoredSquad,
                                            BattleView ignoredView) {
                    return null;
                }
            };
            return PatrolMotion.advance(member, squad, sim, source, false);
        }
    }
}
