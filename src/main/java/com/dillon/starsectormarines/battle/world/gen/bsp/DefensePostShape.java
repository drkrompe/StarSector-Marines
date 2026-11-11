package com.dillon.starsectormarines.battle.world.gen.bsp;

import java.util.Random;

/**
 * Visual variant for a LARGE-tier defense post. Picked randomly per placement
 * so the kill zone reads as a built-up line of distinct emplacements rather
 * than a row of clones. This enum is only a compatibility key for callers
 * that request one shipped variant; catalog data owns its bounds, cells, art,
 * and turret composition.
 *
 * <ul>
 *   <li>{@link #LINE_H} — current horizontal embankment line, 2 turrets E/W.
 *       Uses the standard {@code turretEmbankment} art (block 2). Best read
 *       for a post oriented perpendicular to a N/S attack axis.</li>
 *   <li>{@link #LINE_V} — rotated 90°: 2 turrets N/S, 3×5 footprint. Visually
 *       distinct from LINE_H, useful flavor for any attack axis.</li>
 *   <li>{@link #TRIANGLE_FORMATION} — a walled gun pit, 5x5: a closed concrete
 *       parapet round a paved floor, its three turrets set inside in a
 *       spearhead clear of the rim. Concentrates firepower at one placement.
 *       It was once a spearhead on a broken U of walls that read as a scrap of
 *       wall, with no front and the guns crowding its rim (owner, 2026-11-11:
 *       "a bad design and looks bad").</li>
 * </ul>
 *
 * <p>LIGHT and MEDIUM tiers don't use this enum — they keep their simple
 * four-parapet cross and 8-cell ring respectively. Shape variety on
 * the smaller tiers wouldn't read at their scale.
 */
public enum DefensePostShape {

    LINE_H            ("line-h"),
    LINE_V            ("line-v"),
    TRIANGLE_FORMATION("triangle-formation"),
    /** One raised gun on a 3x3 plinth with a paved apron round it ({@code ground-turret-tiers.md}). */
    HARDPOINT         ("hardpoint");

    /** Stable compatibility key for the data-authored LARGE layout variant. */
    public final String key;

    DefensePostShape(String key) {
        this.key = key;
    }

    private static final DefensePostShape[] LARGE_SHAPES = values();

    /** Uniform-random pick from the LARGE-tier shape pool. */
    public static DefensePostShape pickForLarge(Random rng) {
        return LARGE_SHAPES[rng.nextInt(LARGE_SHAPES.length)];
    }
}
