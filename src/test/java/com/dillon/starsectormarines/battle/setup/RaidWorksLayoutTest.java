package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.ops.OperationTier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** How a disrupting raid's works chooses its machines ({@code raid-mission.md}, Break). */
class RaidWorksLayoutTest {

    /** A works compound's machines are its own depots, nearest its seed first; homes and far depots are not. */
    @Test
    void aCompoundsHallsAreItsNearestDepots() {
        List<PointOfInterest> pois = List.of(
                poi(PointOfInterest.Kind.DEPOT, 120, 50),
                poi(PointOfInterest.Kind.RESIDENTIAL, 101, 50),
                poi(PointOfInterest.Kind.DEPOT, 105, 50),
                poi(PointOfInterest.Kind.ADMINISTRATIVE, 102, 50),
                poi(PointOfInterest.Kind.DEPOT, 100 + RaidWorksLayout.WORKS_REACH + 5, 50));
        List<int[]> halls = RaidWorksLayout.compoundHalls(pois, 100, 50);
        assertEquals(2, halls.size());
        assertArrayEquals(new int[]{105, 50}, halls.get(0));
        assertArrayEquals(new int[]{120, 50}, halls.get(1));
    }

    /** The machine houses lead the compound's other depots, however much nearer the seed the others stand. */
    @Test
    void aCompoundsMachineHousesLeadItsOtherDepots() {
        PointOfInterest stores = poi(PointOfInterest.Kind.DEPOT, 103, 50);
        PointOfInterest machine = poi(PointOfInterest.Kind.DEPOT, 115, 50);
        List<int[]> halls = RaidWorksLayout.compoundHalls(List.of(stores, machine), 100, 50, poi -> poi == machine);
        assertArrayEquals(new int[]{115, 50}, halls.get(0));
        assertArrayEquals(new int[]{103, 50}, halls.get(1));
    }

    private static PointOfInterest poi(PointOfInterest.Kind kind, int x, int y) {
        return new PointOfInterest(kind, x - 2, y - 2, x + 2, y + 2, x, y + 3, x, y);
    }

    @Test
    void machinesAreTakenInCandidateOrderUpToTheCount() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{20, 0}, new int[]{40, 0},
                new int[]{60, 0}), 3, 7, cell -> true, List.of());
        assertEquals(3, picked.size());
        assertArrayEquals(new int[]{40, 0}, picked.get(2));
    }

    @Test
    void twoMachinesNeverShareAPocket() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{3, 3}, new int[]{10, 0}),
                3, 7, cell -> true, List.of());
        assertEquals(2, picked.size());
        assertArrayEquals(new int[]{10, 0}, picked.get(1));
    }

    @Test
    void anUnusableCellIsSkipped() {
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{0, 0}, new int[]{20, 0}), 2, 7,
                cell -> cell[0] != 0, List.of());
        assertEquals(1, picked.size());
        assertArrayEquals(new int[]{20, 0}, picked.get(0));
    }

    @Test
    void aFallbackKeepsClearOfMachinesAlreadyChosen() {
        List<int[]> chosen = List.of(new int[]{0, 0});
        List<int[]> picked = RaidWorksLayout.pick(List.of(new int[]{2, 0}, new int[]{30, 0}), 3, 7,
                cell -> true, chosen);
        assertEquals(2, picked.size());
        assertArrayEquals(new int[]{30, 0}, picked.get(1));
    }

    @Test
    void aBiggerRaidHasMoreMachines() {
        assertTrue(RaidWorksLayout.machinesFor(OperationTier.FIRST_CONTRACT)
                < RaidWorksLayout.machinesFor(OperationTier.ESTABLISHED));
        assertTrue(RaidWorksLayout.machinesFor(OperationTier.VETERAN)
                < RaidWorksLayout.machinesFor(OperationTier.FULL_STRENGTH));
    }
}
