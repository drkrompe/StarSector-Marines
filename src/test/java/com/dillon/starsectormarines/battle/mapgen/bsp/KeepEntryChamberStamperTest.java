package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.RoomPurpose;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link KeepEntryChamberStamper}. Each test
 * builds a small grid + topology, stamps {@link RoomPurpose} labels on
 * cells the way {@link com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingShellCore}
 * does at carve time, registers a single COMMAND_POST tactical node, runs
 * the stamper, and asserts whether an INNER_POSITION (the entry-chamber
 * anchor) was emitted at the expected cell.
 *
 * <p>Slice A of the three-chamber refactor: the stamper reads
 * {@link RoomPurpose} labels directly instead of inferring chambers via
 * the zone graph, so tests stamp labels onto cells rather than carving
 * walls + doorways and relying on zone-detector flood behavior.
 */
public class KeepEntryChamberStamperTest {

    private static final int W = 20;
    private static final int H = 20;

    /** All-walkable grid. */
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

    /** Stamp {@code purpose} across a rectangular region — what BuildingShellCore.labelRooms emits per chamber. */
    private static void labelRegion(CellTopology topology, RoomPurpose purpose,
                                    int l, int t, int r, int b) {
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                topology.setRoomPurpose(x, y, purpose);
            }
        }
    }

    @Test
    public void singleRoomBuildingSkipsEmission() {
        // Single-room COMMAND building — labelRooms stamps THRONE across the
        // whole interior, no ENTRY cells exist, stamper emits nothing.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 9, 9, 11, 11);
        TacticalNode cp = commandPost(8, 8, 12, 12, 10, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(1, tactical.size(),
                "single-room keep has no entry chamber to emit");
    }

    @Test
    public void multiRoomBuildingEmitsEntryChamberOnFarSide() {
        // 8×8 building bbox at (6,6)-(13,13) with a partition at y=10.
        // Throne side (y=11..12) labeled KEEP_THRONE; entry side (y=7..9)
        // labeled KEEP_ENTRY. COMMAND_POST anchor at (10, 12) sits in the
        // throne chamber. Stamper finds the labeled entry cells and emits
        // INNER_POSITION at their centroid (snapped to a real cell).
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 7, 11, 12, 12);
        labelRegion(topology, RoomPurpose.KEEP_ENTRY,  7,  7, 12,  9);
        TacticalNode cp = commandPost(6, 6, 13, 13, 10, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

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
    public void emitsNothingWhenBuildingHasNoLabels() {
        // Defensive case — COMMAND_POST exists but no carver labeled the
        // building (legacy callers, non-keep buildings repurposed as
        // COMMAND_POST, degenerate map-gen state). Stamper must not crash,
        // must not emit.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        TacticalNode cp = commandPost(8, 8, 12, 12, 10, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(1, tactical.size(),
                "unlabeled building must not crash; skip emission");
    }

    @Test
    public void ignoresNonCommandPostKinds() {
        // The stamper only walks COMMAND_POST nodes. A BARRACKS or ARMORY
        // with KEEP_ENTRY labels in its bbox (hypothetical) shouldn't get
        // an extra INNER_POSITION emission — only the keep does.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_ENTRY, 7, 7, 12, 9);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 7, 11, 12, 12);
        TacticalNode bx = new TacticalNode(TacticalNode.Kind.BARRACKS,
                10, 12, 6, 6, 13, 13, Faction.DEFENDER, 60, 4);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(bx);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(1, tactical.size(),
                "non-COMMAND_POST kinds must not get the chamber emission");
    }

    @Test
    public void verticalPartitionEntryChamberAlsoEmits() {
        // Vertical-partition mirror of the horizontal-partition coverage —
        // pins that the stamper isn't axis-biased. Throne side at x=10..12
        // (where COMMAND_POST anchor lives), entry side at x=7..9.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 10,  7, 12, 12);
        labelRegion(topology, RoomPurpose.KEEP_ENTRY,   7,  7,  9, 12);
        TacticalNode cp = commandPost(6, 6, 13, 13, 11, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(2, tactical.size(),
                "vertical-partition keep should emit an entry-chamber INNER_POSITION");
        TacticalNode entry = tactical.get(1);
        assertEquals(TacticalNode.Kind.INNER_POSITION, entry.kind);
        assertTrue(entry.anchorX < 10,
                "entry-chamber anchor should sit in the low-X half (opposite the COMMAND_POST anchor)");
        assertTrue(entry.anchorY >= 6 && entry.anchorY <= 13,
                "entry-chamber anchor must remain inside the leaf bbox");
    }

    @Test
    public void labeledCellTurnedNonWalkableIsExcluded() {
        // Defensive: a labeled KEEP_ENTRY cell that a later pass made non-
        // walkable (hypothetical FortressWallStamper / DefensePostStamper
        // mutation) must be skipped by the centroid calculation. Without
        // the re-check the centroid would drift onto a wall cell.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 7, 11, 12, 12);
        labelRegion(topology, RoomPurpose.KEEP_ENTRY,  7,  7, 12,  9);
        // Make one labeled entry cell non-walkable post-hoc.
        grid.setWalkable(8, 8, false);
        TacticalNode cp = commandPost(6, 6, 13, 13, 10, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(2, tactical.size(),
                "remaining walkable entry cells still form a valid chamber");
        TacticalNode entry = tactical.get(1);
        assertTrue(grid.isWalkable(entry.anchorX, entry.anchorY),
                "entry anchor must land on a walkable cell (the non-walkable labeled cell was filtered)");
    }

    @Test
    public void threeChamberKeepEmitsTwoPositions() {
        // Three chambers: THRONE (x=10..14), INNER (x=7..9), ENTRY (x=3..6).
        // Anchor at (12, 10) sits in THRONE. Stamper should emit one
        // INNER_POSITION per non-throne chamber (INNER + ENTRY = 2 nodes).
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 10,  7, 14, 12);
        labelRegion(topology, RoomPurpose.KEEP_INNER,   7,  7,  9, 12);
        labelRegion(topology, RoomPurpose.KEEP_ENTRY,   3,  7,  6, 12);
        TacticalNode cp = commandPost(2, 6, 15, 13, 12, 10);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(3, tactical.size(),
                "3-chamber keep should emit COMMAND_POST + 2 INNER_POSITIONs");
        TacticalNode inner = tactical.get(1);
        TacticalNode entry = tactical.get(2);
        assertEquals(TacticalNode.Kind.INNER_POSITION, inner.kind);
        assertEquals(TacticalNode.Kind.INNER_POSITION, entry.kind);
        // INNER emitted first (priority 65) — its anchor should be in x=7..9
        assertTrue(inner.anchorX >= 7 && inner.anchorX <= 9,
                "inner anchor x=" + inner.anchorX + " should be in INNER chamber [7..9]");
        // ENTRY emitted second (priority 60) — its anchor should be in x=3..6
        assertTrue(entry.anchorX >= 3 && entry.anchorX <= 6,
                "entry anchor x=" + entry.anchorX + " should be in ENTRY chamber [3..6]");
        assertTrue(inner.priorityScore > entry.priorityScore,
                "INNER priority (" + inner.priorityScore + ") should exceed ENTRY (" + entry.priorityScore + ")");
    }

    @Test
    public void skipsSubMinimumChamber() {
        // Entry chamber has only 2 labeled cells (below MIN_CHAMBER_CELLS=3).
        // Even though the labels exist, the chamber's too small to read as a
        // real room and the stamper skips emission.
        NavigationGrid grid = openGrid();
        CellTopology topology = new CellTopology(W, H);
        labelRegion(topology, RoomPurpose.KEEP_THRONE, 9, 11, 11, 11);
        // Only 2 entry-chamber cells.
        topology.setRoomPurpose(10, 9, RoomPurpose.KEEP_ENTRY);
        topology.setRoomPurpose(11, 9, RoomPurpose.KEEP_ENTRY);
        TacticalNode cp = commandPost(8, 8, 12, 12, 10, 11);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(cp);

        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        assertEquals(1, tactical.size(),
                "2-cell entry chamber is below MIN_CHAMBER_CELLS — must not emit");
    }
}
