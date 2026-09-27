package com.dillon.starsectormarines.battle.infantry;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforceContactWorkBudgetTest {
    private final boolean originalCardinal = GridPathfinder.USE_CARDINAL_NAVIGATION;

    @AfterEach
    void releaseProfileAndRestoreMovement() {
        TickInnerProfile.releaseCurrentThread();
        GridPathfinder.USE_CARDINAL_NAVIGATION = originalCardinal;
    }

    @Test
    void distantDoglegStopsInsideFirstProofEvenWhenTheStepGateCannotApply() {
        GridPathfinder.USE_CARDINAL_NAVIGATION = true;
        NavigationGrid grid = openGrid(130, 100);
        for (int y = 2; y < 100; y++) grid.setWalkable(65, y, false);
        TickInnerProfile control = bindProfile();
        assertArrayEquals(new int[]{4, 90}, ReinforceContact.snapToReachable(
                115, 90, grid, 4, 90, true, true, true));
        assertTrue(control.countOf(TickInnerProfile.Bucket.PATHFIND) >= 100);
        assertTrue(control.pathfindExpandedNodes() > 8192);
        assertEquals(0, control.countOf(TickInnerProfile.Bucket.FLANK_STEP_FIELD));

        // The whole-selection ceiling is independent of the older per-proof
        // detour envelope. Both arms stop the very first expensive search.
        for (boolean boundProofs : new boolean[]{true, false}) {
            TickInnerProfile subject = bindProfile();
            assertArrayEquals(new int[]{4, 90}, ReinforceContact.snapToReachable(
                    115, 90, grid, 4, 90, true, boundProofs, true, 128));
            assertEquals(1, subject.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(0, subject.countOf(TickInnerProfile.Bucket.FLANK_STEP_FIELD));
            assertWork(subject, 128, true);
            assertEquals(1, subject.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT_REFUSAL));
        }
    }

    @Test
    void multipleProofsShareOneAllowanceAndKeepTheAlreadyVerifiedIncumbent() {
        NavigationGrid grid = openGrid(30, 20);
        for (boolean cardinal : new boolean[]{true, false}) {
            GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
            for (boolean boundProofs : new boolean[]{true, false}) {
                TickInnerProfile first = bindProfile();
                int[] path = boundProofs
                        ? GridPathfinder.findPathWithinStepEnvelope(grid, 3, 8, 15, 8, cardinal, 20)
                        : GridPathfinder.findPath(grid, 3, 8, 15, 8);
                assertFalse(Paths.isEmpty(path));
                int budget = (int) first.pathfindExpandedNodes() + 2;
                TickInnerProfile subject = bindProfile();
                assertArrayEquals(new int[]{15, 8}, ReinforceContact.snapToReachable(
                        15, 8, grid, 3, 8, false, boundProofs, false, budget));
                assertEquals(2, subject.countOf(TickInnerProfile.Bucket.PATHFIND),
                        "the first completed proof and next partial proof share the same budget");
                assertWork(subject, budget, true);
                assertEquals(0, subject.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT_REFUSAL),
                        "exhaustion retains the verified incumbent rather than refusing the flank");
            }
        }
    }

    @Test
    void sharedStepFloodSpendsOnlyWhatTheFirstFourProofsLeft() {
        NavigationGrid grid = openGrid(60, 70);
        for (int y = 2; y < 70; y++) grid.setWalkable(30, y, false);
        for (boolean cardinal : new boolean[]{true, false}) {
            GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
            TickInnerProfile firstFour = bindProfile();
            // Exact first four eligible candidates in the unchanged ring order.
            for (int[] goal : new int[][]{{36, 50}, {35, 49}, {36, 49}, {37, 49}}) {
                int direct = Math.max(Math.abs(goal[0] - 25), Math.abs(goal[1] - 50));
                int maxSteps = Math.min(direct + ReinforceContact.MAX_FLANK_EXTRA_STEPS,
                        (int) Math.floor(direct * ReinforceContact.MAX_FLANK_DETOUR_RATIO
                                + ReinforceContact.MAX_FLANK_DETOUR_SLACK));
                assertTrue(Paths.isEmpty(GridPathfinder.findPathWithinStepEnvelope(
                        grid, 25, 50, goal[0], goal[1], cardinal, maxSteps)));
            }
            int budget = (int) firstFour.pathfindExpandedNodes() + 17;
            TickInnerProfile subject = bindProfile();
            assertArrayEquals(new int[]{25, 50}, ReinforceContact.snapToReachable(
                    36, 50, grid, 25, 50, true, true, true, budget));
            assertEquals(4, subject.countOf(TickInnerProfile.Bucket.PATHFIND));
            assertEquals(1, subject.countOf(TickInnerProfile.Bucket.FLANK_STEP_FIELD));
            assertEquals(17, subject.countOf(TickInnerProfile.Bucket.FLANK_STEP_EXPANDED));
            assertWork(subject, budget, true);
            assertEquals(1, subject.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT_REFUSAL));
        }
    }

    @Test
    void ordinaryWinnerAndMovementModesAreUnchangedWhenTheAllowanceIsSufficient() {
        NavigationGrid grid = openGrid(30, 20);
        grid.setDoorway(15, 8, true);
        for (boolean cardinal : new boolean[]{true, false}) {
            GridPathfinder.USE_CARDINAL_NAVIGATION = cardinal;
            for (boolean boundProofs : new boolean[]{true, false}) {
                for (boolean prune : new boolean[]{true, false}) {
                    bindProfile();
                    int[] expected = ReinforceContact.snapToReachable(
                            15, 8, grid, 3, 8, prune, boundProofs, true);
                    TickInnerProfile subject = bindProfile();
                    int[] result = ReinforceContact.snapToReachable(
                            15, 8, grid, 3, 8, prune, boundProofs, true, 100_000);
                    assertArrayEquals(expected, result);
                    assertFalse(grid.isDoorway(result[0], result[1]));
                    assertEquals(0, subject.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT));
                    assertEquals(subject.pathfindExpandedNodes()
                                    + subject.countOf(TickInnerProfile.Bucket.FLANK_STEP_EXPANDED),
                            subject.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_EXPANDED));
                }
            }
        }
    }

    @Test
    void zeroAllowanceDoesNotRunAnEligibleSearchAndBudgetDoesNotDependOnProfiling() {
        NavigationGrid grid = openGrid(30, 20);
        TickInnerProfile profile = bindProfile();
        assertArrayEquals(new int[]{3, 8}, ReinforceContact.snapToReachable(
                15, 8, grid, 3, 8, true, true, true, 0));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.PATHFIND));
        assertWork(profile, 0, true);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT_REFUSAL));
        TickInnerProfile.releaseCurrentThread();
        assertArrayEquals(new int[]{3, 8}, ReinforceContact.snapToReachable(
                15, 8, grid, 3, 8, true, true, true, 1));
    }

    @Test
    void bothProductionEntrypointsDefaultToTheCapAndKeepAnIndependentControl() {
        GridPathfinder.USE_CARDINAL_NAVIGATION = true;
        NavigationGrid grid = openGrid(130, 100);
        for (int y = 2; y < 100; y++) grid.setWalkable(65, y, false);
        BattleView view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "resolveUnit" -> 0L;
                    case "squadMemberCount", "getSimTickIndex" -> 0;
                    case "getSquads" -> List.of();
                    default -> throw new AssertionError("Unexpected query: " + method.getName());
                });
        String[] properties = {ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY,
                ReinforceContact.PRUNE_FLANK_CANDIDATES_PROPERTY,
                ReinforceContact.BOUND_FLANK_PROOFS_PROPERTY,
                ReinforceContact.FLANK_STEP_GATE_PROPERTY};
        String[] previous = new String[properties.length];
        for (int i = 0; i < properties.length; i++) previous[i] = System.getProperty(properties[i]);
        try {
            for (String property : properties) System.clearProperty(property);
            for (boolean planEntry : new boolean[]{false, true}) {
                for (boolean budgeted : new boolean[]{false, true}) {
                    if (budgeted) System.clearProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY);
                    else System.setProperty(ReinforceContact.BUDGET_FLANK_SELECTION_PROPERTY, "false");
                    Squad squad = new Squad(1, Faction.MARINE);
                    squad.centroidX = 4.5f;
                    squad.centroidY = 90.5f;
                    // Its perpendicular flank lands near (116, 90), also beyond
                    // the shared step gate's radius cap on the far side of the wall.
                    squad.lastSeenEnemyX = 115;
                    squad.lastSeenEnemyY = 80;
                    TickInnerProfile profile = bindProfile();
                    if (planEntry) ReinforceContact.INSTANCE.customPlan(squad, view);
                    else assertArrayEquals(new int[]{4, 90},
                            ReinforceContact.snapToReachable(115, 90, squad, view));
                    assertEquals(0, profile.countOf(TickInnerProfile.Bucket.FLANK_STEP_FIELD));
                    if (budgeted) assertWork(profile, ReinforceContact.FLANK_SELECTION_EXPANSIONS, true);
                    else {
                        assertTrue(profile.pathfindExpandedNodes() > ReinforceContact.FLANK_SELECTION_EXPANSIONS);
                        assertTrue(profile.countOf(TickInnerProfile.Bucket.PATHFIND) >= 100);
                        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT));
                    }
                }
            }
        } finally {
            for (int i = 0; i < properties.length; i++) {
                if (previous[i] == null) System.clearProperty(properties[i]);
                else System.setProperty(properties[i], previous[i]);
            }
        }
    }

    private static void assertWork(TickInnerProfile profile, int expected, boolean limited) {
        long total = profile.pathfindExpandedNodes()
                + profile.countOf(TickInnerProfile.Bucket.FLANK_STEP_EXPANDED);
        assertEquals(expected, total, "A* and the optional step flood share the allowance");
        assertEquals(total, profile.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_EXPANDED));
        assertEquals(limited ? 1 : 0, profile.countOf(TickInnerProfile.Bucket.FLANK_SELECTION_LIMIT));
    }

    private static TickInnerProfile bindProfile() {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        return profile;
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
