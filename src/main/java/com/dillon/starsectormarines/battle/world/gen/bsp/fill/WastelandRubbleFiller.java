package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.map.Doodad;
import com.dillon.starsectormarines.battle.map.PointOfInterest;
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
 * Fills a {@link BlockKind#WASTELAND_RUBBLE} leaf as broken open ground —
 * walkable rubble surface scattered with damaged props and rubble decals.
 *
 * <p>Layout recipe:
 * <ul>
 *   <li>Every cell stays walkable. Ground is painted as
 *       {@link GroundKind#RUBBLE} wall-to-wall — no full wall cells.</li>
 *   <li>3-6 doodads pulled from an inline pool that mixes the urban-1 row-7
 *       damaged props (shelf-dam-1/2, desk-dam, box-dam, chair-s-yellow-dam,
 *       chair-s-green-dam) with the row-7 rubble decals
 *       (decal-rubble-1..4).</li>
 * </ul>
 *
 * <p>No POI is emitted. The block reads as ambush terrain: open lines,
 * scattered low cover, no defended hardpoints.
 */
public final class WastelandRubbleFiller implements BlockFiller {

    private static final int MIN_DOODADS = 3;
    private static final int MAX_DOODADS = 6;

    /**
     * Damaged-prop + rubble-decal mix from urban-tileset.png row 7. Cols 0-3
     * are the rubble decals; cols 4-9 are the damaged props (shelves, desk,
     * box, chairs).
     */
    private static final TileFrame[] WASTELAND_POOL = {
            new TileFrame(0, 7), // decal-rubble-1
            new TileFrame(1, 7), // decal-rubble-2
            new TileFrame(2, 7), // decal-rubble-3
            new TileFrame(3, 7), // decal-rubble-4
            new TileFrame(4, 7), // shelf-dam-1
            new TileFrame(5, 7), // shelf-dam-2
            new TileFrame(6, 7), // desk-dam
            new TileFrame(7, 7), // box-dam
            new TileFrame(8, 7), // chair-s-yellow-dam
            new TileFrame(9, 7), // chair-s-green-dam
    };

    @Override
    public BlockKind kind() { return BlockKind.WASTELAND_RUBBLE; }

    @Override
    public void fill(BlockLeaf leaf, NavigationGrid grid, CellTopology topology,
                     List<PointOfInterest> pois, List<Doodad> doodads, Random rng) {

        // Paint the whole leaf as rubble + walkable; no walls.
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.RUBBLE);
            }
        }

        // Collect walkable cells, then sample-without-replacement so no two
        // doodads stack on the same cell.
        List<int[]> walkable = new ArrayList<>();
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                if (grid.isWalkable(x, y)) walkable.add(new int[]{x, y});
            }
        }
        if (walkable.isEmpty()) return;

        int target = MIN_DOODADS + rng.nextInt(MAX_DOODADS - MIN_DOODADS + 1);
        int placed = 0;
        while (placed < target && !walkable.isEmpty()) {
            int idx = rng.nextInt(walkable.size());
            int[] cell = walkable.remove(idx);
            TileFrame tile = WASTELAND_POOL[rng.nextInt(WASTELAND_POOL.length)];
            doodads.add(new Doodad(cell[0], cell[1], tile));
            placed++;
        }
    }
}
