package com.dillon.starsectormarines.battle.world.gen;

/**
 * High-level zoning label for a region of the map. Where {@link BlockKind} is
 * per-BSP-leaf and {@link MapDistrictTheme} is per-district, {@code BiomeKind}
 * is per-large-region — the layer that says "this whole strip of the map is
 * the beach the attacker lands on, that strip is the harbor, the far end is
 * the fortress district."
 *
 * <p>Used by {@link com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap}
 * to lay percentile bands along a {@link TraversalAxis} so a single map can
 * span multiple themed regions in a deliberate progression (beach → port →
 * city → fortress) rather than scattering themes uniformly. Each biome maps
 * to a primary {@link MapDistrictTheme}; per-cell theme weights drive
 * {@link BlockKind} selection inside each biome's footprint.
 */
public enum BiomeKind {

    /** Attacker-side coast. Mostly open SAND with sparse defenses; the map-edge water lives here. */
    BEACH,

    /** Harbor and docks. Industrial + commercial mix with warehouses and yards; transitions inland from the beach. */
    PORT,

    /** Civilian urban core. Mixed residential / commercial / civic — the main city sprawl between the harbor and the military district. */
    CITY,

    /** Defender stronghold at the far end of the axis. Dense military compounds, fortified posts, depots. Becomes the walled fortress in the super-wall pass. */
    FORTRESS_DISTRICT,

    /** Sparse fallback used for transition zones or off-axis flanks. Wasteland, scattered structures. */
    OUTSKIRTS,
}
