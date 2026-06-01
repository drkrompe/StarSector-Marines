package com.dillon.starsectormarines.battle.world.gen.taxonomy;

/**
 * A region's position along the conquest traversal axis — the indoor-agnostic
 * "assault gradient". Computed geometrically from the region centroid's
 * normalized distance from the attacker-facing edge, NOT from a connectivity
 * graph: the leaf-adjacency graph is trunk-segmented and the porous walkable
 * blob has no real chokepoints, so geometry is the only honest depth signal
 * (and it crosses trunks correctly, which a graph BFS would not).
 *
 * <p>{@link #UNSET} on legacy (no-axis) maps, where there is no attacker edge
 * to measure from.
 */
public enum DepthBand {
    /** Attacker-side — the landing approach. {@code depth01 < 0.30}. */
    FORWARD,
    /** The contested middle of the push. {@code 0.30 ≤ depth01 < 0.60}. */
    MID,
    /** Defender-side approaches — near the keep/fortress. {@code 0.60 ≤ depth01 < 0.85}. */
    DEEP,
    /** The defender's rear — behind the last line. {@code depth01 ≥ 0.85}. */
    REAR,
    /** No traversal axis (legacy district maps) — depth is undefined. */
    UNSET;

    /** Band a normalized {@code [0,1]} depth, or {@link #UNSET} for a negative (axis-absent) value. */
    public static DepthBand fromDepth01(float depth01) {
        if (depth01 < 0f)    return UNSET;
        if (depth01 < 0.30f) return FORWARD;
        if (depth01 < 0.60f) return MID;
        if (depth01 < 0.85f) return DEEP;
        return REAR;
    }
}
