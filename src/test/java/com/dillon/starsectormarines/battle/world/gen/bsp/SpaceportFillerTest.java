package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.SpaceportFiller;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 of the economic-districts feature — the spaceport apron, the
 * <em>open killing ground</em>. Verifies the tactical identity is real
 * nav-layout structure (not doodads): a mostly-open walkable apron, sparse
 * isolated hard cover that actually produces directional cover at the bake, a
 * control-tower POI, and — the gating invariant — that none of it partitions
 * the apron.
 *
 * <p>See {@code roadmap/economic-districts/overview.md}.
 */
public class SpaceportFillerTest {

    private static final SpaceportFiller FILLER = new SpaceportFiller();

    private static GenContext fill(BlockLeaf leaf, int w, int h, long seed) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        GenContext ctx = new GenContext(grid, topology, new Random(seed), w, h, seed);
        FILLER.fill(leaf, ctx);
        // Mirror FinalizeStage's full-grid cover bake so the test sees the cover
        // the apron's hard-cover islands actually generate.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.recomputeCoverAt(x, y);
        }
        return ctx;
    }

    @Test
    void apronIsMostlyOpenWithRealHardCover() {
        BlockLeaf leaf = new BlockLeaf(2, 2, 15, 13, false); // 14×12 interior apron
        GenContext ctx = fill(leaf, 20, 18, 42L);

        int walkable = 0, nonWalkable = 0, total = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                total++;
                if (ctx.grid.isWalkable(x, y)) walkable++; else nonWalkable++;
            }
        }
        double openRatio = (double) walkable / total;
        assertTrue(openRatio >= 0.85,
                "spaceport apron should read open (>=85% walkable); was " + openRatio);
        assertTrue(nonWalkable >= 2,
                "apron should have at least a couple of hard-cover cells; had " + nonWalkable);

        // Cover is real: at least one apron cell gains directional cover from an
        // adjacent cover island / tower wall after the bake.
        int covered = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (ctx.grid.isWalkable(x, y) && ctx.grid.getCoverAt(x, y) > 0) covered++;
            }
        }
        assertTrue(covered > 0, "no apron cell gained cover — hard cover isn't producing cover");
    }

    @Test
    void emitsAControlTowerPoi() {
        BlockLeaf leaf = new BlockLeaf(2, 2, 15, 13, false);
        GenContext ctx = fill(leaf, 20, 18, 7L);
        assertEquals(1, ctx.pois.size(), "a large apron should emit exactly one control-tower POI");
        PointOfInterest tower = ctx.pois.get(0);
        assertEquals(PointOfInterest.Kind.COMMS, tower.kind, "control tower should be a COMMS POI");
        assertTrue(leaf.contains(tower.interiorAnchorX, tower.interiorAnchorY), "tower interior must sit inside the leaf");
        assertTrue(ctx.grid.isWalkable(tower.interiorAnchorX, tower.interiorAnchorY), "tower interior must be reachable");
    }

    @Test
    void apronStaysOneConnectedRegion() {
        // Sweep seeds AND leaf shapes (square, wide, thin): hard cover and the
        // tower must never split the apron, whatever the BSP hands us.
        int[][] shapes = {
                {2, 2, 15, 13},   // 14×12 wide
                {2, 2, 8, 8},     //  7×7 square (smallest that carves a tower)
                {2, 2, 8, 21},    //  7×20 thin column
        };
        for (int[] s : shapes) {
            for (long seed : new long[] { 1L, 2L, 3L, 13L, 42L, 100L, 777L, 9999L }) {
                BlockLeaf leaf = new BlockLeaf(s[0], s[1], s[2], s[3], false);
                GenContext ctx = fill(leaf, s[2] + 3, s[3] + 3, seed);
                assertEquals(walkableCount(ctx, leaf), reachableFromCorner(ctx, leaf),
                        "apron split into >1 region (shape " + leaf.width() + "x" + leaf.height()
                                + ", seed " + seed + ")");
            }
        }
    }

    @Test
    void smallLeafSkipsTowerButIslandsStillProduceCover() {
        // Below the tower-min leaf size: open apron + cover islands, no tower.
        // With no tower walls present, any cover is PROOF the islands produce it
        // — this isolates the island mechanic the large-leaf test can't.
        BlockLeaf leaf = new BlockLeaf(1, 1, 6, 6, false); // 6×6
        GenContext ctx = fill(leaf, 10, 10, 3L);
        assertTrue(ctx.pois.isEmpty(), "a small apron shouldn't carve a control tower");

        int nonWalkable = 0, covered = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (!ctx.grid.isWalkable(x, y)) nonWalkable++;
                else if (ctx.grid.getCoverAt(x, y) > 0) covered++;
            }
        }
        assertTrue(nonWalkable >= 2, "small apron should still scatter hard-cover islands");
        assertTrue(covered > 0, "cover islands produced no cover (no tower here — must be the islands)");
    }

    @Test
    void harborPortThemeRollsSpaceportPads() {
        // The wiring half of the slice: the port band's theme can actually
        // select the new district.
        Random rng = new Random(11L);
        boolean rolledPad = false;
        for (int i = 0; i < 500 && !rolledPad; i++) {
            if (MapDistrictTheme.HARBOR_PORT.pickBlockKind(rng) == BlockKind.SPACEPORT_PAD) rolledPad = true;
        }
        assertTrue(rolledPad, "HARBOR_PORT theme never rolled SPACEPORT_PAD — district not wired into selection");
    }

    private static int walkableCount(GenContext ctx, BlockLeaf leaf) {
        int c = 0;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (ctx.grid.isWalkable(x, y)) c++;
            }
        }
        return c;
    }

    /** Flood-fill walkable cells within the leaf from its NW corner (always open apron — the tower is inset). */
    private static int reachableFromCorner(GenContext ctx, BlockLeaf leaf) {
        NavigationGrid grid = ctx.grid;
        boolean[][] seen = new boolean[grid.getWidth()][grid.getHeight()];
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{leaf.left, leaf.top});
        seen[leaf.left][leaf.top] = true;
        int count = 0;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            count++;
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!leaf.contains(nx, ny) || seen[nx][ny]) continue;
                if (!grid.isWalkable(nx, ny)) continue;
                seen[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return count;
    }
}
