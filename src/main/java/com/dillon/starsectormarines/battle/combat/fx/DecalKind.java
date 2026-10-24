package com.dillon.starsectormarines.battle.combat.fx;

import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetSlicer;

/**
 * Semantic names for the frames in {@code mod/graphics/decals/decals.png}.
 * Sheet is a horizontal strip 160 px tall, appended to on the right; frame index in this enum matches the
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
    RUBBLE_FIRE(12),
    /**
     * Soot fanned forward from a heavy gun's muzzle, pointing along the frame's +x with the muzzle at its left edge
     * ({@code art-source/fx/build_muzzle_scorch.py}). Faint on its own; overlapping stamps darken it.
     */
    MUZZLE_SCORCH(13),
    /**
     * Spitter acid splashed on the ground: a star of dark venom-green spatter. This and the next two are appended by
     * {@code art-source/rav-redesign/spitter/export_spitter_rig.py}.
     */
    ACID_SPLASH(14),
    /** Spitter acid that landed at a slant: an elongated spatter. */
    ACID_SPLASH_LONG(15),
    /** A settled pool of Spitter acid eating into the ground, with a scorched rim. */
    ACID_POOL(16);

    public final int index;
    DecalKind(int index) { this.index = index; }
}
