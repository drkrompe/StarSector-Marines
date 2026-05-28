package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.sprites.SpriteSheetSlicer;

/**
 * Semantic names for the 13 frames in {@code mod/graphics/decals/decals.png}.
 * Sheet is a 2048×160 horizontal strip; frame index in this enum matches the
 * sliced frame index from {@link SpriteSheetSlicer}.
 *
 * <p>Use these to pick a decal kind by intent ("a small bullet hole") rather
 * than by magic number ({@code 0}).
 */
public enum DecalKind {
    /** Single small bullet hole — rifle / vulcan / SMG round on a wall. */
    BULLET_HOLE_SINGLE(0),
    /** Cluster of small bullet holes. */
    BULLET_HOLE_MULTI(1),
    /** Single larger hole — kinetic shell impact (arbalest, flak, hephaestus, DMR). */
    BULLET_HOLE_LARGE_SINGLE(2),
    /** Cluster of larger holes. */
    BULLET_HOLE_LARGE_MULTI(3),
    /** Spent shell casing — small, scaled down at draw time. Reserved for future shooter-side spawn. */
    SHELL_CASING(4),
    /** Alternate shell casing pose. */
    SHELL_CASING_ALT(5),
    /** Small crater on a floor cell — light kinetic / rifle round. */
    CRATER_SMALL(6),
    /** Medium crater — DMR / kinetic turret round. */
    CRATER_MEDIUM_A(7),
    /** Medium crater alt. */
    CRATER_MEDIUM_B(8),
    /** Small crater alt — yellow/brown burst pattern. */
    CRATER_SMALL_ALT(9),
    /** Pile of rubble — HE detonation aftermath. */
    RUBBLE(10),
    /** Alternate rubble pile. */
    RUBBLE_ALT(11),
    /** Rubble with ongoing fire — heavy HE detonation. Visually busier; use sparingly. */
    RUBBLE_FIRE(12);

    public final int index;
    DecalKind(int index) { this.index = index; }
}
