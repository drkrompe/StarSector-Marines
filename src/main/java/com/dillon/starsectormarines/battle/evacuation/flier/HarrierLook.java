package com.dillon.starsectormarines.battle.evacuation.flier;

/**
 * Which drawing a Harrier wears. Presentation only: every look flies, bites and dies the same. Three are shipped
 * while the owner picks one (round 1 in {@code art-source/rav-redesign/exploration/flier/}); the others go when
 * the pick is made.
 */
public enum HarrierLook {
    /** The Runner's lilac carapace and head; its spike arms grown into bat wings of crimson membrane. */
    LILAC_GLIDER("lilac", 2.4f),
    /** A broad plum ray whose wings are its body, with a whip tail. */
    PLUM_MANTA("manta", 2.2f),
    /** The Matriarch's brood in her charcoal and dried blood, wings of overlapping bone blades. */
    BLOOD_SCYTHE("scythe", 2.3f);

    private static final String ROOT = "graphics/battle/rav-harrier/";
    /**
     * Every drawing of every look is cut at this many pixels to a cell, so the three poses keep one body size and
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

    /**
     * The default look, the one built on until the owner picks: the manta, whose single wide silhouette reads as a
     * flier at play zoom where the bat-winged glider reads as a small dragon and the charcoal one sinks into grass.
     */
    public static final HarrierLook DEFAULT = PLUM_MANTA;

    public String glide() { return ROOT + folder + "/glide.png"; }
    public String flap() { return ROOT + folder + "/flap.png"; }
    public String dive() { return ROOT + folder + "/dive.png"; }
}
