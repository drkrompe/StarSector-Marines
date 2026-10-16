package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.SatchelApproach.Answer;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Cloud;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Enabler;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Move;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Plan;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Reading;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Verdict;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The satchel approach's preconditions and its choice of enabling action, each on a hand-made line of cells. */
class SatchelApproachTest {

    private static final double REACH = 24;
    private static final SatchelApproach.Sight OPEN = (x0, y0, x1, y1) -> true;
    private static final SatchelApproach.Sight WALLED = (x0, y0, x1, y1) -> false;
    /** A route running east along y = 0 from x = 10 to x = 30, a nest standing at (20, 10). */
    private static final List<int[]> ROUTE = SatchelApproach.route(10, 0, 30, 0);
    private static final int[] NEST = {20, 10};

    private static Reading read(boolean live, SatchelApproach.Sight sight, boolean engaged, List<Cloud> clouds, int tick) {
        return SatchelApproach.read(3, NEST, live, ROUTE, REACH, sight, engaged, clouds, tick, 0);
    }

    // ------------------------------------------------------------------------------------------ the four answers

    @Test
    void aNestBelievedDeadIsAnsweredWhateverElseIsTrue() {
        assertEquals(Answer.DEAD, read(false, OPEN, false, List.of(), 0).answer());
    }

    @Test
    void aNestWhoseSightOfTheRouteIsBlockedIsHiddenGroundNotAThreat() {
        assertEquals(Answer.HIDDEN, read(true, WALLED, false, List.of(), 0).answer());
    }

    @Test
    void aNestOutOfReachOfEveryCellOfTheRouteIsHiddenToo() {
        Reading far = SatchelApproach.read(0, new int[] {20, 200}, true, ROUTE, REACH, OPEN, false, List.of(), 0, 0);
        assertEquals(Answer.HIDDEN, far.answer());
    }

    @Test
    void aNestThatSeesTheRouteAndIsEngagedIsSuppressed() {
        assertEquals(Answer.SUPPRESSED, read(true, OPEN, true, List.of(), 0).answer());
    }

    @Test
    void aNestThatSeesTheRouteWithNothingAgainstItIsOpen() {
        assertTrue(read(true, OPEN, false, List.of(), 0).open());
    }

    @Test
    void smokeOnTheLineFromTheNestToEveryExposedCellBlindsIt() {
        // A cloud on the line between the nest and the route, wide enough to cover every segment to the route.
        List<Cloud> cloud = List.of(new Cloud(20f, 5f, 12f, 100));
        assertEquals(Answer.BLINDED, read(true, OPEN, false, cloud, 50).answer());
    }

    @Test
    void smokeThatLeavesAnExposedCellUncoveredDoesNotBlindTheNest() {
        List<Cloud> narrow = List.of(new Cloud(20f, 5f, 1f, 100));
        assertTrue(read(true, OPEN, false, narrow, 50).open(), "the cells away from the cloud's shadow are still in the nest's sight");
    }

    @Test
    void smokeThatHasThinnedOutBlindsNothing() {
        List<Cloud> gone = List.of(new Cloud(20f, 5f, 12f, 100));
        assertTrue(read(true, OPEN, false, gone, 100).open());
    }

    @Test
    void answersAreTakenInOrderDeadBeforeHiddenBeforeBlindedBeforeSuppressed() {
        List<Cloud> cloud = List.of(new Cloud(20f, 5f, 12f, 100));
        assertEquals(Answer.BLINDED, read(true, OPEN, true, cloud, 0).answer());
        assertEquals(Answer.HIDDEN, read(true, WALLED, true, cloud, 0).answer());
        assertEquals(Answer.DEAD, read(false, WALLED, true, cloud, 0).answer());
    }

    @Test
    void theExposedCellsAreOnlyThoseInReachAndSight() {
        // The nest at (20,10): cells with |dx| beyond sqrt(24^2 - 10^2) = 21.8 are out of reach, none here; the cells of a short route all count.
        assertEquals(ROUTE.size(), SatchelApproach.exposed(NEST, ROUTE, REACH, OPEN).size());
        assertEquals(0, SatchelApproach.exposed(NEST, ROUTE, REACH, WALLED).size());
        assertEquals(0, SatchelApproach.exposed(NEST, ROUTE, 5, OPEN).size());
    }

    @Test
    void aWalkedPathIsSampledAtTheRouteStepWithItsLastCellKept() {
        int[] path = {0, 0, 1, 0, 2, 0, 3, 0, 4, 0};
        List<int[]> cells = SatchelApproach.routeOf(path);
        assertEquals(0, cells.get(0)[0]);
        assertEquals(2, cells.get(1)[0]);
        assertEquals(4, cells.get(cells.size() - 1)[0]);
    }

    @Test
    void aCloudThatWillNotLastTheCrossingBlindsNothing() {
        List<Cloud> cloud = List.of(new Cloud(20f, 5f, 12f, 100));
        assertTrue(SatchelApproach.read(3, NEST, true, ROUTE, REACH, OPEN, false, cloud, 50, 50).answer() == Answer.BLINDED);
        assertTrue(SatchelApproach.read(3, NEST, true, ROUTE, REACH, OPEN, false, cloud, 50, 51).open(),
                "one tick short of the crossing and the sapper walks out from under it");
    }

    @Test
    void screenPointsPutACloudOnTheLineToEveryExposedCell() {
        List<int[]> exposed = SatchelApproach.exposed(NEST, ROUTE, REACH, OPEN);
        List<int[]> points = SatchelApproach.screenPoints(NEST, exposed, 2.25);
        assertFalse(points.isEmpty());
        List<Cloud> clouds = new java.util.ArrayList<>();
        for (int[] p : points) clouds.add(new Cloud(p[0] + 0.5f, p[1] + 0.5f, 2.25f, 1000));
        assertTrue(SatchelApproach.blinded(NEST, exposed, clouds, 0, 0), "the clouds the plan asks for do screen the route");
        assertTrue(points.size() < exposed.size(), "a cloud covers several cells, or smoke would be a grenade a step");
    }

    @Test
    void smokeIsTheChoiceWhenItIsTheOnlyCheaperThingThanTheAir() {
        Plan plan = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.of(Enabler.SMOKE, Enabler.STRIKE), EnumSet.noneOf(Enabler.class)));
        assertEquals(List.of(new Move(3, Enabler.SMOKE)), plan.moves());
    }

    // ------------------------------------------------------------------------------------------ the plan

    /** A company that has some enablers on some nests and some already in motion. */
    private record Kit(Set<Enabler> available, Set<Enabler> inMotion) implements SatchelApproach.Resources {
        @Override public boolean can(Enabler enabler, int nest) { return available.contains(enabler); }

        @Override public boolean pending(Enabler enabler, int nest) { return inMotion.contains(enabler); }
    }

    private static final List<Reading> ONE_OPEN = List.of(new Reading(3, null), new Reading(4, Answer.DEAD));

    @Test
    void withNothingOpenTheApproachIsSurvivableAndNothingIsAsked() {
        Plan plan = SatchelApproach.plan(List.of(new Reading(1, Answer.DEAD), new Reading(2, Answer.HIDDEN),
                new Reading(3, Answer.SUPPRESSED), new Reading(4, Answer.BLINDED)), new Kit(EnumSet.allOf(Enabler.class), EnumSet.noneOf(Enabler.class)));
        assertEquals(Verdict.GO, plan.verdict());
        assertTrue(plan.moves().isEmpty());
    }

    @Test
    void anOpenNestMakesTheCompanyAskForTheCheapestEnablerItHas() {
        Plan all = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.allOf(Enabler.class), EnumSet.noneOf(Enabler.class)));
        assertEquals(Verdict.ENABLING, all.verdict());
        assertEquals(List.of(new Move(3, Enabler.ENGAGE)), all.moves());
        Plan noBase = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.of(Enabler.SMOKE, Enabler.STRIKE), EnumSet.noneOf(Enabler.class)));
        assertEquals(List.of(new Move(3, Enabler.SMOKE)), noBase.moves(), "smoke is cheaper than a strike");
        Plan airOnly = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.of(Enabler.STRIKE), EnumSet.noneOf(Enabler.class)));
        assertEquals(List.of(new Move(3, Enabler.STRIKE)), airOnly.moves());
    }

    @Test
    void anEnablerAlreadyInMotionIsWaitedOnNotAskedForAgain() {
        Plan plan = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.allOf(Enabler.class), EnumSet.of(Enabler.STRIKE)));
        assertEquals(Verdict.ENABLING, plan.verdict());
        assertTrue(plan.moves().isEmpty());
        assertEquals(List.of(3), plan.pending());
    }

    @Test
    void withNothingAvailableAnOpenNestHoldsTheSappers() {
        Plan plan = SatchelApproach.plan(ONE_OPEN, new Kit(EnumSet.noneOf(Enabler.class), EnumSet.noneOf(Enabler.class)));
        assertEquals(Verdict.HOLD, plan.verdict());
        assertEquals(List.of(3), plan.unservable());
        assertTrue(plan.moves().isEmpty());
    }

    @Test
    void answeredCountsEveryNestThatIsNoLongerOpen() {
        assertEquals(1, SatchelApproach.answered(ONE_OPEN));
    }

    // ------------------------------------------------------------------------------------------ the bound

    @Test
    void thePatienceIsTheSuppressionWindowPlusOneStrikeFlight() {
        assertEquals(FortAssault.SUPPRESS_MAX_TICKS + FortAssault.STRIKE_FLIGHT_TICKS, SatchelApproach.PATIENCE_TICKS);
    }

    @Test
    void patienceIsSpentOnlyAfterTheWholeBoundWithNoProgress() {
        assertFalse(SatchelApproach.patienceSpent(100, 100 + SatchelApproach.PATIENCE_TICKS - 1));
        assertTrue(SatchelApproach.patienceSpent(100, 100 + SatchelApproach.PATIENCE_TICKS));
        assertFalse(SatchelApproach.patienceSpent(-1, 100000), "nothing held, nothing to be patient about");
    }

    @Test
    void aRouteAlwaysEndsAtItsDestination() {
        List<int[]> route = SatchelApproach.route(0, 0, 7, 0);
        int[] last = route.get(route.size() - 1);
        assertEquals(7, last[0]);
        assertNull(route.stream().filter(c -> c[0] < 0).findFirst().orElse(null));
    }
}
