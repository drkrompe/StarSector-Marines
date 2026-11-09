package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Layer;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.LayerKind;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Way;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.WayKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delve's laws on a 40x20 ground with one wall line at x=20: the vault is reached through the layer's ways, and
 * at the larger grades a layer whose ways all funnel through one gap closes the route when that gap is blocked.
 */
class DelveCheckTest {

    static final int W = 40, H = 20, LINE = 20;

    @Test
    void aLineWithTwoSeparateWaysKeepsTheRoute() {
        NavigationGrid grid = ground();
        wallLine(grid, 4, 15);
        assertEquals(List.of(), DelveCheck.problems(grid, delve(way(1, 4), way(2, 15)), RuinsGroundPlan.forLayers(1)));
    }

    @Test
    void aWayThatIsNotOpenGroundIsNotCrossable() {
        NavigationGrid grid = ground();
        wallLine(grid, 4);
        List<String> problems = DelveCheck.problems(grid, delve(way(1, 4), way(2, 15)), RuinsGroundPlan.forLayers(1));
        assertTrue(problems.contains("ruins.layer.1.way.2 is not crossable"), problems.toString());
    }

    @Test
    void twoWaysIntoOneChokeCloseTheRouteWhenOneIsBlocked() {
        NavigationGrid grid = ground();
        wallLine(grid, 4, 15);
        // Behind the line, a second wall whose only gap stands behind way 1, and a cell closing the lane between the
        // two walls: way 2 crosses into a dead end, so the vault is reached through way 1 alone.
        for (int y = 0; y < H; y++) if (y != 4) grid.setWalkable(LINE + 2, y, false);
        grid.setWalkable(LINE + 1, 9, false);
        List<String> problems = DelveCheck.problems(grid, delve(way(1, 4), way(2, 15)), RuinsGroundPlan.forLayers(1));
        assertTrue(problems.contains("blocking ruins.layer.1.way.1 closes the route"), problems.toString());
    }

    static NavigationGrid ground() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    /** The layer line at {@link #LINE}, open only on {@code gaps} rows. */
    static void wallLine(NavigationGrid grid, int... gaps) {
        for (int y = 0; y < H; y++) {
            boolean gap = false;
            for (int g : gaps) gap |= g == y;
            if (!gap) grid.setWalkable(LINE, y, false);
        }
    }

    static Way way(int number, int y) {
        return new Way("ruins.layer.1.way." + number, WayKind.GAP, new Area(LINE, y, LINE, y),
                new Cell(LINE - 3, y), new Cell(LINE + 1, y));
    }

    /** One layer with {@code ways}; the vault's source on the far side, every other place on the near side. */
    static RuinsDelve delve(Way... ways) {
        LandingArea landing = new LandingArea("landing", LandingPad.civilian(3, 4, LandingPad.Approach.WEST),
                LandingPad.civilian(3, 12, LandingPad.Approach.WEST));
        Cell near = new Cell(10, 10);
        Area spot = new Area(10, 10, 10, 10);
        Layer layer = new Layer(RuinsDelve.layerName(0), 0, LayerKind.COLLAPSED_STREET, new Area(18, 0, 30, H - 1),
                LINE, LINE, List.of(ways), List.of(new RuinsDelve.SideRoom("room", spot, near, near)), null);
        RuinsDelve.Vault vault = new RuinsDelve.Vault(new Area(34, 8, 38, 12), new Cell(34, 10), new Cell(36, 10),
                new Cell(33, 10), new Cell(37, 10));
        Way collapse = new Way(RuinsDelve.COLLAPSE, WayKind.COLLAPSE, new Area(LINE, 10, LINE, 10),
                new Cell(LINE - 1, 10), new Cell(LINE + 1, 10));
        return new RuinsDelve(1, new Area(0, 0, 6, H - 1), List.of(landing), vault, List.of(layer),
                new RuinsDelve.PowerHall(RuinsDelve.POWER_HALL, spot, near, near, 0),
                List.of(new RuinsDelve.Junction(RuinsDelve.junctionName(0), near, 0)),
                new RuinsDelve.RecordsTerminal(RuinsDelve.RECORDS, spot, near, near, 0),
                new RuinsDelve.Collapse(RuinsDelve.COLLAPSE, 0, collapse),
                new RuinsDelve.Interior(RuinsDelve.INTERIOR, new Area(32, 6, W - 1, 14), 1,
                        List.of(new Area(33, 10, 33, 10)), List.of()));
    }
}
