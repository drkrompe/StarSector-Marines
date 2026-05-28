package com.dillon.starsectormarines.battle.world.model;

/**
 * A tagged building footprint on the battle map. Recorded by
 * {@link UrbanMapGenerator} as it carves the city, then consumed by mission
 * setups when they need to anchor objectives to specific structures (charge
 * sites on a lab, loot crates in a depot, VIP hiding in residential, etc.).
 *
 * <p>Bounds are inclusive on both axes ({@link #left}..{@link #right},
 * {@link #top}..{@link #bottom}) and refer to the wall cells of the building.
 *
 * <p>Two anchors are exposed:
 * <ul>
 *   <li>{@link #anchorCellX}/{@link #anchorCellY} — the canonical "stand here
 *       to interact with this building" cell, picked just <em>outside</em> the
 *       building near a doorway. This is what civilians, patrol waypoints, and
 *       LZ-pickers want.</li>
 *   <li>{@link #interiorAnchorX}/{@link #interiorAnchorY} — a walkable INDOOR
 *       cell <em>inside</em> the building. Mission objectives that need to
 *       happen inside the structure (sabotage charge plants, garrison spawns,
 *       hostage extracts) want this anchor; the unit pathfinds in through a
 *       doorway and arrives at it.</li>
 * </ul>
 * For solid (uncarved) footprints with no walkable interior, the interior
 * anchor falls back to the exterior anchor so consumers don't need to null-
 * check.
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
    public final int interiorAnchorX, interiorAnchorY;

    public PointOfInterest(Kind kind, int left, int top, int right, int bottom,
                           int anchorCellX, int anchorCellY,
                           int interiorAnchorX, int interiorAnchorY) {
        this.kind = kind;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.anchorCellX = anchorCellX;
        this.anchorCellY = anchorCellY;
        this.interiorAnchorX = interiorAnchorX;
        this.interiorAnchorY = interiorAnchorY;
    }

    /** Back-compat ctor — used by legacy callers that don't track an interior cell. Interior anchor mirrors the exterior anchor, so any caller reading {@code interiorAnchor*} on these still gets a walkable cell. */
    public PointOfInterest(Kind kind, int left, int top, int right, int bottom,
                           int anchorCellX, int anchorCellY) {
        this(kind, left, top, right, bottom, anchorCellX, anchorCellY, anchorCellX, anchorCellY);
    }

    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
}
