package com.dillon.starsectormarines.battle.mapgen.bsp.fill;

import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.Random;

/**
 * Two parallel interior walls dividing a building into three chambers
 * along the longer axis. The middle chamber is intentionally narrow so
 * the BFS-from-center anchor reliably lands in an end chamber (which
 * the distance-indexed labeling then tags as KEEP_THRONE).
 *
 * <p>Falls back to {@link BinaryPartitionStrategy} when the building
 * is large enough for two chambers but not three.
 */
final class TernaryPartitionStrategy implements PartitionStrategy {

    private static final int MIN_DIM_LONG  = 14;
    private static final int MIN_DIM_SHORT =  7;
    private static final int MIN_CHAMBER   =  3;

    static final TernaryPartitionStrategy DEFAULT = new TernaryPartitionStrategy(0.65f);

    private final BinaryPartitionStrategy binaryFallback;

    TernaryPartitionStrategy(float chance) {
        this.binaryFallback = new BinaryPartitionStrategy(chance);
    }

    @Override
    public PartitionLayout partition(NavigationGrid grid, CellTopology topology,
                                     int bl, int bt, int br, int bb,
                                     Random rng, GroundKind interiorGround) {
        int w = br - bl + 1;
        int h = bb - bt + 1;

        boolean vertTernary  = w >= MIN_DIM_LONG && h >= MIN_DIM_SHORT;
        boolean horizTernary = h >= MIN_DIM_LONG && w >= MIN_DIM_SHORT;
        if (!vertTernary && !horizTernary) {
            return binaryFallback.partition(grid, topology, bl, bt, br, bb, rng, interiorGround);
        }

        boolean vertical;
        if (vertTernary && horizTernary) {
            if (w > h)      vertical = true;
            else if (h > w) vertical = false;
            else            vertical = rng.nextBoolean();
        } else {
            vertical = vertTernary;
        }

        if (vertical) {
            return carveVertical(grid, topology, bl, bt, br, bb, w, h, rng, interiorGround);
        } else {
            return carveHorizontal(grid, topology, bl, bt, br, bb, w, h, rng, interiorGround);
        }
    }

    private PartitionLayout carveVertical(NavigationGrid grid, CellTopology topology,
                                          int bl, int bt, int br, int bb,
                                          int w, int h, Random rng, GroundKind interiorGround) {
        int interior = w - 2;
        int center = interior / 2;
        int[] walls = pickWallPair(interior, center, rng);
        int wx1 = bl + 1 + walls[0];
        int wx2 = bl + 1 + walls[1];

        for (int y = bt + 1; y <= bb - 1; y++) {
            grid.setWalkable(wx1, y, false);
            grid.setWalkable(wx2, y, false);
        }
        int dy1 = bt + 1 + rng.nextInt(h - 2);
        int dy2 = bt + 1 + rng.nextInt(h - 2);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, wx1, dy1, interiorGround);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, wx2, dy2, interiorGround);
        return new PartitionLayout(PartitionLayout.Orient.VERTICAL, new int[]{wx1, wx2});
    }

    private PartitionLayout carveHorizontal(NavigationGrid grid, CellTopology topology,
                                            int bl, int bt, int br, int bb,
                                            int w, int h, Random rng, GroundKind interiorGround) {
        int interior = h - 2;
        int center = interior / 2;
        int[] walls = pickWallPair(interior, center, rng);
        int wy1 = bt + 1 + walls[0];
        int wy2 = bt + 1 + walls[1];

        for (int x = bl + 1; x <= br - 1; x++) {
            grid.setWalkable(x, wy1, false);
            grid.setWalkable(x, wy2, false);
        }
        int dx1 = bl + 1 + rng.nextInt(w - 2);
        int dx2 = bl + 1 + rng.nextInt(w - 2);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, dx1, wy1, interiorGround);
        BinaryPartitionStrategy.openInteriorDoorway(grid, topology, dx2, wy2, interiorGround);
        return new PartitionLayout(PartitionLayout.Orient.HORIZONTAL, new int[]{wy1, wy2});
    }

    /**
     * Picks two wall offsets within the interior span that produce three
     * chambers, each at least {@link #MIN_CHAMBER} cells wide. Both walls
     * are placed on the same side of {@code center} so BFS-from-center
     * reliably lands in an end chamber (the THRONE side).
     *
     * <p>Layout: {@code [0..wall1-1] wall1 [wall1+1..wall2-1] wall2 [wall2+1..interior-1]}
     *
     * @param interior number of interior cells along the split axis
     * @param center   interior-relative center offset (interior / 2)
     * @return two offsets (0-based from the first interior cell), sorted ascending
     */
    private static int[] pickWallPair(int interior, int center, Random rng) {
        // Place both walls above center (so center is in chamber 0) or
        // below center (so center is in chamber 2). Pick randomly.
        boolean wallsAboveCenter = rng.nextBoolean();

        if (wallsAboveCenter) {
            // wall1 ≥ center + 1 (center stays in chamber 0)
            // wall2 ≤ interior - 1 - MIN_CHAMBER (right end gets MIN_CHAMBER)
            // middle: wall2 - wall1 - 1 ≥ MIN_CHAMBER
            int wall1Min = Math.max(MIN_CHAMBER, center + 1);
            int wall2Max = interior - 1 - MIN_CHAMBER;
            return pickConstrained(wall1Min, wall2Max, interior, rng);
        } else {
            // wall2 ≤ center - 1 (center stays in chamber 2)
            // wall1 ≥ MIN_CHAMBER (left end gets MIN_CHAMBER)
            // middle: wall2 - wall1 - 1 ≥ MIN_CHAMBER
            int wall1Min = MIN_CHAMBER;
            int wall2Max = Math.min(interior - 1 - MIN_CHAMBER, center - 1);
            return pickConstrained(wall1Min, wall2Max, interior, rng);
        }
    }

    private static int[] pickConstrained(int wall1Min, int wall2Max, int interior, Random rng) {
        int wall1Max = wall2Max - MIN_CHAMBER - 1;
        if (wall1Max < wall1Min) {
            // Degenerate fallback — pack walls as tightly as constraints allow.
            return new int[]{wall1Min, wall1Min + MIN_CHAMBER + 1};
        }
        int wall1 = wall1Min + rng.nextInt(wall1Max - wall1Min + 1);
        int wall2Min = wall1 + MIN_CHAMBER + 1;
        if (wall2Max < wall2Min) wall2Max = wall2Min;
        int wall2 = wall2Min + rng.nextInt(wall2Max - wall2Min + 1);
        return new int[]{wall1, wall2};
    }
}
