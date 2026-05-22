package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link KeepEntryChamberStamper}. Each test
 * builds a small grid with a known building geometry, registers a single
 * COMMAND_POST tactical node, runs the stamper, and asserts whether the
 * entry-chamber BARRACKS was emitted (and at the expected anchor).
 */
public class KeepEntryChamberStamperTest {

    private static final int W = 20;
    private static final int H = 20;

    /** All-walkable grid. Tests that need a wall partition stamp it explicitly. */
    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static TacticalNode commandPost(int left, int top, int right, int bottom,
                                            int anchorX, int anchorY) {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                anchorX, anchorY, left, top, right, bottom,
                Faction.DEFENDER, 95, 4);
    }

    @Test
    public void singleRoomBuildingSkipsEmission() {
        // No interior wall → flood from the COMMAND_POST anchor reaches
        // every walkable cell in the leaf bbox → no other-room cells
        // exist → no BARRACKS emitted.
        NavigationGrid grid = openGrid();
        TacticalNode cp = commandPost(8, 8, 12, 12, 10, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, tactical);

        assertEquals(1, tactical.size(),
                "single-room keep has no entry chamber to emit");
    }

    @Test
    public void multiRoomBuildingEmitsEntryChamberOnFarSide() {
        // Stamp a wall stripe across the middle of an 8x8 leaf with a
        // single-cell DOORWAY-flagged opening, simulating BuildingShellCore's
        // multi-room partition (cell stays walkable but is flagged as a
        // doorway — the stamper treats doorway cells as room boundaries so
        // the flood doesn't leak through them). COMMAND_POST anchor sits in
        // the upper (high-Y) half = throne room. The stamper should emit an
        // INNER_POSITION anchored in the lower (low-Y) half = entry chamber.
        NavigationGrid grid = openGrid();
        // 8x8 leaf bbox at (6,6)-(13,13). Wall row at y=10 with doorway
        // at x=10. Cells y=11..13 are throne room (anchor at (10,12));
        // cells y=6..9 are entry chamber.
        for (int x = 6; x <= 13; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        // Partition doorway: walkable + flagged. Mirrors what
        // BuildingShellCore.subdivide produces.
        grid.setDoorway(10, 10, true);
        TacticalNode cp = commandPost(6, 6, 13, 13, 10, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, tactical);

        assertEquals(2, tactical.size(),
                "multi-room keep should emit an entry-chamber INNER_POSITION");
        TacticalNode entry = tactical.get(1);
        assertEquals(TacticalNode.Kind.INNER_POSITION, entry.kind);
        assertTrue(entry.anchorY < 10,
                "entry-chamber anchor should sit in the low-Y half (opposite the COMMAND_POST anchor)");
        assertTrue(entry.anchorX >= 6 && entry.anchorX <= 13,
                "entry-chamber anchor must remain inside the leaf bbox");
    }

    @Test
    public void skipsCommandPostWithUnwalkableAnchor() {
        // Defensive case: COMMAND_POST anchor placed on a wall cell
        // (degenerate map-gen state). Stamper can't seed a flood, skips.
        NavigationGrid grid = openGrid();
        grid.setWalkable(10, 10, false);
        TacticalNode cp = commandPost(8, 8, 12, 12, 10, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, tactical);

        assertEquals(1, tactical.size(),
                "COMMAND_POST anchor on a wall must not crash; skip emission");
    }

    @Test
    public void ignoresNonCommandPostKinds() {
        // The stamper only walks COMMAND_POST nodes. A BARRACKS or
        // ARMORY in a multi-room building shouldn't get an extra
        // chamber emission — only the keep does.
        NavigationGrid grid = openGrid();
        for (int x = 6; x <= 13; x++) {
            if (x == 10) continue;
            grid.setWalkable(x, 10, false);
        }
        grid.setDoorway(10, 10, true);
        TacticalNode bx = new TacticalNode(TacticalNode.Kind.BARRACKS,
                10, 12, 6, 6, 13, 13, Faction.DEFENDER, 60, 4);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(bx);

        KeepEntryChamberStamper.stamp(grid, tactical);

        assertEquals(1, tactical.size(),
                "non-COMMAND_POST kinds must not get the chamber emission");
    }

    @Test
    public void skipsSubMinimumChamber() {
        // Two-cell pocket of unreached walkable cells is below the
        // MIN_CHAMBER_CELLS threshold — irregular geometry, not a
        // real chamber. Skip emission.
        NavigationGrid grid = openGrid();
        // 4x4 leaf, wall stripe across the middle leaves only 2 cells
        // on the entry side after the doorway accounts for one.
        // Actually simpler: small leaf where the unreached set is 2.
        // 4x3 leaf at (10,10)-(13,12): walls at (10,11) and (12,11),
        // doorway at (11,11) and (13,11). Anchor at (11,12). Unreached
        // = cells in the y=10 row that aren't connected back through
        // the doorways. Hmm, hard to set up without bigger geometry.
        //
        // Simpler synthetic: 6x3 leaf at (10,10)-(15,12). Wall row at
        // y=11 with doorway at x=12. Anchor at (12, 12). The y=10 row
        // has cells (10..15, 10) — six walkable cells, but two of them
        // are connected back through wall holes... actually let me
        // just isolate a 2-cell pocket directly.
        //
        // Use leaf bbox (10,10)-(13,11). The y=10 row has 4 cells; wall
        // out three of them so only one is walkable. The y=11 row stays
        // walkable. Anchor at (11, 11). Flood reaches the y=11 row but
        // not the single walkable y=10 cell (surrounded by walls).
        grid.setWalkable(10, 10, false);
        grid.setWalkable(11, 10, false);
        grid.setWalkable(13, 10, false);
        // (12, 10) stays walkable. y=11 stays fully walkable.
        // From anchor (11, 11), flood reaches (10..13, 11). (12, 10) is
        // isolated from the flood because (12, 11) only connects N to
        // (12, 10) — which IS walkable, so actually it gets reached.
        // Need to also wall off (12, 11) → (12, 10) the vertical edge.
        // The grid's setWalkable controls cell occupancy, not edge
        // openness — flood uses cell walkability, so adjacent walkable
        // cells are connected regardless of edge state.
        // To isolate (12, 10), also make (12, 11) unwalkable. But then
        // the flood crosses through nothing in that column.
        grid.setWalkable(12, 11, false);
        TacticalNode cp = commandPost(10, 10, 13, 11, 11, 11);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, tactical);

        assertEquals(1, tactical.size(),
                "1-cell unreached pocket is below MIN_CHAMBER_CELLS — must not emit");
    }
}
