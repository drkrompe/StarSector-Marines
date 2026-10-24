package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * Which drawing a Harrier wears. Presentation only: every look flies, bites and dies the same. One is shipped, the
 * owner's pick from round 1 ({@code art-source/rav-redesign/exploration/flier/}); a later strain adds its own here.
 */
public enum HarrierLook {
    /** The Runner's lilac carapace and head; its spike arms grown into bat wings of crimson membrane. */
    LILAC_GLIDER("lilac", 2.4f);

    private static final String ROOT = "graphics/battle/rav-harrier/";
    /**
     * Every drawing is cut at this many pixels to a cell, so every frame keeps one body size and
     * a sprite's drawn size is its pixel size over this.
     */
    public static final float PX_PER_CELL = 64f;
    /** Frames in one wingbeat: frame 0 is the glide drawing, then up, over the top, down and back. */
    public static final int BEAT_FRAMES = 8;
    /** Frames between the glide and the dive drawing as the wings sweep back. */
    public static final int FOLD_FRAMES = 2;
    /** Frames a shot flier tumbles through as it falls. */
    public static final int FALL_FRAMES = 4;
    /** Drawings of a dead flier on the ground. */
    public static final int CORPSE_VARIANTS = 2;
    /**
     * Where the body's segments hinge, in exported pixels behind the canvas centre (negative: ahead of it): the
     * neck, the waist, and the tail's root and two joints. Every segment shares the frames' canvas, so its centre
     * is the body's anchor; {@code export_harrier.py} prints these.
     */
    public static final float NECK_PX = -21.62f, WAIST_PX = 3.52f;
    public static final float[] TAIL_PX = {28.66f, 39.12f, 49.30f};

    private final String folder;
    /** Wingspan in cells with the wings spread: the glide drawing's width, which the exporter scales it to. */
    public final float spanCells;

    HarrierLook(String folder, float spanCells) {
        this.folder = folder;
        this.spanCells = spanCells;
    }

    /** The look a pack wears unless told otherwise: the lilac glider, the owner's pick (2026-10-23). */
    public static final HarrierLook DEFAULT = LILAC_GLIDER;

    public String glide() { return ROOT + folder + "/glide.png"; }
    /** The dive: wings swept back along the flanks, still open, so the wings read on the way down. */
    public String dive() { return ROOT + folder + "/dive.png"; }

    /**
     * Wingbeat frame {@code i} of {@link #BEAT_FRAMES}; frame 0 is the glide. The beat frames share the glide's
     * canvas and pivot, and its body to the pixel: only the wings move.
     */
    public String beat(int i) { return i == 0 ? glide() : ROOT + folder + "/beat-" + i + ".png"; }

    /** The body's segments, which the live frames leave off: only the thorax rides with the wings. */
    public String head() { return ROOT + folder + "/head.png"; }
    public String abdomen() { return ROOT + folder + "/abdomen.png"; }
    /** Tail segment {@code i}, 1 (at the root) to 3 (the tip). */
    public String tail(int i) { return ROOT + folder + "/tail-" + i + ".png"; }

    /** Fold frame {@code i}, 1 to {@link #FOLD_FRAMES}: the wings part way from the glide to the dive. */
    public String fold(int i) { return ROOT + folder + "/fold-" + i + ".png"; }

    /** Fall frame {@code i}, 1 to {@link #FALL_FRAMES}: limp wings thrown about by the tumble. */
    public String fall(int i) { return ROOT + folder + "/fall-" + i + ".png"; }

    /** Corpse drawing {@code variant}, 0 to {@link #CORPSE_VARIANTS} - 1: wings sprawled and crumpled on the ground. */
    public String corpse(int variant) { return ROOT + folder + "/corpse-" + (variant + 1) + ".png"; }

    /** The corpse sprite index that names this look's drawing {@code variant} ({@link #corpseAt}). */
    public int corpseIndex(int variant) { return ordinal() * CORPSE_VARIANTS + variant; }

    /** The corpse drawing a Harrier corpse's sprite index names ({@link #corpseIndex}). */
    public static String corpseAt(int index) {
        HarrierLook[] looks = values();
        HarrierLook look = looks[Math.floorMod(index / CORPSE_VARIANTS, looks.length)];
        return look.corpse(Math.floorMod(index, CORPSE_VARIANTS));
    }
}
