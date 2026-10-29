package com.dillon.starsectormarines.battle.vehicle;

/**
 * The finish of the turret mount ring a hull's gun stands on, by the hull's class (owner, 2026-10-29): the army hulls
 * stand on a crude olive-drab ring, the silver hover hulls on a sleek brushed-silver one, and the futuristic laser
 * tank on the white one that also carries its turning collar and laser bases ({@code vehicle-card-sizes.md}).
 */
public enum TurretMountStyle {
    /** The low end: olive-drab, bolted and chipped, for the army hulls. */
    GREEN("gun-mount-green.png"),
    /** The middle: brushed silver with cyan edge lights, for the silver hover hulls. */
    SILVER("gun-mount-silver.png"),
    /** The top: white ceramic and graphite machinery, for the laser tank. */
    WHITE("gun-mount-white.png");

    /** The ring's sprite, under {@code graphics/battle/vehicles/}. */
    public final String ringTexture;

    TurretMountStyle(String ringTexture) {
        this.ringTexture = ringTexture;
    }
}
