package com.dillon.starsectormarines.battle.world.gen;

import java.util.Locale;

/**
 * The selection-layer policy that turns a vanilla planet type id into the
 * {@link SurfacePalette} the generator paints wild ground with — the terrain
 * counterpart to {@link EconomicZoning}, and the only place planet type ids are
 * named on the battle side of the bridge.
 *
 * <p>It takes a {@link String} rather than a {@code PlanetAPI} on purpose:
 * {@code battle.world.gen} must compile and run headless, so the resolver
 * distills the type id at the boundary and this policy stays testable without a
 * sector. Ids are matched by substring against the lowercased type because
 * vanilla and mods both spell families out with suffixes — {@code barren},
 * {@code barren-bombarded}, {@code barren_castiron}, {@code lava_minor} — and
 * enumerating every variant would silently miss the next one.
 *
 * <p>An unknown type resolves to {@link SurfacePalette#ROCK}, which is also the
 * baseline for a battle with no planet behind it at all. Guessing green for a
 * world nobody described would assert habitability the Sector rarely has.
 */
public final class SurfaceZoning {

    private SurfaceZoning() {}

    /**
     * The palette a planet type reads as. Ordered most specific first: a
     * {@code water} world is living, while {@code rocky_ice} is not, and both
     * contain a family name that appears in other ids.
     *
     * @param planetTypeId vanilla planet type id, or {@code null} when nothing
     *                     is known about the target.
     */
    public static SurfacePalette forPlanetType(String planetTypeId) {
        if (planetTypeId == null || planetTypeId.isBlank()) return SurfacePalette.ROCK;
        String id = planetTypeId.toLowerCase(Locale.ROOT);

        // Living worlds. "terran-eccentric" and "US_ocean" both land here.
        if (id.contains("terran") || id.contains("jungle") || id.contains("ocean")
                || id.contains("tundra") || id.contains("water")) {
            return SurfacePalette.VERDANT;
        }
        // Dry but weathered — dust and sand rather than bare stone.
        if (id.contains("desert") || id.contains("arid")) {
            return SurfacePalette.ARID;
        }
        // Everything else the Sector actually fights over: barren, rocky,
        // toxic, irradiated, lava, cryovolcanic, frozen. Frozen reads as stone
        // rather than ice because there is no snow art in the project yet.
        return SurfacePalette.ROCK;
    }
}
