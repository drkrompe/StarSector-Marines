package com.dillon.starsectormarines.battle.world.gen;

/**
 * A basin of other ground in the middle of a map: a floor of {@link #floor} out to an ellipse whose half-axes are
 * {@link #reachX} and {@link #reachY} shares of the map's half-width and half-height, and the world's own
 * {@link TargetProfile#surface} beyond it, the rim. Rav Bloom country on a desert world is a green basin with an arid
 * rim (Asharu): the floor is wooded and watered, the rim is the desert. {@link SurfaceField} reads it per cell.
 *
 * <p>A reach above one puts the floor past the map's edge along that axis, so the rim shows only in the corners.
 *
 * @param floor  the basin floor's palette, or null for no basin: the world's surface everywhere
 * @param reachX the floor's half-width as a share of the map's
 * @param reachY the floor's half-height as a share of the map's
 */
public record Basin(SurfacePalette floor, float reachX, float reachY) {

    /** No basin: the whole map is the world's own surface. */
    public static final Basin NONE = new Basin(null, 0f, 0f);

    public Basin {
        if (floor != null && (!(reachX > 0f) || !(reachY > 0f))) {
            throw new IllegalArgumentException("A basin needs a positive reach: " + reachX + ", " + reachY);
        }
    }

    /**
     * Rav Bloom country on a world of {@code world}: a verdant floor on a desert world, and none on any other, where
     * the bloom's country is the world's own ground.
     */
    public static Basin ravBloom(SurfacePalette world, float reachX, float reachY) {
        return world == SurfacePalette.ARID ? new Basin(SurfacePalette.VERDANT, reachX, reachY) : NONE;
    }

    /** Whether there is a floor different from the rim at all. */
    public boolean present() {
        return floor != null;
    }
}
