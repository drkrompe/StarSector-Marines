package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforceContactSnapTest {
    @AfterEach
    void releaseProfile() { TickInnerProfile.releaseCurrentThread(); }

    @Test
    void openGroundMakesOneSearchInsteadOf121AndPreservesAttribution() {
        NavigationGrid grid = openGrid(30, 30);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        int[] exhaustive = ReinforceContact.snapToReachable(15, 15, grid, 3, 3, false);
        assertEquals(121, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        profile.reset();
        profile.enterAction(17L, 3, "AttackMove");

        int[] pruned = ReinforceContact.snapToReachable(15, 15, grid, 3, 3, true);

        assertArrayEquals(exhaustive, pruned);
        assertArrayEquals(new int[]{15, 15}, pruned);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        TickInnerProfile.PathSearch sample = profile.slowPathSearches().get(0);
        assertEquals("FLANK_SNAP", sample.routeReason());
        assertEquals("AttackMove", sample.action());
        assertEquals(17L, sample.memberId());
        // A later objective search must not inherit flank attribution.
        GridPathfinder.findPath(grid, 3, 3, 5, 3);
        assertEquals("", profile.slowPathSearches().stream()
                .filter(search -> search.goalX() == 5 && search.goalY() == 3)
                .findFirst().orElseThrow().routeReason());
    }

    @Test
    void equalScoresKeepTheOriginalRingEnumerationWinner() {
        NavigationGrid grid = openGrid(24, 24);
        grid.setWalkable(10, 10, false);
        int[] result = ReinforceContact.snapToReachable(10, 10, grid, 0, 0, true);
        assertArrayEquals(new int[]{10, 9}, result);
        assertArrayEquals(exhaustiveOracle(grid, 10, 10, 0, 0), result);
    }

    @Test
    void doorwayCandidatesAreExcludedButDoorwaysRemainLegalRouteTransitions() {
        NavigationGrid grid = openGrid(24, 18);
        for (int y = 0; y < 18; y++) grid.setWalkable(8, y, false);
        grid.setWalkableFloor(8, 8);
        grid.setDoorway(8, 8, true);
        grid.setDoorway(15, 8, true);
        int[] result = ReinforceContact.snapToReachable(15, 8, grid, 3, 8, true);
        assertArrayEquals(exhaustiveOracle(grid, 15, 8, 3, 8), result);
        assertFalse(grid.isDoorway(result[0], result[1]));
        assertTrue(result[0] > 8, "the door may be crossed, not selected as the flank post");
    }

    @Test
    void extremeDoglegRefusalStillReturnsTheOrigin() {
        NavigationGrid grid = new NavigationGrid(30, 20);
        for (int y = 1; y < 19; y++) {
            for (int x = 1; x < 29; x++) if (x != 15) grid.setWalkableFloor(x, y);
        }
        grid.setWalkableFloor(15, 1);
        grid.setDoorway(15, 1, true);
        int[] result = ReinforceContact.snapToReachable(20, 15, grid, 10, 15, true);
        assertArrayEquals(new int[]{10, 15}, result);
        assertArrayEquals(exhaustiveOracle(grid, 20, 15, 10, 15), result);
    }

    @Test
    void absentReachableIncumbentNeverPrunesAnUnprovedCandidate() {
        NavigationGrid grid = openGrid(24, 18);
        for (int y = 0; y < 18; y++) grid.setWalkable(8, y, false);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        int[] result = ReinforceContact.snapToReachable(16, 8, grid, 3, 8, true);
        assertArrayEquals(new int[]{3, 8}, result);
        assertEquals(121, profile.countOf(TickInnerProfile.Bucket.PATHFIND),
                "the score bound alone cannot prove disconnected candidates unreachable");
    }

    @Test
    void distantOriginsUseTheRouteLowerBoundNotJustTheRawCellPenalty() {
        NavigationGrid grid = openGrid(1110, 13);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        int[] result = ReinforceContact.snapToReachable(1100, 6, grid, 0, 6, true);
        assertArrayEquals(new int[]{1100, 6}, result);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.PATHFIND),
                "raw-distance penalty alone is smaller than this incumbent's route length");
        assertArrayEquals(exhaustiveOracle(grid, 1100, 6, 0, 6), result);
    }

    @Test
    void optimizedAndControlMatchIndependentExhaustiveOracleOnSmallRandomGrids() {
        Random random = new Random(0x5A17C0DEL);
        for (int trial = 0; trial < 24; trial++) {
            NavigationGrid grid = new NavigationGrid(12, 12);
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    if (random.nextInt(5) != 0) grid.setWalkableFloor(x, y);
                    if (random.nextInt(23) == 0) grid.setDoorway(x, y, true);
                }
            }
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    if (random.nextInt(17) == 0) grid.blockSharedEdge(x, y, Direction.E);
                    if (random.nextInt(17) == 0) grid.blockEdge(x, y, Direction.N);
                }
            }
            int originX = random.nextInt(12), originY = random.nextInt(12);
            grid.setWalkable(originX, originY, true);
            int rawX = random.nextInt(18) - 3, rawY = random.nextInt(18) - 3;
            int[] oracle = exhaustiveOracle(grid, rawX, rawY, originX, originY);
            assertArrayEquals(oracle, ReinforceContact.snapToReachable(
                    rawX, rawY, grid, originX, originY, true), "pruned trial " + trial);
            assertArrayEquals(oracle, ReinforceContact.snapToReachable(
                    rawX, rawY, grid, originX, originY, false), "control trial " + trial);
        }
    }

    @Test
    void publicSameBuildSwitchSelectsExhaustiveOrPrunedWork() {
        NavigationGrid grid = openGrid(30, 30);
        Squad squad = new Squad(1, Faction.MARINE);
        squad.centroidX = squad.centroidY = 3.5f;
        BattleView view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "resolveUnit" -> 0L;
                    case "squadMemberCount" -> 0;
                    default -> throw new AssertionError("Unexpected query: " + method.getName());
                });
        String property = ReinforceContact.PRUNE_FLANK_CANDIDATES_PROPERTY;
        String previous = System.getProperty(property);
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try {
            System.setProperty(property, "false");
            int[] control = ReinforceContact.snapToReachable(15, 15, squad, view);
            assertEquals(121, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
            profile.reset();
            System.clearProperty(property);
            assertArrayEquals(control, ReinforceContact.snapToReachable(15, 15, squad, view));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
        }
    }

    /** The pre-pruning exhaustive implementation, kept separate from the subject helper. */
    private static int[] exhaustiveOracle(NavigationGrid grid, int rawX, int rawY,
                                           int originX, int originY) {
        float best = Float.MAX_VALUE;
        int[] winner = {originX, originY};
        for (int ring = 0; ring <= 5; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (ring != 0 && Math.abs(dx) != ring && Math.abs(dy) != ring) continue;
                    int x = rawX + dx, y = rawY + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || grid.isDoorway(x, y)) continue;
                    int[] path = GridPathfinder.findPath(grid, originX, originY, x, y);
                    if (Paths.isEmpty(path)) continue;
                    int steps = Paths.cellCount(path) - 1;
                    int direct = Math.max(Math.abs(x - originX), Math.abs(y - originY));
                    if (steps > direct * 1.75f + 4 || steps - direct > 8) continue;
                    float score = (dx * dx + dy * dy) * 1000f + steps;
                    if (score < best) {
                        best = score;
                        winner = new int[]{x, y};
                    }
                }
            }
        }
        return winner;
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
