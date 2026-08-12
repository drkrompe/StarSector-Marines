package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.SpaceportFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Structural coverage for the civilian-spaceport pad grammar. */
public class SpaceportFillerTest {

    private static final SpaceportFiller FILLER = new SpaceportFiller();

    private static GenContext fill(BlockLeaf leaf, int w, int h, long seed) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        GenContext ctx = new GenContext(grid, topology, new Random(seed), w, h, seed);
        FILLER.fill(leaf, ctx);
        return ctx;
    }

    @Test
    void publishesClearFiveByFiveBerthWithMarkedCenter() {
        BlockLeaf leaf = new BlockLeaf(2, 2, 15, 13, false);
        GenContext ctx = fill(leaf, 20, 18, 42L);

        assertEquals(1, ctx.landingPads.size());
        LandingPad pad = ctx.landingPads.get(0);
        assertEquals(2, pad.halfWidth);
        assertEquals(2, pad.halfHeight);
        assertTrue(pad.isClear(ctx.grid, ctx.topology));
        assertEquals(CellTopology.GroundKind.LZ_MARKER,
                ctx.topology.getGroundKind(pad.centerX, pad.centerY));

        for (Doodad doodad : ctx.doodads) {
            assertFalse(pad.contains(doodad.cellX, doodad.cellY),
                    "service/cargo prop entered the touchdown exclusion footprint");
        }
    }

    @Test
    void cargoReadsAsAnEdgeBandRatherThanRandomApronScatter() {
        BlockLeaf leaf = new BlockLeaf(2, 2, 17, 12, false); // wide grammar
        GenContext ctx = fill(leaf, 21, 17, 7L);
        LandingPad pad = ctx.landingPads.get(0);

        assertTrue(ctx.doodads.size() >= 2, "large apron should have an organized service presence");
        for (Doodad doodad : ctx.doodads) {
            boolean nearEdge = doodad.cellX <= leaf.left + 1 || doodad.cellX >= leaf.right - 1
                    || doodad.cellY <= leaf.top + 1 || doodad.cellY >= leaf.bottom - 1;
            assertTrue(nearEdge, "spaceport prop should remain in an edge service band");
            assertFalse(pad.contains(doodad.cellX, doodad.cellY));
        }
    }

    @Test
    void emitsReachableControlOffice() {
        BlockLeaf leaf = new BlockLeaf(2, 2, 15, 13, false);
        GenContext ctx = fill(leaf, 20, 18, 7L);
        assertEquals(1, ctx.pois.size());
        PointOfInterest office = ctx.pois.get(0);
        assertEquals(PointOfInterest.Kind.COMMS, office.kind);
        assertTrue(ctx.grid.isWalkable(office.interiorAnchorX, office.interiorAnchorY));
        assertTrue(ctx.grid.isWalkable(office.anchorCellX, office.anchorCellY));
    }

    @Test
    void apronStaysOneConnectedRegionAcrossShapesAndSeeds() {
        int[][] shapes = {
                {2, 2, 15, 13},
                {2, 2, 10, 10},
                {2, 2, 10, 21},
        };
        for (int[] shape : shapes) {
            for (long seed = 0; seed < 20; seed++) {
                BlockLeaf leaf = new BlockLeaf(shape[0], shape[1], shape[2], shape[3], false);
                GenContext ctx = fill(leaf, shape[2] + 3, shape[3] + 3, seed);
                assertEquals(walkableOutdoorCount(ctx, leaf), reachableOutdoorCount(ctx, leaf),
                        "spaceport disconnected for " + leaf.width() + "x" + leaf.height()
                                + " seed=" + seed);
            }
        }
    }

    @Test
    void harborPortThemeRollsSpaceportPads() {
        Random rng = new Random(11L);
        boolean rolledPad = false;
        for (int i = 0; i < 500 && !rolledPad; i++) {
            rolledPad = MapDistrictTheme.HARBOR_PORT.pickBlockKind(rng) == BlockKind.SPACEPORT_PAD;
        }
        assertTrue(rolledPad);
    }

    private static int walkableOutdoorCount(GenContext ctx, BlockLeaf leaf) {
        int count = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (ctx.grid.isWalkable(x, y)
                        && ctx.topology.getGroundKind(x, y) != CellTopology.GroundKind.TILE) count++;
            }
        }
        return count;
    }

    private static int reachableOutdoorCount(GenContext ctx, BlockLeaf leaf) {
        boolean[][] seen = new boolean[ctx.grid.getWidth()][ctx.grid.getHeight()];
        Queue<int[]> queue = new ArrayDeque<>();
        outer:
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (ctx.grid.isWalkable(x, y)
                        && ctx.topology.getGroundKind(x, y) != CellTopology.GroundKind.TILE) {
                    queue.add(new int[]{x, y});
                    seen[x][y] = true;
                    break outer;
                }
            }
        }
        int count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            count++;
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!leaf.contains(nx, ny) || seen[nx][ny] || !ctx.grid.isWalkable(nx, ny)) continue;
                if (ctx.topology.getGroundKind(nx, ny) == CellTopology.GroundKind.TILE) continue;
                seen[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return count;
    }
}
