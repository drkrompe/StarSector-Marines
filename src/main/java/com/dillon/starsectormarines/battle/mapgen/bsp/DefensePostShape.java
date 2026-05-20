package com.dillon.starsectormarines.battle.mapgen.bsp;

import java.util.Random;

/**
 * Visual variant for a LARGE-tier defense post. Picked randomly per placement
 * so the kill zone reads as a built-up line of distinct emplacements rather
 * than a row of clones. All shapes fit a 5×3 or 3×5 bbox; the stamper picks
 * the bbox via {@link #halfX}/{@link #halfY} for footprint validation and the
 * partition-connectivity check.
 *
 * <ul>
 *   <li>{@link #LINE_H} — current horizontal embankment line, 2 turrets E/W.
 *       Uses the standard {@code turretEmbankment} art (block 2). Best read
 *       for a post oriented perpendicular to a N/S attack axis.</li>
 *   <li>{@link #LINE_V} — rotated 90°: 2 turrets N/S, 3×5 footprint. Visually
 *       distinct from LINE_H, useful flavor for any attack axis.</li>
 *   <li>{@link #WEDGE} — chevron silhouette with a 1-cell apex pointing south
 *       and a wide back row. Single center turret. Uses the chunkier
 *       {@code turretBowOut} art so the protruding apex reads as a heavy
 *       earthwork.</li>
 *   <li>{@link #TRAPEZOID} — narrower south, wider north, 2 turrets E/W of
 *       center. Same {@code turretBowOut} art as WEDGE — both "protruding"
 *       silhouettes share the heavier read.</li>
 *   <li>{@link #TRIANGLE_FORMATION} — three turrets arranged in a spearhead
 *       (2 back, 1 forward apex) with minimal flanking walls. Concentrates
 *       firepower at a single placement rather than spreading it along an
 *       embankment line.</li>
 * </ul>
 *
 * <p>LIGHT and MEDIUM tiers don't use this enum — they keep their simple
 * 4-cell vent ring and 8-cell sandbag ring respectively. Shape variety on
 * the smaller tiers wouldn't read at their scale.
 */
public enum DefensePostShape {

    LINE_H            (2, 1),
    LINE_V            (1, 2),
    WEDGE             (2, 1),
    TRAPEZOID         (2, 1),
    TRIANGLE_FORMATION(2, 1);

    /** Footprint half-extent on the X axis. Full footprint width is {@code 2 * halfX + 1}. */
    public final int halfX;
    /** Footprint half-extent on the Y axis. Full footprint height is {@code 2 * halfY + 1}. */
    public final int halfY;

    DefensePostShape(int halfX, int halfY) {
        this.halfX = halfX;
        this.halfY = halfY;
    }

    private static final DefensePostShape[] LARGE_SHAPES = values();

    /** Uniform-random pick from the LARGE-tier shape pool. */
    public static DefensePostShape pickForLarge(Random rng) {
        return LARGE_SHAPES[rng.nextInt(LARGE_SHAPES.length)];
    }
}
