package com.dillon.starsectormarines.battle.world.tiles;

/**
 * Gameplay cover bucket of a {@link TileDef}. Nature overlays publish this
 * authored quality into the battle's physical-cover profile: units prefer
 * firing positions beside it, and a round crossing the overlay cell may be
 * intercepted when its target-plane height intersects the default silhouette.
 */
public enum TileCover {
    NONE(0, 0f),
    LIGHT(1, 0.18f),
    MED(2, 0.38f),
    HEAVY(3, 0.60f);

    private final int level;
    private final float defaultBallisticHalfHeight;

    TileCover(int level, float defaultBallisticHalfHeight) {
        this.level = level;
        this.defaultBallisticHalfHeight = defaultBallisticHalfHeight;
    }

    /** Cover quality on the shared {@code [0..3]} tactical-cover scale. */
    public int level() {
        return level;
    }

    /** Default symmetric target-plane silhouette used for ray interception. */
    public float defaultBallisticHalfHeight() {
        return defaultBallisticHalfHeight;
    }

    /** Case-insensitive parse from {@code none|light|med|heavy}. */
    public static TileCover fromJson(String s) {
        return TileCover.valueOf(s.trim().toUpperCase());
    }
}
