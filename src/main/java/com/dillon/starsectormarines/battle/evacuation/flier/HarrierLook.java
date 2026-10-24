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
     * Every drawing is cut at this many pixels to a cell, so the three poses keep one body size and
     * a sprite's drawn size is its pixel size over this.
     */
    public static final float PX_PER_CELL = 64f;

    private final String folder;
    /** Wingspan in cells with the wings spread: the glide drawing's width, which the exporter cuts it to. */
    public final float spanCells;

    HarrierLook(String folder, float spanCells) {
        this.folder = folder;
        this.spanCells = spanCells;
    }

    /** The look a pack wears unless told otherwise: the lilac glider, the owner's pick (2026-10-23). */
    public static final HarrierLook DEFAULT = LILAC_GLIDER;

    public String glide() { return ROOT + folder + "/glide.png"; }
    public String flap() { return ROOT + folder + "/flap.png"; }
    public String dive() { return ROOT + folder + "/dive.png"; }
}
