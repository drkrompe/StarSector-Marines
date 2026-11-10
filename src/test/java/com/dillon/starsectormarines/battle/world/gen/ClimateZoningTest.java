package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.Climate.Hazard;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which worlds storm, and which have no weather at all ({@link ClimateZoning}). */
class ClimateZoningTest {

    @Test
    void aDesertWithAirIsADustWorld() {
        assertTrue(ClimateZoning.forConditions(List.of("hot", "thin_atmosphere"), SurfacePalette.ARID).has(Hazard.STORMS));
    }

    @Test
    void extremeWeatherStormsOnAnyGround() {
        assertTrue(ClimateZoning.forConditions(List.of("extreme_weather"), SurfacePalette.VERDANT).has(Hazard.STORMS));
        assertFalse(ClimateZoning.forConditions(List.of("habitable"), SurfacePalette.VERDANT).has(Hazard.STORMS));
    }

    @Test
    void onlyExtremeColdBlowsAFrozenWorld() {
        assertTrue(ClimateZoning.forConditions(List.of("very_cold"), SurfacePalette.FROZEN).has(Hazard.STORMS));
        assertFalse(ClimateZoning.forConditions(List.of("cold"), SurfacePalette.FROZEN).has(Hazard.STORMS));
    }

    @Test
    void anAirlessWorldHasNoWeatherWhateverItsGround() {
        Climate airless = ClimateZoning.forConditions(List.of("no_atmosphere", "very_cold"), SurfacePalette.ARID);
        assertTrue(airless.has(Hazard.AIRLESS));
        assertFalse(airless.has(Hazard.STORMS));
    }

    @Test
    void theAirAndTheGroundAreReadAsTheyAre() {
        Climate c = ClimateZoning.forConditions(
                List.of("toxic_atmosphere", "irradiated", "extreme_tectonic_activity"), SurfacePalette.ROCK);
        assertTrue(c.has(Hazard.TOXIC) && c.has(Hazard.RADIATION) && c.has(Hazard.QUAKES));
        assertFalse(c.has(Hazard.STORMS));
        assertTrue(ClimateZoning.forConditions(null, SurfacePalette.ROCK).hazards().isEmpty());
    }
}
