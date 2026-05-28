package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.map.Doodad;
import com.dillon.starsectormarines.battle.map.PointOfInterest;
import com.dillon.starsectormarines.battle.map.TileManifest;
import com.dillon.starsectormarines.battle.map.TileManifest.TileFrame;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fills a {@link BlockKind#PLAZA} leaf as an open gathering space:
 * <ul>
 *   <li>Every cell walkable — no walls, no doorways.</li>
 *   <li>Whole interior reads as polished {@link GroundKind#TILE}, with a
 *       1-cell perimeter ring (the inside-edge row of the leaf) bumped to
 *       {@link GroundKind#STONE} as a path-style border accent. Skipped on
 *       tiny leaves where there's no room for a separate ring.</li>
 *   <li>2-4 sparse doodads (benches + stools — urban-1 (6,7), (8,7), (9,7))
 *       biased to edge cells, never on the central rectangle. Reads as a
 *       plaza you walk through, not a junkyard.</li>
 *   <li>No POI emitted.</li>
 * </ul>
 *
 * Deterministic — all randomness sourced from the orchestrator-seeded
 * {@link Random} passed in.
 */
public final class PlazaFiller implements BlockFiller {

    /** Bench tile on the urban-1 sheet — paired-seat doodad at (6, 7). */
    private static final TileFrame BENCH = new TileFrame(6, 7);
    /** Small stool tiles at (8, 7) and (9, 7). */
    private static final TileFrame STOOL_A = new TileFrame(8, 7);
    private static final TileFrame STOOL_B = new TileFrame(9, 7);

    private static final TileFrame[] PLAZA_DOODADS = { BENCH, BENCH, STOOL_A, STOOL_B };

    /** Minimum width/height for the stone perimeter ring — below this the
     *  whole leaf stays TILE so the ring wouldn't swallow the interior. */
    private static final int MIN_RING_SIDE = 5;

    @Override
    public BlockKind kind() { return BlockKind.PLAZA; }

    @Override
    public void fill(BlockLeaf leaf,
                     NavigationGrid grid,
                     CellTopology topology,
                     List<PointOfInterest> pois,
                     List<Doodad> doodads,
                     Random rng) {

        // Carve the leaf as walkable brick-paved plaza pavement.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.BRICK);
            }
        }

        // Stone perimeter ring (1 cell deep on the inside edge of the leaf)
        // — gives the plaza a visible border on anything large enough.
        boolean hasRing = leaf.width() >= MIN_RING_SIDE && leaf.height() >= MIN_RING_SIDE;
        if (hasRing) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                topology.setGroundKind(x, leaf.top,    GroundKind.STONE);
                topology.setGroundKind(x, leaf.bottom, GroundKind.STONE);
            }
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                topology.setGroundKind(leaf.left,  y, GroundKind.STONE);
                topology.setGroundKind(leaf.right, y, GroundKind.STONE);
            }
        }

        // Build the candidate set: edge-biased cells. With a ring, "edge" is
        // the ring itself. Without a ring (tiny leaf), use the actual border
        // cells of the leaf — still keeps doodads off the centerline.
        List<int[]> edgeCells = new ArrayList<>();
        if (leaf.width() >= 3 && leaf.height() >= 3) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                edgeCells.add(new int[] { x, leaf.top });
                edgeCells.add(new int[] { x, leaf.bottom });
            }
            // Avoid double-counting corners.
            for (int y = leaf.top + 1; y <= leaf.bottom - 1; y++) {
                edgeCells.add(new int[] { leaf.left,  y });
                edgeCells.add(new int[] { leaf.right, y });
            }
        }
        if (edgeCells.isEmpty()) {
            // 1- or 2-wide leaves — bail without doodads, the space is too tight.
            return;
        }

        int target = 2 + rng.nextInt(3); // 2..4
        target = Math.min(target, edgeCells.size());

        // Sample without replacement via partial Fisher-Yates so each chosen
        // cell is unique and the picks are deterministic from rng.
        for (int picked = 0; picked < target; picked++) {
            int swapIdx = picked + rng.nextInt(edgeCells.size() - picked);
            int[] tmp = edgeCells.get(picked);
            edgeCells.set(picked, edgeCells.get(swapIdx));
            edgeCells.set(swapIdx, tmp);

            int[] cell = edgeCells.get(picked);
            TileFrame frame = PLAZA_DOODADS[rng.nextInt(PLAZA_DOODADS.length)];
            doodads.add(new Doodad(cell[0], cell[1], frame));
        }
    }
}
