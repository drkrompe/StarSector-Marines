package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.Climate.Hazard;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which worlds build which settlements ({@link HabitatZoning}), and what a habitat rebuilds ({@link Habitat}). */
class HabitatZoningTest {

    private static Climate air(Hazard... hazards) {
        EnumSet<Hazard> set = EnumSet.noneOf(Hazard.class);
        for (Hazard h : hazards) set.add(h);
        return new Climate(set);
    }

    @Test
    void radiationShieldsATownEvenWhereTheAirAlsoKills() {
        assertEquals(Habitat.SHIELDED, HabitatZoning.forWorld(SurfacePalette.ROCK, air(Hazard.RADIATION)));
        assertEquals(Habitat.SHIELDED, HabitatZoning.forWorld(SurfacePalette.ROCK, air(Hazard.RADIATION, Hazard.TOXIC)));
    }

    @Test
    void airThatKillsOrIsNotThereSealsATown() {
        assertEquals(Habitat.SEALED, HabitatZoning.forWorld(SurfacePalette.ROCK, air(Hazard.TOXIC)));
        assertEquals(Habitat.SEALED, HabitatZoning.forWorld(SurfacePalette.FROZEN, air(Hazard.AIRLESS)));
    }

    @Test
    void aWorldWithNoHazardOrNoMarketBuildsTheOpenTown() {
        assertEquals(Habitat.OPEN, HabitatZoning.forWorld(SurfacePalette.ARID, air(Hazard.STORMS, Hazard.QUAKES)));
        assertEquals(Habitat.OPEN, HabitatZoning.forWorld(SurfacePalette.ROCK, null));
        assertEquals(Habitat.OPEN, HabitatZoning.forProfile(null));
        assertEquals(Habitat.OPEN, HabitatZoning.forProfile(TargetProfile.NEUTRAL));
    }

    @Test
    void aHabitatRebuildsOnlyTownWalls() {
        assertEquals(Habitat.SHIELD_WALL, Habitat.SHIELDED.wallFor("colony.masonry-wall"));
        assertEquals("colony.ceramic-wall", Habitat.SEALED.wallFor("colony.masonry-wall"));
        assertEquals("colony.olive-wall", Habitat.SEALED.wallFor("colony.olive-wall"));
        // A compound's perimeter, which other rules read by its surface, is never rebuilt.
        assertEquals("installation.dark-concrete-wall", Habitat.SHIELDED.wallFor("installation.dark-concrete-wall"));
        assertEquals("colony.masonry-wall", Habitat.OPEN.wallFor("colony.masonry-wall"));
    }
}
