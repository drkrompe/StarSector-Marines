package com.dillon.starsectormarines.battle.nav;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A per-cell traversal multiplier published as one immutable snapshot, carrying
 * the revision that identifies it.
 *
 * <p>A bare array of multipliers is enough to bend
 * a route. It is not enough to <em>cache</em> one: {@link SharedGoalPathfinder}
 * keeps a reverse tree per goal and reuses it across snapshots, so a field
 * whose contents changed underneath it would keep serving routes computed
 * against costs that no longer exist. The revision is what lets that cache tell
 * two costings apart, which is why it travels with the snapshot.
 *
 * <p>Distinct producers must not share a revision sequence: the marine and
 * defender loss costings are different fields for the same map and would
 * otherwise collide in the cache. {@link #nextRevision()} hands out values
 * unique across every field in the process, which makes that impossible to get
 * wrong by accident.
 *
 * <p><b>Baseline is 1.0 and values only rise from there.</b> A multiplier below
 * one would break the octile heuristic's admissibility in every A* that reads
 * this, so a producer discourages ground rather than encouraging it.
 */
public final class RouteCostField implements GridPathfinder.IndexedCost {

    private static final AtomicLong REVISIONS = new AtomicLong(1L);

    private final float[] values;
    private final BlockLayout blockLayout;
    private final long revision;

    /** Takes ownership of a dense array; the producer must never mutate it again. */
    public RouteCostField(float[] cells, long revision) {
        this(cells, null, revision);
    }

    private RouteCostField(float[] values, BlockLayout blockLayout, long revision) {
        this.values = Objects.requireNonNull(values, "values");
        this.blockLayout = blockLayout;
        this.revision = revision;
    }

    /** A revision distinct from every other one handed out in this process. */
    public static long nextRevision() {
        return REVISIONS.getAndIncrement();
    }

    /**
     * The multiplier at a logical cell, indexed by {@link NavigationGrid#index}.
     * Block-backed snapshots keep the producer's resolution without allocating
     * a full-map array, including when a search retains an older publication.
     */
    @Override
    public float costAt(int index) {
        if (blockLayout == null) return values[index];
        int y = index / blockLayout.cellWidth;
        int x = index - y * blockLayout.cellWidth;
        return costAt(index, x, y);
    }

    /** Coordinates must describe the same logical cell as index. No lookup allocation. */
    @Override
    public float costAt(int index, int x, int y) {
        if (blockLayout == null) return values[index];
        return values[blockLayout.blockRows[y] + blockLayout.blockColumns[x]];
    }

    /** Logical cell count, independent of the storage resolution. */
    public int size() { return blockLayout == null ? values.length : blockLayout.cellCount; }

    /** Stored multipliers, excluding the reusable layout's coordinate tables. */
    public int storedValueCount() { return values.length; }

    public long revision() { return revision; }

    /**
     * One immutable map layout shared by successive block-cost publications.
     * Coordinate tables cost only width + height integers and avoid dividing
     * both cell coordinates by the block size on every path expansion.
     */
    public static final class BlockLayout {
        private final int cellWidth;
        private final int cellCount;
        private final int blockWidth;
        private final int blockHeight;
        private final int blockCount;
        private final int[] blockColumns;
        private final int[] blockRows;

        public BlockLayout(int cellWidth, int cellHeight, int blockSize) {
            if (cellWidth <= 0 || cellHeight <= 0 || blockSize <= 0) {
                throw new IllegalArgumentException("Map dimensions and block size must be positive");
            }
            this.cellWidth = cellWidth;
            this.cellCount = Math.multiplyExact(cellWidth, cellHeight);
            this.blockWidth = 1 + (cellWidth - 1) / blockSize;
            this.blockHeight = 1 + (cellHeight - 1) / blockSize;
            this.blockCount = Math.multiplyExact(blockWidth, blockHeight);
            this.blockColumns = new int[cellWidth];
            this.blockRows = new int[cellHeight];
            for (int x = 0; x < cellWidth; x++) blockColumns[x] = x / blockSize;
            for (int y = 0; y < cellHeight; y++) blockRows[y] = (y / blockSize) * blockWidth;
        }

        public int blockWidth() { return blockWidth; }

        public int blockHeight() { return blockHeight; }

        /**
         * Takes ownership of one multiplier per row-major block. The producer
         * must never mutate this array after publication, just as for dense
         * snapshots; the shared layout itself contains no mutable cost state.
         */
        public RouteCostField snapshot(float[] blockMultipliers, long revision) {
            Objects.requireNonNull(blockMultipliers, "blockMultipliers");
            if (blockMultipliers.length != blockCount) {
                throw new IllegalArgumentException("Expected " + blockCount + " block multipliers, got "
                        + blockMultipliers.length);
            }
            return new RouteCostField(blockMultipliers, this, revision);
        }
    }
}
