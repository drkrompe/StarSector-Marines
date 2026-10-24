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
    /** Frames between the glide and the dive drawing as the wings fold back along the flanks. */
    public static final int FOLD_FRAMES = 2;

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
    public String dive() { return ROOT + folder + "/dive.png"; }

    /**
     * Wingbeat frame {@code i} of {@link #BEAT_FRAMES}; frame 0 is the glide. The beat frames share the glide's
     * canvas and pivot, and its body to the pixel: only the wings move.
     */
    public String beat(int i) { return i == 0 ? glide() : ROOT + folder + "/beat-" + i + ".png"; }

    /** Fold frame {@code i}, 1 to {@link #FOLD_FRAMES}: the wings part way from the glide to the dive. */
    public String fold(int i) { return ROOT + folder + "/fold-" + i + ".png"; }
}
