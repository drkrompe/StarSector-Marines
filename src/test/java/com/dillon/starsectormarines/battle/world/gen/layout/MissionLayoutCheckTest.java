package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionLayoutCheckTest {

    /** A 20x10 field with a wire course across row 5, every cell closed except those in {@code open}. */
    private static NavigationGrid field(List<int[]> wire, int... open) {
        NavigationGrid grid = new NavigationGrid(20, 10);
        for (int x = 0; x < 20; x++) for (int y = 0; y < 10; y++) grid.setWalkable(x, y, true);
        for (int[] c : wire) grid.setWalkable(c[0], c[1], false);
        for (int x : open) grid.setWalkable(x, 5, true);
        return grid;
    }

    private static List<int[]> wire() {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < 20; x++) out.add(new int[]{x, 5});
        return out;
    }

    private static LaidLayout line(List<int[]> cells, List<int[]> gaps, boolean required, Laid.Status status) {
        return new LaidLayout(List.of(new Laid("picket", Laid.Kind.LINE, status, "", required, cells, gaps, List.of())));
    }

    @Test
    void aSealedWireCourseFails() {
        List<int[]> wire = wire();
        NavigationGrid grid = field(wire);
        List<String> problems = MissionLayoutCheck.problems(grid, line(wire, List.of(new int[]{9, 5}), false, Laid.Status.LAID));
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.get(1).contains("no way through"));
    }

    @Test
    void aFunnelLeftOpenPasses() {
        List<int[]> wire = wire();
        NavigationGrid grid = field(wire, 9, 10);
        assertEquals(List.of(), MissionLayoutCheck.problems(grid,
                line(wire, List.of(new int[]{9, 5}, new int[]{10, 5}), false, Laid.Status.LAID)));
    }

    @Test
    void aWalkableLineNeedsNoWayThrough() {
        NavigationGrid grid = field(List.of());
        assertEquals(List.of(), MissionLayoutCheck.problems(grid, line(wire(), List.of(), false, Laid.Status.PARTIAL)));
    }

    @Test
    void onlyARequiredPieceMayNotBeRefused() {
        NavigationGrid grid = field(List.of());
        assertEquals(List.of(), MissionLayoutCheck.problems(grid,
                new LaidLayout(List.of(Laid.refused("site", Laid.Kind.SITE, false, "no ground")))));
        assertEquals(1, MissionLayoutCheck.problems(grid,
                new LaidLayout(List.of(Laid.refused("site", Laid.Kind.SITE, true, "no ground")))).size());
    }
}
