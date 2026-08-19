package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

/**
 * Describes the interior partition(s) a {@link PartitionStrategy} carved
 * inside a building shell. Consumers read orientation + axis positions to
 * align perimeter doorways and compute chamber indices for room labeling.
 *
 * <p>Binary partitions carry one axis; ternary carry two. The
 * {@link #chamberIndex} method abstracts over the count via sorted-array
 * bisect so callers don't need to know how many walls exist.
 */
final class PartitionLayout {

    enum Orient { NONE, VERTICAL, HORIZONTAL }

    private static final int[] NO_AXES = new int[0];

    static final PartitionLayout NONE = new PartitionLayout(Orient.NONE, NO_AXES);

    final Orient orient;
    final int[] axes;
    /** True when this plan is the purpose-built sales-floor + stockroom layout. */
    final boolean tacticalCommercial;
    /** True when the partitioner carved and labeled the multi-room civic plan. */
    final boolean tacticalCivic;
    /** Large stores require a public entrance and an opposed service entrance. */
    final boolean forceOpposedPerimeterDoorways;
    /** Preferred coordinate along the frontage/rear walls, or -1 for random. */
    final int preferredPerimeterDoorAlong;

    PartitionLayout(Orient orient, int[] axes) {
        this(orient, axes, false, false, false, -1);
    }

    PartitionLayout(Orient orient, int[] axes,
                    boolean tacticalCommercial,
                    boolean forceOpposedPerimeterDoorways) {
        this(orient, axes, tacticalCommercial, false,
                forceOpposedPerimeterDoorways, -1);
    }

    PartitionLayout(Orient orient, int[] axes,
                    boolean tacticalCommercial,
                    boolean tacticalCivic,
                    boolean forceOpposedPerimeterDoorways,
                    int preferredPerimeterDoorAlong) {
        this.orient = orient;
        this.axes = axes;
        this.tacticalCommercial = tacticalCommercial;
        this.tacticalCivic = tacticalCivic;
        this.forceOpposedPerimeterDoorways = forceOpposedPerimeterDoorways;
        this.preferredPerimeterDoorAlong = preferredPerimeterDoorAlong;
    }

    /**
     * Returns the chamber index (0..N) for a cell, or {@code -1} when
     * the cell sits exactly on a partition axis (wall or doorway cell).
     * Single-chamber buildings ({@link #NONE}) always return 0.
     */
    int chamberIndex(int x, int y) {
        if (orient == Orient.NONE) return 0;
        int coord = (orient == Orient.VERTICAL) ? x : y;
        for (int i = 0; i < axes.length; i++) {
            if (coord == axes[i]) return -1;
            if (coord < axes[i]) return i;
        }
        return axes.length;
    }
}
