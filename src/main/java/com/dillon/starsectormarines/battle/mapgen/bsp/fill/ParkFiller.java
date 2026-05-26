package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.map.Doodad;
import com.dillon.starsectormarines.battle.map.PointOfInterest;
import com.dillon.starsectormarines.battle.map.TileManifest.TileFrame;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.List;
import java.util.Random;

/**
 * Fills a {@link BlockKind#PARK} leaf as a green, open block:
 * <ul>
 *   <li>Every cell walkable — no walls, no doorways.</li>
 *   <li>Whole interior reads as {@link GroundKind#GRASS}.</li>
 *   <li>Optionally a single 1-cell-wide stone path cuts the leaf either
 *       horizontally or vertically (RNG-picked) so squads can flow through
 *       without trampling the grass. Skipped on leaves too small to host a
 *       meaningful path.</li>
 *   <li>At most 1-2 bench doodads (urban-1 (6, 7)) — parks are low-cover
 *       open ground, not seating areas.</li>
 *   <li>No POI emitted.</li>
 * </ul>
 *
 * Deterministic — all randomness sourced from the orchestrator-seeded
 * {@link Random} passed in.
 */
public final class ParkFiller implements BlockFiller {

    /** Bench tile on the urban-1 sheet — paired-seat doodad at (6, 7). */
    private static final TileFrame BENCH = new TileFrame(6, 7);

    /** Minimum width/height to bother cutting a stone path through the park. */
    private static final int MIN_PATH_SIDE = 5;

    @Override
    public BlockKind kind() { return BlockKind.PARK; }

    @Override
    public void fill(BlockLeaf leaf,
                     NavigationGrid grid,
                     CellTopology topology,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     Random rng) {

        // Carve the leaf as walkable grass.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.GRASS);
            }
        }

        // Optional stone path — single-cell-wide line, orientation RNG'd.
        // Path is purely cosmetic; the cells stay walkable (already are).
        if (leaf.width() >= MIN_PATH_SIDE && leaf.height() >= MIN_PATH_SIDE) {
            if (rng.nextBoolean()) {
                // Horizontal path along an interior row.
                int innerSpan = leaf.height() - 2;
                int pathY = leaf.top + 1 + rng.nextInt(innerSpan);
                for (int x = leaf.left; x <= leaf.right; x++) {
                    topology.setGroundKind(x, pathY, GroundKind.STONE);
                }
            } else {
                // Vertical path along an interior column.
                int innerSpan = leaf.width() - 2;
                int pathX = leaf.left + 1 + rng.nextInt(innerSpan);
                for (int y = leaf.top; y <= leaf.bottom; y++) {
                    topology.setGroundKind(pathX, y, GroundKind.STONE);
                }
            }
        }

        // 1-2 benches, placed on edge cells so the middle of the park stays open.
        int benchCount = 1 + rng.nextInt(2);

        // Tiny leaves: skip doodads entirely rather than crowd them.
        if (leaf.width() < 3 || leaf.height() < 3) return;

        for (int i = 0; i < benchCount; i++) {
            // Pick a perimeter cell of the leaf.
            // Side: 0=top, 1=bottom, 2=left, 3=right.
            int side = rng.nextInt(4);
            int x, y;
            switch (side) {
                case 0: // top
                    x = leaf.left + rng.nextInt(leaf.width());
                    y = leaf.top;
                    break;
                case 1: // bottom
                    x = leaf.left + rng.nextInt(leaf.width());
                    y = leaf.bottom;
                    break;
                case 2: // left
                    x = leaf.left;
                    y = leaf.top + rng.nextInt(leaf.height());
                    break;
                default: // right
                    x = leaf.right;
                    y = leaf.top + rng.nextInt(leaf.height());
                    break;
            }
            doodads.add(new Doodad(x, y, BENCH));
        }
    }
}
