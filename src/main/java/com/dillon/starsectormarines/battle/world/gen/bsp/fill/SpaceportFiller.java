package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.BuildingKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TileManifest.TileFrame;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Filler for {@link BlockKind#SPACEPORT_PAD} — the spaceport apron, an
 * <em>open killing ground</em>. The leaf is one wide walkable striped pad with
 * only <b>sparse, isolated hard-cover islands</b> and a single hardened
 * control-tower hardpoint. Long sightlines, little cover: the tactical opposite
 * of downtown CQB.
 *
 * <p><b>Tactical identity lives in the nav layout, not the doodads</b> (the
 * economic-districts rule). Hard cover here is <em>real</em>: a cover island is
 * a 1-cell non-walkable cargo block, so the apron cells around it gain
 * directional cover at the finalize cover-bake (cover is derived from adjacent
 * walls — see {@link NavigationGrid#recomputeCoverAt}). The blocks are seeded
 * with wall HP, so they read as destructible cargo. Islands are kept small,
 * sparse, and mutually non-adjacent so they never partition the apron or close
 * its sightlines — they're cover to fight <em>around</em>, not a maze.
 *
 * <p>Placeholder doodads come from {@link TileManifest#SKYPORT_DOODADS}; bespoke
 * pad gear (bowsers, loaders, light masts) is a later art pass — see the doodad
 * palette in {@code roadmap/economic-districts/overview.md}.
 */
public final class SpaceportFiller implements BlockFiller {

    /** Apron area per cover island — one island per this many cells, so the read scales with leaf size but stays sparse. */
    private static final int CELLS_PER_COVER_ISLAND = 42;
    private static final int MIN_COVER_ISLANDS = 2;
    private static final int MAX_COVER_ISLANDS = 8;

    /** Apron area per scattered apron doodad — sparse, the apron should read as mostly empty tarmac. */
    private static final int CELLS_PER_APRON_DOODAD = 55;
    private static final int MAX_APRON_DOODADS = 6;

    /** Control-tower hardpoint footprint (square) and its inset from the leaf perimeter so the road frame stays clean. */
    private static final int TOWER_SIDE = 3;
    private static final int TOWER_INSET = 1;
    /** Smallest leaf (each axis) that gets a control tower — below this the leaf is too small to hold a tower and still read as open apron. */
    private static final int TOWER_MIN_LEAF = 7;

    @Override
    public BlockKind kind() { return BlockKind.SPACEPORT_PAD; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        Random rng = ctx.rng;

        // 1. The whole leaf is an open, walkable striped apron.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STRIPED);
            }
        }

        // 2. A single hardened control tower in one corner (when the leaf is
        //    large enough to hold it and still leave open apron around it).
        int[] towerRect = null;
        if (leaf.width() >= TOWER_MIN_LEAF && leaf.height() >= TOWER_MIN_LEAF) {
            towerRect = carveControlTower(leaf, ctx);
        }

        // 3. Sparse, isolated hard-cover islands across the open apron.
        scatterCoverIslands(leaf, towerRect, ctx);

        // 4. Sparse placeholder doodads on the open tarmac (crates / markers).
        scatterApronDoodads(leaf, ctx);
    }

    /**
     * Carves a {@link #TOWER_SIDE}×{@link #TOWER_SIDE} hardened shell in a
     * randomly-chosen corner of the leaf, inset by {@link #TOWER_INSET}: a
     * non-walkable perimeter, a striped interior, and one doorway facing the
     * apron interior so the interior stays reachable (the {@code MapResult}
     * invariant). Emits a {@link PointOfInterest.Kind#COMMS} POI — the control
     * tower is the apron's tactical anchor. Returns the inclusive
     * {@code [left, top, right, bottom]} footprint so cover scatter can avoid it.
     */
    private static int[] carveControlTower(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        CellTopology topology = ctx.topology;
        Random rng = ctx.rng;

        boolean west = rng.nextBoolean();
        boolean north = rng.nextBoolean();
        int left = west ? leaf.left + TOWER_INSET : leaf.right - TOWER_INSET - (TOWER_SIDE - 1);
        int top  = north ? leaf.top + TOWER_INSET  : leaf.bottom - TOWER_INSET - (TOWER_SIDE - 1);
        int right  = left + TOWER_SIDE - 1;
        int bottom = top + TOWER_SIDE - 1;

        // Perimeter wall; interior striped + FORTIFIED hint (reads as a hardpoint).
        for (int x = left; x <= right; x++) {
            grid.setWalkable(x, top, false);
            grid.setWalkable(x, bottom, false);
        }
        for (int y = top + 1; y <= bottom - 1; y++) {
            grid.setWalkable(left, y, false);
            grid.setWalkable(right, y, false);
        }
        int interiorX = left + 1;
        int interiorY = top + 1;
        topology.setGroundKind(interiorX, interiorY, GroundKind.STRIPED);
        topology.setBuildingKindHint(interiorX, interiorY, BuildingKind.FORTIFIED);

        // Doorway on the side facing the apron interior — keeps the tower
        // reachable and its breach point oriented inward, not at the map edge.
        int doorX, doorY, anchorX, anchorY;
        if (west) { doorX = right; anchorX = right + 1; } else { doorX = left; anchorX = left - 1; }
        // Door on the vertical (left/right) face, mid-height.
        doorY = top + 1;
        anchorY = doorY;
        grid.setWalkable(doorX, doorY, true);
        grid.setDoorway(doorX, doorY, true);
        grid.openAllEdges(doorX, doorY);
        topology.setGroundKind(doorX, doorY, GroundKind.STRIPED);
        if (!grid.inBounds(anchorX, anchorY) || !grid.isWalkable(anchorX, anchorY)) {
            anchorX = doorX;
            anchorY = doorY;
        }

        ctx.pois.add(new PointOfInterest(PointOfInterest.Kind.COMMS,
                left, top, right, bottom, anchorX, anchorY, interiorX, interiorY));
        return new int[]{left, top, right, bottom};
    }

    /**
     * Scatters small non-walkable cargo blocks across the apron interior. Each
     * is a 1-cell hard-cover island: kept off the leaf perimeter, off the
     * control-tower footprint, and never cardinally adjacent to an existing
     * island — so the apron stays one connected open field with long sightlines,
     * and the cover-bake gives the cells around each block real directional
     * cover. A placeholder cargo doodad dresses each block.
     */
    private static void scatterCoverIslands(BlockLeaf leaf, int[] towerRect, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        Random rng = ctx.rng;

        // Candidate cells: one in from the leaf border, clear of the tower (+1 margin).
        List<int[]> candidates = new ArrayList<>();
        for (int y = leaf.top + 1; y <= leaf.bottom - 1; y++) {
            for (int x = leaf.left + 1; x <= leaf.right - 1; x++) {
                if (withinMargin(x, y, towerRect, 1)) continue;
                candidates.add(new int[]{x, y});
            }
        }
        if (candidates.isEmpty()) return;

        int area = leaf.width() * leaf.height();
        int budget = Math.max(MIN_COVER_ISLANDS,
                Math.min(MAX_COVER_ISLANDS, area / CELLS_PER_COVER_ISLAND));
        TileFrame[] pool = TileManifest.SKYPORT_DOODADS;
        int placed = 0;
        while (placed < budget && !candidates.isEmpty()) {
            int idx = rng.nextInt(candidates.size());
            int[] cell = candidates.remove(idx);
            int x = cell[0], y = cell[1];
            // Keep islands isolated — skip if a cardinal neighbour is already
            // non-walkable (would chain into a wall and choke a sightline).
            if (!grid.isWalkable(x - 1, y) || !grid.isWalkable(x + 1, y)
                    || !grid.isWalkable(x, y - 1) || !grid.isWalkable(x, y + 1)) {
                continue;
            }
            grid.setWalkable(x, y, false);
            ctx.doodads.add(new Doodad(x, y, pool[rng.nextInt(pool.length)]));
            placed++;
        }
    }

    /** Sparse placeholder doodads on walkable apron cells — the tarmac reads occupied without crowding the open field. */
    private static void scatterApronDoodads(BlockLeaf leaf, GenContext ctx) {
        NavigationGrid grid = ctx.grid;
        Random rng = ctx.rng;

        List<int[]> walkable = new ArrayList<>();
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (grid.isWalkable(x, y) && !grid.isDoorway(x, y)) walkable.add(new int[]{x, y});
            }
        }
        if (walkable.isEmpty()) return;

        int area = leaf.width() * leaf.height();
        int budget = Math.min(MAX_APRON_DOODADS, Math.max(1, area / CELLS_PER_APRON_DOODAD));
        TileFrame[] pool = TileManifest.SKYPORT_DOODADS;
        for (int i = 0; i < budget && !walkable.isEmpty(); i++) {
            int[] cell = walkable.remove(rng.nextInt(walkable.size()));
            ctx.doodads.add(new Doodad(cell[0], cell[1], pool[rng.nextInt(pool.length)]));
        }
    }

    /** True if {@code (x, y)} lies within {@code margin} cells of the inclusive rect {@code [l, t, r, b]} (null rect → false). */
    private static boolean withinMargin(int x, int y, int[] rect, int margin) {
        if (rect == null) return false;
        return x >= rect[0] - margin && x <= rect[2] + margin
                && y >= rect[1] - margin && y <= rect[3] + margin;
    }
}
