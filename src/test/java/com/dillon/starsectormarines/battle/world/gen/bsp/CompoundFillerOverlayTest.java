package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MilitaryBaseFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolated coverage for the compound-filler road-overlay contract — the
 * {@link CompoundFiller#fill} path that reads {@link BspKeys#ROAD_CELLS} /
 * {@link BspKeys#ROAD_RESERVATION}. Previously this path was only ever exercised
 * end-to-end by {@code BspMapPreviewTest} (conquest maps always have a
 * MILITARY_BASE compound); the per-filler {@code BuildingZonePreviewTest} builds
 * a {@link GenContext} without binding those overlays and only drives the
 * single-leaf building fillers, so the compound overlay-read path had no unit
 * coverage and the "always non-null" contract on the overlays was unenforced.
 *
 * <p>Two cases:
 * <ul>
 *   <li>overlays bound → {@link MilitaryBaseFiller#fill} runs cleanly, paints a
 *       wall ring (grid mutated) and emits the role-tagged tactical nodes;</li>
 *   <li>overlays unbound → {@link CompoundFiller#requireRoadOverlays} fails fast
 *       with a clear error instead of NPEing deep in the road-bridging pass.</li>
 * </ul>
 */
public class CompoundFillerOverlayTest {

    private static final int W = 22, H = 20;

    /** All-walkable STREET grid — the filler-contract starting state for a leaf's cells. */
    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        return grid;
    }

    private static CellTopology streetTopology() {
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) topology.setGroundKind(x, y, GroundKind.STREET);
        return topology;
    }

    /**
     * A two-leaf MILITARY_BASE compound at the production claim minima:
     * 8x8 COMMAND seed plus 9x9 BARRACKS member.
     */
    private static Compound militaryBase() {
        BlockLeaf seed  = new BlockLeaf(2, 2, 9, 9, false);
        BlockLeaf other = new BlockLeaf(12, 2, 20, 10, false);
        List<BlockLeaf> members = new ArrayList<>(List.of(seed, other));
        Map<BlockLeaf, Compound.Role> roles = new IdentityHashMap<>();
        roles.put(seed, Compound.Role.COMMAND);
        roles.put(other, Compound.Role.BARRACKS);
        // Null biome mirrors a legacy (non-conquest) compound; the filler maps a
        // null biome's COMMAND leaf to COMMAND_POST.
        return new Compound(BlockKind.MILITARY_BASE, seed, members, roles, null);
    }

    private static GenContext ctx(NavigationGrid grid, CellTopology topology) {
        return new GenContext(grid, topology, new Random(1), W, H, 1L);
    }

    @Test
    public void fillRunsWithOverlaysBound() {
        NavigationGrid grid = openGrid();
        GenContext ctx = ctx(grid, streetTopology());
        // The documented all-false fallback a road-graph-less generator binds.
        ctx.put(BspKeys.ROAD_CELLS, new boolean[W][H]);
        ctx.put(BspKeys.ROAD_RESERVATION, new boolean[W][H]);

        new MilitaryBaseFiller().fill(militaryBase(), ctx);

        assertFalse(ctx.tactical.isEmpty(),
                "role-tagged members should emit tactical nodes (COMMAND_POST + BARRACKS)");
        boolean anyWall = false;
        for (int y = 0; y < H && !anyWall; y++) {
            for (int x = 0; x < W; x++) {
                if (!grid.isWalkable(x, y)) { anyWall = true; break; }
            }
        }
        assertTrue(anyWall, "the compound's perimeter wall ring should turn some cells non-walkable");
    }

    @Test
    public void minimumClaimedParcelsStillProduceFunctionalBuildings() {
        NavigationGrid grid = openGrid();
        GenContext ctx = ctx(grid, streetTopology());
        ctx.put(BspKeys.ROAD_CELLS, new boolean[W][H]);
        ctx.put(BspKeys.ROAD_RESERVATION, new boolean[W][H]);

        new MilitaryBaseFiller().fill(militaryBase(), ctx);

        assertEquals(2, ctx.pois.size(),
                "every accepted member parcel must carve a hollow building");
        for (PointOfInterest poi : ctx.pois) {
            assertTrue(poi.right - poi.left + 1 >= 4);
            assertTrue(poi.bottom - poi.top + 1 >= 4);
            assertTrue(grid.isWalkable(poi.interiorAnchorX, poi.interiorAnchorY),
                    "building interior anchor must be standable");
            assertFalse(grid.isDoorway(poi.interiorAnchorX, poi.interiorAnchorY),
                    "interior anchor is not the threshold");
            assertTrue(hasDoorway(grid, poi),
                    "accepted compound building must have an entrance");
        }
    }

    @Test
    public void fillFailsFastWhenOverlaysUnbound() {
        GenContext ctx = ctx(openGrid(), streetTopology());
        // ROAD_CELLS / ROAD_RESERVATION deliberately left unbound.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new MilitaryBaseFiller().fill(militaryBase(), ctx));
        assertTrue(ex.getMessage().contains("ROAD_CELLS"),
                "the precondition error should name the missing overlay; was: " + ex.getMessage());
    }

    private static boolean hasDoorway(NavigationGrid grid, PointOfInterest poi) {
        for (int y = poi.top; y <= poi.bottom; y++) {
            for (int x = poi.left; x <= poi.right; x++) {
                if (grid.isDoorway(x, y)) return true;
            }
        }
        return false;
    }
}
