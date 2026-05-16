package com.dillon.starsectormarines.battle;

/**
 * A tagged building footprint on the battle map. Recorded by
 * {@link UrbanMapGenerator} as it carves the city, then consumed by mission
 * setups when they need to anchor objectives to specific structures (charge
 * sites on a lab, loot crates in a depot, VIP hiding in residential, etc.).
 *
 * <p>Bounds are inclusive on both axes ({@link #left}..{@link #right},
 * {@link #top}..{@link #bottom}) and refer to the wall cells of the building.
 * Mission code typically wants an adjacent walkable cell for unit placement —
 * see {@link #anchorCellX}/{@link #anchorCellY} for the canonical "stand here
 * to interact with this building" coordinate, which the generator picks as the
 * nearest walkable cell to the building center.
 */
public final class PointOfInterest {

    public enum Kind {
        LABORATORY,
        COMMS,
        DEPOT,
        RESIDENTIAL
    }

    public final Kind kind;
    public final int left, top, right, bottom;
    public final int anchorCellX, anchorCellY;

    public PointOfInterest(Kind kind, int left, int top, int right, int bottom, int anchorCellX, int anchorCellY) {
        this.kind = kind;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.anchorCellX = anchorCellX;
        this.anchorCellY = anchorCellY;
    }

    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
}
