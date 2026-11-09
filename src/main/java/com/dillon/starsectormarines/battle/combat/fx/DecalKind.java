package com.dillon.starsectormarines.battle.combat.fx;

import java.util.Random;

/**
 * Semantic names for the frames in {@code mod/graphics/decals/decals.png}, a grid of {@link #CELL_PX} px cells
 * {@link #COLUMNS} wide, written by {@code art-source/impact-decals/export.py}. A frame's index is its cell, counted
 * along rows. A kind is a run of {@link #variants} interchangeable frames from {@link #index}; a directional kind
 * is drawn along +x, the way the round travelled, so its decal is turned to the shot's heading.
 */
public enum DecalKind {
    /** Spent shell casing, scaled down at draw time. */
    SHELL_CASING(0, 1, false),
    /** Alternate shell casing pose. */
    SHELL_CASING_ALT(1, 1, false),
    /**
     * Soot fanned forward from a heavy gun's muzzle, pointing along the frame's +x with the muzzle at its left edge
     * ({@code art-source/fx/build_muzzle_scorch.py}). Faint on its own; overlapping stamps darken it.
     */
    MUZZLE_SCORCH(2, 1, true),
    /** Spitter acid splashed on the ground: a star of dark venom-green spatter. */
    ACID_SPLASH(3, 1, false),
    /** Spitter acid that landed at a slant: an elongated spatter. */
    ACID_SPLASH_LONG(4, 1, false),
    /** A settled pool of Spitter acid eating into the ground, with a scorched rim. */
    ACID_POOL(5, 1, false),

    /** Small-arms rounds in the ground: a dust pocket and spat grit, singly or in a short burst. */
    SMALL_ARMS_GROUND(8, 4, false),
    /** Small-arms pocks in a wall: a dark hole in a ring of chipped spall. */
    SMALL_ARMS_WALL(12, 4, false),
    /** A heavy round's furrow ploughed along its line of flight, clods thrown ahead. */
    HEAVY_GOUGE(16, 4, true),
    /** A heavy round's punched crater in a wall, cracks running out from it. */
    HEAVY_WALL(20, 4, false),
    /** A shallow blast spot peppered round with fragment strikes. */
    FRAG(24, 4, false),
    /** A rocket's or grenade's flat scorched blast star. */
    BLAST(28, 4, false),
    /** A shell's crater: a dark bowl, a broken rim and thrown ejecta. */
    SHELL_CRATER(32, 4, false),
    /** Loose rubble and clods lying where a blast threw them. */
    DEBRIS(36, 4, false),
    /** A pulse laser's fused scorch dot. */
    PULSE_SCORCH(40, 4, false),
    /** A beam's seared line along its sweep. */
    BEAM_BURN(44, 4, true),
    /** A thermal lance's glassy melt crater, cooled droplets round it. */
    MELT_CRATER(48, 4, false),
    /** Burned-out ground where an incendiary burned: soot to ash. */
    FIRE_SCORCH(52, 2, false),
    /** A flame-thrower's burn, widening along the jet. */
    FLAME_SWEEP(54, 2, true);

    /** Side of one frame's cell on the sheet, in pixels. */
    public static final int CELL_PX = 160;
    /** Cells along one row of the sheet. */
    public static final int COLUMNS = 8;

    /** The kind's first frame. */
    public final int index;
    /** How many frames from {@link #index} draw this kind. */
    public final int variants;
    /** Whether the frame is drawn along +x and so is turned to the shot's heading rather than at random. */
    public final boolean directional;

    DecalKind(int index, int variants, boolean directional) {
        this.index = index;
        this.variants = variants;
        this.directional = directional;
    }

    /** One of this kind's frames, picked at random. */
    public int frame(Random rng) {
        return variants == 1 ? index : index + rng.nextInt(variants);
    }

    /** Whether {@code frame} draws this kind. */
    public boolean owns(int frame) {
        return frame >= index && frame < index + variants;
    }
}
