package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.TileManifest;
import com.dillon.starsectormarines.battle.world.model.TileManifest.TileFrame;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fills an {@link BlockKind#INDUSTRIAL_YARD} leaf as an open, dirt-floored
 * loading yard packed with crate cover.
 *
 * <p>Layout recipe:
 * <ul>
 *   <li>Every cell stays walkable (no walls carved). Ground is painted as
 *       {@link GroundKind#DIRT} wall-to-wall.</li>
 *   <li>1-2 small 2x2 pads of {@link GroundKind#STRIPED} floor are stamped
 *       inside the leaf for "loading bay" flavor — kept fully within the
 *       rect so the road frame is never touched.</li>
 *   <li>4-8 doodads from {@link TileManifest#WAREHOUSE_DOODADS} are
 *       scattered on walkable cells. INDUSTRIAL_YARD is the "dense outdoor
 *       cover" archetype, so the doodad count skews high.</li>
 * </ul>
 *
 * <p>No POI is emitted — the leaf is intentionally a sea of low-cover crates
 * rather than a hardpoint building.
 */
public final class IndustrialYardFiller implements BlockFiller {

    private static final int MIN_DOODADS = 4;
    private static final int MAX_DOODADS = 8;
    private static final int STRIPED_PAD_SIZE = 2;
    private static final int MAX_STRIPED_PADS = 2;

    @Override
    public BlockKind kind() { return BlockKind.INDUSTRIAL_YARD; }

    @Override
    public void fill(BlockLeaf leaf, NavigationGrid grid, CellTopology topology,
                     List<PointOfInterest> pois, List<Doodad> doodads, Random rng) {

        // Paint the whole leaf as dirt + walkable.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }

        // Stamp 1..MAX_STRIPED_PADS small striped pads inside the leaf,
        // provided the leaf is wide/tall enough to hold one. Each pad is a
        // 2x2 square fully contained in the rect — origin is sampled so the
        // far corner stays inside [left, right] x [top, bottom].
        int maxOriginX = leaf.right  - (STRIPED_PAD_SIZE - 1);
        int maxOriginY = leaf.bottom - (STRIPED_PAD_SIZE - 1);
        if (maxOriginX >= leaf.left && maxOriginY >= leaf.top) {
            int padCount = 1 + rng.nextInt(MAX_STRIPED_PADS); // 1..MAX_STRIPED_PADS
            for (int p = 0; p < padCount; p++) {
                int ox = leaf.left + rng.nextInt(maxOriginX - leaf.left + 1);
                int oy = leaf.top  + rng.nextInt(maxOriginY - leaf.top  + 1);
                for (int dy = 0; dy < STRIPED_PAD_SIZE; dy++) {
                    for (int dx = 0; dx < STRIPED_PAD_SIZE; dx++) {
                        topology.setGroundKind(ox + dx, oy + dy, GroundKind.STRIPED);
                    }
                }
            }
        }

        // Collect every walkable cell in the leaf for doodad sampling.
        List<int[]> walkable = new ArrayList<>();
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (grid.isWalkable(x, y)) walkable.add(new int[]{x, y});
            }
        }
        if (walkable.isEmpty()) return;

        int target = MIN_DOODADS + rng.nextInt(MAX_DOODADS - MIN_DOODADS + 1);
        int placed = 0;
        TileFrame[] pool = TileManifest.WAREHOUSE_DOODADS;
        while (placed < target && !walkable.isEmpty()) {
            int idx = rng.nextInt(walkable.size());
            int[] cell = walkable.remove(idx);
            TileFrame tile = pool[rng.nextInt(pool.length)];
            doodads.add(new Doodad(cell[0], cell[1], tile));
            placed++;
        }
    }
}
