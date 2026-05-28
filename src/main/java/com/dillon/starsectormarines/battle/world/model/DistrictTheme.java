package com.dillon.starsectormarines.battle.world.model;

/**
 * Per-block flavor that biases what gets generated inside the footprint. Each
 * (row, col) cell of the block grid picks a theme; the theme controls which
 * {@link PointOfInterest.Kind} the building takes, which doodads scatter
 * inside, and whether plaza cells become LZ pads.
 *
 * <p>Today's bias is heavy-handed (RESIDENTIAL blocks always pick RESIDENTIAL
 * POIs, WAREHOUSE always picks DEPOT). That reads as "every block has a clear
 * identity" which is the visual goal — it can soften to weighted mixes later
 * without changing the call sites.
 */
public enum DistrictTheme {

    /** Homes — small chairs, chests, decor doodads. Always RESIDENTIAL POIs. */
    RESIDENTIAL,
    /** Cargo / storage — heavy crates, fewer seats. Always DEPOT POIs. */
    WAREHOUSE,
    /** Commercial spaceport — control towers + cargo handling. Plaza cells get LZ pads. */
    SKY_PORT,
    /** Mixed-use — falls through to the default kind-weight table and full doodad pool. */
    MIXED;
}
