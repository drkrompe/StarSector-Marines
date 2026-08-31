package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Synthetic-grid coverage for {@link CompoundPerimeterDefenderStamper}. Each
 * test builds a small walkable grid + a single compound tactical node at a
 * known bbox, runs the stamper, and asserts the emitted GUARDPOST anchor
 * sits on the attacker-facing side of the compound.
 */
public class CompoundPerimeterDefenderStamperTest {

    private static final int W = 20;
    private static final int H = 20;

    /** Walkable grid the same shape every test starts with. */
    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    /**
     * Drive the stamper as a {@link GenStage}: bind {@code axis} under
     * {@link BspKeys#AXIS} (skipped for the null-axis legacy case), seed
     * {@code ctx.tactical} from the caller's nodes, run, then mirror the result
     * back so the assertions read {@code tactical} like the old static call did.
     */
    private static void runStamper(NavigationGrid grid, TraversalAxis axis, List<TacticalNode> tactical) {
        GenContext ctx = new GenContext(grid, new CellTopology(W, H), new Random(0), W, H, 0L);
        if (axis != null) ctx.put(BspKeys.AXIS, axis);
        ctx.tactical.addAll(tactical);
        new CompoundPerimeterDefenderStamper().run(ctx);
        tactical.clear();
        tactical.addAll(ctx.tactical);
    }

    /** As {@link #runStamper}, with a reservation mask bound. */
    private static void runStamper(NavigationGrid grid, TraversalAxis axis,
                                   List<TacticalNode> tactical, boolean[][] reserved) {
        GenContext ctx = new GenContext(grid, new CellTopology(W, H), new Random(0), W, H, 0L);
        if (axis != null) ctx.put(BspKeys.AXIS, axis);
        ctx.put(BspKeys.ROAD_RESERVATION, reserved);
        ctx.tactical.addAll(tactical);
        new CompoundPerimeterDefenderStamper().run(ctx);
        tactical.clear();
        tactical.addAll(ctx.tactical);
    }

    /** Compound node anchored at the bbox centroid with a (left, top)-(right, bottom) footprint. defaultGuard = DEFENDER mirrors what {@link com.dillon.starsectormarines.battle.world.gen.bsp.fill.MilitaryBaseFiller} emits. */
    private static TacticalNode compoundNode(TacticalNode.Kind kind,
                                             int left, int top, int right, int bottom) {
        int ax = (left + right) / 2;
        int ay = (top + bottom) / 2;
        return new TacticalNode(kind, ax, ay, left, top, right, bottom,
                Faction.DEFENDER, 60, 4);
    }

    @Test
    public void emitsGuardpostOnSouthEdgeForSouthToNorthAxis() {
        // SOUTH_TO_NORTH: attacker comes from the south (low Y). The
        // attacker-facing edge of the compound is its min-Y side
        // (node.top). The stamper picks a walkable cell one cell beyond
        // that edge.
        NavigationGrid grid = openGrid();
        TacticalNode compound = compoundNode(TacticalNode.Kind.BARRACKS,
                8, 8, 12, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compound);

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical);

        assertEquals(2, tactical.size(), "one GUARDPOST should have been appended");
        TacticalNode guardpost = tactical.get(1);
        assertEquals(TacticalNode.Kind.GUARDPOST, guardpost.kind);
        // mid-X of the compound bbox, one cell below top (lower Y).
        assertEquals(10, guardpost.anchorX);
        assertEquals(7, guardpost.anchorY);
    }

    @Test
    public void emitsGuardpostOnWestEdgeForWestToEastAxis() {
        // WEST_TO_EAST: attacker comes from the west (low X). The
        // attacker-facing edge is min-X (node.left).
        NavigationGrid grid = openGrid();
        TacticalNode compound = compoundNode(TacticalNode.Kind.COMMAND_POST,
                8, 8, 12, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compound);

        runStamper(grid, TraversalAxis.WEST_TO_EAST, tactical);

        assertEquals(2, tactical.size());
        TacticalNode guardpost = tactical.get(1);
        assertEquals(TacticalNode.Kind.GUARDPOST, guardpost.kind);
        // One cell west of left edge, mid-Y of the bbox.
        assertEquals(7, guardpost.anchorX);
        assertEquals(10, guardpost.anchorY);
    }

    @Test
    public void emitsOneGuardpostPerCompoundKind() {
        // Three compound kinds in one list — stamper emits one GUARDPOST
        // each, leaving the original nodes untouched.
        NavigationGrid grid = openGrid();
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compoundNode(TacticalNode.Kind.BARRACKS,    4, 4,  6,  6));
        tactical.add(compoundNode(TacticalNode.Kind.ARMORY,      4, 10, 6, 12));
        tactical.add(compoundNode(TacticalNode.Kind.COMMAND_POST, 12, 4, 14, 6));

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical);

        assertEquals(6, tactical.size(), "three compounds → three GUARDPOSTs appended");
        int guardposts = 0;
        for (TacticalNode n : tactical) {
            if (n.kind == TacticalNode.Kind.GUARDPOST) guardposts++;
        }
        assertEquals(3, guardposts);
    }

    @Test
    public void skipsNonCompoundKinds() {
        // The stamper only looks at COMMAND_POST/BARRACKS/ARMORY. Other
        // defender nodes (HEAVY_TOWER, GUARDPOST itself, etc.) should
        // not get a perimeter GUARDPOST emitted next to them.
        NavigationGrid grid = openGrid();
        List<TacticalNode> tactical = new ArrayList<>();
        TacticalNode tower = new TacticalNode(TacticalNode.Kind.HEAVY_TOWER,
                10, 10, 9, 9, 11, 11, Faction.DEFENDER, 80, 3);
        TacticalNode existingGuard = new TacticalNode(TacticalNode.Kind.GUARDPOST,
                15, 15, 15, 15, 15, 15, Faction.DEFENDER, 60, 2);
        tactical.add(tower);
        tactical.add(existingGuard);

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical);

        assertEquals(2, tactical.size(), "no compound nodes → no new GUARDPOSTs emitted");
    }

    @Test
    public void skipsCompoundWithNoWalkableApproach() {
        // A compound whose attacker-facing edge butts into a wall band
        // shouldn't get a GUARDPOST — there's no walkable approach for
        // the lookout to anchor on. Better to skip than crash; the
        // compound's own garrison still defends without the lookout.
        NavigationGrid grid = openGrid();
        // Wall off everything south of y=8 so a compound at (8, 8)-(12, 12)
        // has no walkable cell within OUTSIDE_SCAN_DEPTH of its top edge.
        for (int y = 0; y <= 7; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkable(x, y, false);
            }
        }
        TacticalNode compound = compoundNode(TacticalNode.Kind.BARRACKS,
                8, 8, 12, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compound);

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical);

        assertEquals(1, tactical.size(),
                "no walkable cell on attacker-facing approach → no GUARDPOST emitted");
    }

    @Test
    public void skipsWhenAxisIsNull() {
        // Legacy non-Conquest maps still get MILITARY_BASE compounds (via
        // MapDistrictTheme) and pass through the same generator pipeline,
        // but with axis = null. The marine spawn on legacy is biased to
        // low-X, not aligned with either traversal-axis end, so picking an
        // attacker-facing edge from the axis would silently misplace the
        // lookout. Skip rather than guess — the visual corner emplacements
        // from MilitaryBaseFiller still defend the compound.
        NavigationGrid grid = openGrid();
        TacticalNode compound = compoundNode(TacticalNode.Kind.BARRACKS,
                8, 8, 12, 12);
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compound);

        runStamper(grid, null, tactical);

        assertEquals(1, tactical.size(),
                "null axis (legacy maps) must skip stamping — no attacker side known");
    }

    /**
     * A lookout does not stand on ground somebody else is relying on.
     *
     * <p>The anchor is the first <em>walkable</em> cell outside the compound's
     * attacker-facing edge, and a runway is open ground — so a compound beside
     * the ward airfield planted its guardpost on the apron. Measured across the
     * conquest matrix, thirty-three of forty-eight maps had a guardpost on the
     * airfield and this stamper accounted for thirty-five of the sixty-four.
     * The scan now steps over reserved ground rather than stopping on it.
     */
    @Test
    public void aReservedCellIsNotAPlaceToStand() {
        NavigationGrid grid = openGrid();
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compoundNode(TacticalNode.Kind.BARRACKS, 8, 8, 12, 12));

        // The two rows immediately south of the compound are spoken for.
        boolean[][] reserved = new boolean[W][H];
        for (int x = 0; x < W; x++) {
            reserved[x][7] = true;
            reserved[x][6] = true;
        }

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical, reserved);

        assertEquals(2, tactical.size(), "one GUARDPOST should have been appended");
        TacticalNode post = tactical.get(1);
        assertEquals(TacticalNode.Kind.GUARDPOST, post.kind);
        assertFalse(reserved[post.anchorX][post.anchorY],
                "the guardpost was planted on reserved ground at "
                        + post.anchorX + "," + post.anchorY);
        assertTrue(post.anchorY < 7,
                "the scan stopped short instead of stepping past the reservation");
    }

    /**
     * And with nothing reserved it still stands where it always did, so the
     * change is a refusal rather than a relocation.
     */
    @Test
    public void anUnreservedApproachIsUnchanged() {
        NavigationGrid grid = openGrid();
        List<TacticalNode> withMask = new ArrayList<>();
        withMask.add(compoundNode(TacticalNode.Kind.BARRACKS, 8, 8, 12, 12));
        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, withMask, new boolean[W][H]);

        List<TacticalNode> withoutMask = new ArrayList<>();
        withoutMask.add(compoundNode(TacticalNode.Kind.BARRACKS, 8, 8, 12, 12));
        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, withoutMask);

        assertEquals(withoutMask.get(1).anchorX, withMask.get(1).anchorX);
        assertEquals(withoutMask.get(1).anchorY, withMask.get(1).anchorY);
    }

    /**
     * A compound with nothing legal outside it gets no lookout at all, rather
     * than one standing somewhere it must not.
     */
    @Test
    public void anApproachThatIsEntirelySpokenForYieldsNoPost() {
        NavigationGrid grid = openGrid();
        List<TacticalNode> tactical = new ArrayList<>();
        tactical.add(compoundNode(TacticalNode.Kind.BARRACKS, 8, 8, 12, 12));

        boolean[][] reserved = new boolean[W][H];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) reserved[x][y] = true;
        }

        runStamper(grid, TraversalAxis.SOUTH_TO_NORTH, tactical, reserved);

        assertEquals(1, tactical.size(),
                "a guardpost was emitted with nowhere legal to put it");
    }
}