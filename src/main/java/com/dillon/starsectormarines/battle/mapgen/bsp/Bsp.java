package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure BSP partition over a rect grid. Recursively splits the root rect with
 * jittered axis-aligned cuts; each cut carves a road strip of width
 * {@link #ROAD_WIDTH_MIN}..{@link #ROAD_WIDTH_MAX} along the split line that
 * the two children share. The leaf list returned by {@link #partition} is
 * the set of un-split rects, each of which a {@code BlockFiller} will paint.
 *
 * <p>Connectivity falls out by construction: every leaf's road frame is
 * shared with the road strip its parent carved, so the union of all road
 * strips + the 1-cell perimeter reserve is a single connected outdoor
 * region. Map-edge reservation is folded into the perimeter set so corner
 * spawn anchors always land on walkable ground.
 *
 * <p>Determinism is end-to-end keyed on the {@link Random} the caller passes.
 * Within one partition pass the algorithm makes a fixed sequence of decisions
 * (split axis → split position → recurse left then right), so the same seed
 * yields the same tree.
 *
 * <p>This class is intentionally narrow — segmentation only, no theme
 * decisions, no painting. The orchestrator ({@code BspCityGenerator}) drives
 * labeling and dispatch.
 */
public final class Bsp {

    /** Minimum road strip width. ≥3 because narrower roads read as alleys, which look wrong against city-scale buildings. */
    public static final int ROAD_WIDTH_MIN = 3;
    /** Maximum road strip width — wider strips read as main thoroughfares. */
    public static final int ROAD_WIDTH_MAX = 5;

    /** Minimum leaf dim (both axes). Below this we can't fit a hollow building or a readable plaza. */
    public static final int LEAF_MIN = 5;
    /** Max leaf dim along either axis before we force another split. Caps "one giant block" outcomes. */
    public static final int LEAF_MAX = 18;
    /** Past this aspect ratio we force a split on the long axis instead of coin-flipping. Keeps leaves shaped like real parcels. */
    private static final float SQUARENESS_THRESHOLD = 1.6f;

    /**
     * Carved-out artifacts of one partition pass.
     * {@link #leaves} are the addressable blocks (excluding road strips).
     * {@link #roadCells} are the cells the orchestrator should paint with
     * road autotile / leave walkable for connectivity.
     */
    public static final class Partition {
        public final List<BlockLeaf> leaves;
        public final boolean[][] roadCells;
        public final int width;
        public final int height;

        public Partition(List<BlockLeaf> leaves, boolean[][] roadCells, int width, int height) {
            this.leaves = leaves;
            this.roadCells = roadCells;
            this.width = width;
            this.height = height;
        }
    }

    private Bsp() {}

    /**
     * Partition the rect {@code [0, width) × [0, height)} into BSP leaves +
     * a connected road strip set. The 1-cell perimeter of the grid is
     * reserved as road so map-edge spawns always land on walkable ground.
     */
    public static Partition partition(int width, int height, Random rng) {
        boolean[][] road = new boolean[width][height];
        // Perimeter reserve — every map-edge cell is road.
        for (int x = 0; x < width; x++)  { road[x][0] = true; road[x][height - 1] = true; }
        for (int y = 0; y < height; y++) { road[0][y] = true; road[width  - 1][y] = true; }

        // Initial rect excludes the perimeter (the children own [1..w-2, 1..h-2]).
        List<BlockLeaf> leaves = new ArrayList<>();
        split(1, 1, width - 2, height - 2, width, height, road, leaves, rng);
        return new Partition(leaves, road, width, height);
    }

    /**
     * Recursive split. Stops when the rect is below the leaf-split threshold
     * AND a random coin says "leaf", or when neither axis has room for a
     * child + a road strip. Emits a {@link BlockLeaf} on leaf, otherwise
     * carves a road strip on the split line and recurses into the two halves.
     */
    private static void split(int x0, int y0, int x1, int y1,
                              int mapW, int mapH,
                              boolean[][] road, List<BlockLeaf> leaves, Random rng) {
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        // Can we even split? Each child must satisfy LEAF_MIN, and the road
        // strip in between takes ROAD_WIDTH_MIN. So the splittable dim must be
        // ≥ 2*LEAF_MIN + ROAD_WIDTH_MIN.
        boolean canSplitH = w >= 2 * LEAF_MIN + ROAD_WIDTH_MIN; // vertical split → carves road column
        boolean canSplitV = h >= 2 * LEAF_MIN + ROAD_WIDTH_MIN; // horizontal split → carves road row

        // Stop conditions: too small to split OR both dims small enough that
        // a leaf reads acceptably AND a random coin chooses to stop.
        boolean smallEnoughToBeLeaf = w <= LEAF_MAX && h <= LEAF_MAX;
        boolean canSplit = canSplitH || canSplitV;
        if (!canSplit || (smallEnoughToBeLeaf && rng.nextFloat() < leafProbability(w, h))) {
            emitLeaf(x0, y0, x1, y1, mapW, mapH, leaves);
            return;
        }

        // Pick split axis. Strong bias to splitting the long side past 1.6:1.
        boolean splitVertically; // vertical cut → carve a road column
        float aspect = (float) Math.max(w, h) / Math.min(w, h);
        if (aspect >= SQUARENESS_THRESHOLD) {
            splitVertically = w >= h;
        } else if (canSplitH && canSplitV) {
            splitVertically = rng.nextBoolean();
        } else {
            splitVertically = canSplitH;
        }

        int roadWidth = ROAD_WIDTH_MIN + rng.nextInt(ROAD_WIDTH_MAX - ROAD_WIDTH_MIN + 1);

        if (splitVertically) {
            // Pick split column: leaves [x0..splitX-1] | road [splitX..splitX+rw-1] | leaves [splitX+rw..x1].
            int minSplitX = x0 + LEAF_MIN;
            int maxSplitX = x1 - LEAF_MIN - roadWidth + 1;
            int splitX = jitterMid(minSplitX, maxSplitX, rng);
            for (int xx = splitX; xx < splitX + roadWidth; xx++) {
                for (int yy = y0; yy <= y1; yy++) road[xx][yy] = true;
            }
            split(x0, y0, splitX - 1, y1, mapW, mapH, road, leaves, rng);
            split(splitX + roadWidth, y0, x1, y1, mapW, mapH, road, leaves, rng);
        } else {
            int minSplitY = y0 + LEAF_MIN;
            int maxSplitY = y1 - LEAF_MIN - roadWidth + 1;
            int splitY = jitterMid(minSplitY, maxSplitY, rng);
            for (int yy = splitY; yy < splitY + roadWidth; yy++) {
                for (int xx = x0; xx <= x1; xx++) road[xx][yy] = true;
            }
            split(x0, y0, x1, splitY - 1, mapW, mapH, road, leaves, rng);
            split(x0, splitY + roadWidth, x1, y1, mapW, mapH, road, leaves, rng);
        }
    }

    /**
     * Pick a split offset jittered around the midpoint so leaves aren't all
     * neatly halved. {@code lo}/{@code hi} are inclusive bounds the cut may
     * land between; the jitter span is ±¼ of the available range to keep
     * children from collapsing to {@link #LEAF_MIN}.
     */
    private static int jitterMid(int lo, int hi, Random rng) {
        if (hi <= lo) return lo;
        int mid = (lo + hi) / 2;
        int span = Math.max(1, (hi - lo) / 4);
        int jitter = rng.nextInt(2 * span + 1) - span;
        int v = mid + jitter;
        if (v < lo) v = lo;
        if (v > hi) v = hi;
        return v;
    }

    /**
     * Leaf-stop probability: small enough leaves are likely to stop;
     * larger leaves keep splitting. Hits 1.0 when both dims are at LEAF_MIN
     * (forced stop already handled by the canSplit check above) and ~0.2
     * when both are at LEAF_MAX (heavy bias toward continuing to split).
     */
    private static float leafProbability(int w, int h) {
        float wRatio = (float) (LEAF_MAX - w) / (LEAF_MAX - LEAF_MIN);
        float hRatio = (float) (LEAF_MAX - h) / (LEAF_MAX - LEAF_MIN);
        return Math.max(0f, Math.min(1f, 0.2f + 0.6f * (wRatio + hRatio) / 2f));
    }

    private static void emitLeaf(int x0, int y0, int x1, int y1,
                                 int mapW, int mapH, List<BlockLeaf> leaves) {
        boolean touchesEdge = x0 <= 1 || y0 <= 1 || x1 >= mapW - 2 || y1 >= mapH - 2;
        leaves.add(new BlockLeaf(x0, y0, x1, y1, touchesEdge));
    }
}
