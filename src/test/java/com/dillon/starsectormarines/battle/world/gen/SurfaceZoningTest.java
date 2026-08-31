package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SurfaceZoning} is the only place vanilla planet type ids are named on
 * the battle side of the bridge, so what it does with an id it has never seen
 * matters as much as what it does with the ones it has.
 */
class SurfaceZoningTest {

    @Test
    void livingWorldsAreVerdant() {
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("terran"));
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("terran-eccentric"));
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("jungle"));
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("tundra"));
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("water"));
    }

    @Test
    void dryWorldsAreArid() {
        assertEquals(SurfacePalette.ARID, SurfaceZoning.forPlanetType("desert"));
        assertEquals(SurfacePalette.ARID, SurfaceZoning.forPlanetType("arid"));
    }

    /** The worlds the Sector actually fights over. None of them grow anything. */
    @Test
    void deadWorldsAreRock() {
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("barren"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("barren-bombarded"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("barren_castiron"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("toxic"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("irradiated"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("lava_minor"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("cryovolcanic"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("rocky_ice"));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("frozen"));
    }

    /**
     * Silence must not claim habitability. An unknown type, a station with no
     * planet behind it, and a battle with no world at all all read as rock.
     */
    @Test
    void theUnknownIsRockRatherThanGreen() {
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType(null));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType(""));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("   "));
        assertEquals(SurfacePalette.ROCK, SurfaceZoning.forPlanetType("some_modded_type"));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals(SurfacePalette.VERDANT, SurfaceZoning.forPlanetType("TERRAN"));
        assertEquals(SurfacePalette.ARID, SurfaceZoning.forPlanetType("Desert"));
    }

    /** A profile built with no surface at all must not silently become a garden. */
    @Test
    void aNullSurfaceNormalizesToRock() {
        TargetProfile p = new TargetProfile(5, 5, 1, 1, "hegemony", Set.of(), null,
                SettlementLink.ROAD);
        assertEquals(SurfacePalette.ROCK, p.surface());
        assertEquals(SurfacePalette.ROCK, TargetProfile.NEUTRAL.surface());
    }
}
