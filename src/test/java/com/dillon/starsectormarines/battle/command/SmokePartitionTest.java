package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.SatchelApproach.Cloud;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Refusal;
import com.dillon.starsectormarines.battle.command.SatchelApproach.Reliance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a screen cuts, and the two edges of laying one: it blinds the gun and it blinds the side that laid it. */
class SmokePartitionTest {

    private static final SatchelApproach.Sight OPEN = (x0, y0, x1, y1) -> true;
    private static final int LIFE = 3000;

    /** A screen across x = 20, three cells either side of y = 0. */
    private static Cloud wall(int untilTick) {
        return new Cloud(20.5f, 0.5f, 3f, untilTick);
    }

    // ------------------------------------------------------------------------------------------ the partition fact

    @Test
    void aScreenCutsSightBothWaysAndNothingBesideIt() {
        SmokePartition partition = new SmokePartition(List.of(wall(LIFE)));
        int[] west = {10, 0};
        int[] east = {30, 0};
        assertTrue(partition.cuts(west, east, 0, 0));
        assertTrue(partition.cuts(east, west, 0, 0), "smoke blocks for the side that laid it as for the side it was laid against");
        assertFalse(partition.cuts(new int[] {10, 12}, new int[] {30, 12}, 0, 0), "a line well clear of the cloud is not cut");
        assertFalse(partition.cuts(new int[] {10, 0}, new int[] {16, 0}, 0, 0), "a line that stops short of it is not cut");
    }

    @Test
    void aScreenOnlyCountsForTheStretchItHoldsItsRadius() {
        SmokePartition partition = new SmokePartition(List.of(wall(400)));
        int[] a = {10, 0};
        int[] b = {30, 0};
        assertTrue(partition.cuts(a, b, 0, 300));
        assertFalse(partition.cuts(a, b, 0, 401), "wanted for longer than it will hold");
        assertFalse(partition.cuts(a, b, 400, 0), "gone");
    }

    @Test
    void theCutPairsAreExactlyTheSightLinesAcrossTheScreen() {
        SmokePartition partition = new SmokePartition(List.of(wall(LIFE)));
        List<int[]> observers = List.of(new int[] {10, 0}, new int[] {10, 14});
        List<int[]> subjects = List.of(new int[] {30, 0}, new int[] {30, 14});
        List<int[]> cut = partition.cutPairs(observers, subjects, 0, 0);
        assertEquals(1, cut.size());
        assertEquals(0, cut.get(0)[0]);
        assertEquals(0, cut.get(0)[1]);
    }

    @Test
    void aSubjectIsIsolatedOnlyWhenEveryObserverIsCutFromIt() {
        SmokePartition partition = new SmokePartition(List.of(wall(LIFE)));
        int[] beyond = {30, 0};
        assertTrue(partition.isolates(List.of(new int[] {10, 0}, new int[] {5, 1}), beyond, 0, 0));
        assertFalse(partition.isolates(List.of(new int[] {10, 0}, new int[] {10, 14}), beyond, 0, 0));
        assertFalse(partition.isolates(List.of(), beyond, 0, 0), "nobody to be cut from is not isolation");
    }

    @Test
    void theFactBuildsFromTheSmokeAsTheSideSeesIt() {
        SmokePartition partition = SmokePartition.of(List.of(new SmokeCloudFact(20.5f, 0.5f, 3f, 60f)), 100, 30);
        assertTrue(partition.cuts(new int[] {10, 0}, new int[] {30, 0}, 100, 1800));
        assertFalse(partition.cuts(new int[] {10, 0}, new int[] {30, 0}, 100, 1801));
    }

    // ------------------------------------------------------------------------------------------ the planner's weighing

    /** Fire from (4, 20) is suppressing the nest at (40, 20); a different nest is the one to screen. */
    private static final int[] SUPPRESSED_NEST = {40, 20};
    private static final int[] BASE = {4, 20};
    private static final List<Reliance> RELIANCE = List.of(new Reliance(1, SUPPRESSED_NEST, BASE));

    @Test
    void aScreenAcrossTheLineOfAFireTheCompanyReliesOnIsRefused() {
        List<Cloud> across = List.of(new Cloud(22.5f, 20.5f, 2.25f, LIFE));
        Refusal refusal = SatchelApproach.weigh(0, List.of(), across, RELIANCE, List.of(BASE), List.of(), 24, OPEN, 0, 600);
        assertEquals(Refusal.BLINDS_BASE_OF_FIRE, refusal);
    }

    @Test
    void aScreenClearOfTheBaseOfFireIsLaid() {
        List<Cloud> clear = List.of(new Cloud(36.5f, 6.5f, 2.25f, LIFE));
        assertNull(SatchelApproach.weigh(0, List.of(), clear, RELIANCE, List.of(BASE), List.of(new int[] {30, 0}), 24, OPEN, 0, 600));
    }

    @Test
    void theNestTheScreenIsLaidAgainstIsNotARelianceOfItsOwn() {
        List<Cloud> across = List.of(new Cloud(22.5f, 20.5f, 2.25f, LIFE));
        // The same line, but the screen is for the nest that fire is on: the screen replaces the suppression.
        assertNull(SatchelApproach.weigh(1, List.of(), across, RELIANCE, List.of(BASE), List.of(), 24, OPEN, 0, 600));
    }

    @Test
    void aCutAlreadyStandingIsNotTheProposalsDoing() {
        List<Cloud> already = List.of(new Cloud(22.5f, 20.5f, 2.25f, LIFE));
        List<Cloud> more = List.of(new Cloud(24.5f, 20.5f, 2.25f, LIFE));
        assertNull(SatchelApproach.weigh(0, already, more, RELIANCE, List.of(BASE), List.of(), 24, OPEN, 0, 600));
    }

    @Test
    void aScreenThatLeavesTheSappersAloneBeyondItWhileASuppressedNestStillSeesThemIsRefused() {
        int[] place = {30, 0};
        int[] watcher = {34, -10};
        List<Reliance> reliance = List.of(new Reliance(2, watcher, new int[] {4, 2}));
        List<int[]> support = List.of(new int[] {4, 2}, new int[] {4, 6});
        // The cloud stands between every element of the base of fire and the place, and off the line to the watcher.
        List<Cloud> between = List.of(new Cloud(16.5f, 3.5f, 5f, LIFE));
        assertEquals(Refusal.STRANDS_SAPPERS,
                SatchelApproach.weigh(0, List.of(), between, reliance, support, List.of(place), 24, OPEN, 0, 600));
    }

    @Test
    void sappersLeftInSightOfAnyElementOfTheBaseOfFireAreNotStranded() {
        int[] place = {30, 0};
        List<Reliance> reliance = List.of(new Reliance(2, new int[] {34, -10}, new int[] {4, 2}));
        List<int[]> support = List.of(new int[] {4, 2}, new int[] {40, 30});
        List<Cloud> between = List.of(new Cloud(16.5f, 3.5f, 5f, LIFE));
        assertNull(SatchelApproach.weigh(0, List.of(), between, reliance, support, List.of(place), 24, OPEN, 0, 600));
    }

    @Test
    void strandingMattersOnlyWhileASuppressedNestCanSeeThePlace() {
        int[] place = {30, 0};
        List<Reliance> reliance = List.of(new Reliance(2, new int[] {34, -10}, new int[] {4, 2}));
        List<int[]> support = List.of(new int[] {4, 2});
        List<Cloud> between = List.of(new Cloud(16.5f, 3.5f, 5f, LIFE));
        SatchelApproach.Sight blind = (x0, y0, x1, y1) -> false;
        assertNull(SatchelApproach.weigh(0, List.of(), between, reliance, support, List.of(place), 24, blind, 0, 600),
                "a nest that cannot see the wall is no reason to keep the sappers in sight of the fire");
    }
}
