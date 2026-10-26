package com.dillon.starsectormarines.battle.vehicle;

/**
 * A hull's momentum spent on ground it breaks. A moving hull carries kinetic energy, half its mass in tonnes times
 * its speed in cells a second squared, and {@link #STRUCTURE_PER_ENERGY} turns that into structure: what the hull
 * could break by driving into it. What it breaks costs it the energy that structure took, so it drives on at the
 * speed what is left gives, slowed in proportion to what gave rather than stopped dead: a dozer at full speed bulls
 * through a crate barricade barely checked and comes up short at a city wall, and a tank flattens a sapling almost
 * without noticing it where an APC lurches.
 */
public final class HullMomentum {
    private HullMomentum() {}

    /**
     * Structure a tonne-cell²/s² of kinetic energy breaks. A dozer at its top speed carries 360: one cell of crate
     * barricade (100) leaves it most of its way, a row of housing wall across its blade (four cells at 80) about half,
     * and a row of city wall (four at 100) stops it just short, for its blade to finish.
     */
    public static final float STRUCTURE_PER_ENERGY = 10.4f;

    /** The structure a hull of {@code tonnes} at {@code speed} cells a second can break; none going backwards. */
    public static float structure(float tonnes, float speed) {
        if (speed <= 0f) return 0f;
        return STRUCTURE_PER_ENERGY * .5f * tonnes * speed * speed;
    }

    /** The speed a hull of {@code tonnes} at {@code speed} keeps after breaking {@code structure}; none once spent. */
    public static float speedAfter(float tonnes, float speed, float structure) {
        if (speed <= 0f) return speed;
        float left = speed * speed - 2f * structure / (STRUCTURE_PER_ENERGY * tonnes);
        return left <= 0f ? 0f : (float) Math.sqrt(left);
    }
}
