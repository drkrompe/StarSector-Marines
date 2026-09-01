package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a zoned place is on the inside, and the two ways of saying so.
 *
 * <p>A programmed precinct has a program; a zoned one had nothing, and three
 * places on one map came out the same mix. A character is the missing
 * statement, in the same authored-wins shape as the rest of the plan.
 */
class PrecinctCharacterTest {

    private static final int W = 560;
    private static final int H = 336;

    private static TargetProfile world(int defenceLevel, EconomicFunction... functions) {
        return new TargetProfile(9, 5, defenceLevel, 1, "independent",
                EnumSet.of(functions[0], functions),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /** Every zoned place says what it is; a programmed one says it with its program. */
    @Test
    void aZonedPlaceIsAlwaysSomeKindOfPlace() {
        PrecinctPlan plan = PrecinctPlan.derive(world(5, EconomicFunction.HABITATION),
                W, H, new Random(42L));
        for (Precinct precinct : plan.precincts()) {
            if (precinct.isProgrammed()) {
                assertNull(precinct.character(), precinct.name()
                        + " is packed from its program and has nothing to be themed from");
            } else {
                assertNotNull(precinct.character(), precinct.name()
                        + " is zoned and says nothing about what it is");
            }
        }
    }

    /** The main settlement has a centre of its own; the places around it do not. */
    @Test
    void theTownHasACentreAndTheHamletsDoNot() {
        PrecinctPlan plan = PrecinctPlan.derive(world(0, EconomicFunction.HABITATION),
                W, H, new Random(42L));
        Precinct town = plan.precincts().get(0);
        assertEquals("settlement", town.name());
        assertTrue(town.character().hasCentre(), "the town has no centre");
        assertEquals(MapDistrictTheme.CIVIC, town.character().centre());
        for (Precinct outlying : plan.precincts().subList(1, plan.precincts().size())) {
            assertTrue(!outlying.character().hasCentre(),
                    outlying.name() + " is an outlying place with a civic core");
        }
    }

    /** The world's economy reaches its town, which is what the economy signal is for. */
    @Test
    void anIndustrialWorldsTownLeansIndustrial() {
        PrecinctCharacter homes = PrecinctPlan.derive(world(0, EconomicFunction.HABITATION),
                W, H, new Random(42L)).precincts().get(0).character();
        PrecinctCharacter works = PrecinctPlan.derive(world(0, EconomicFunction.HEAVY_INDUSTRY),
                W, H, new Random(42L)).precincts().get(0).character();
        int homesShare = homes.mix().getOrDefault(MapDistrictTheme.INDUSTRIAL, 0);
        int worksShare = works.mix().getOrDefault(MapDistrictTheme.INDUSTRIAL, 0);
        assertTrue(worksShare > homesShare, "an industrial world's town gives industry "
                + worksShare + " shares against a residential world's " + homesShare);
    }

    /**
     * What a place is never moves where it is. Character draws come after every
     * seed, so the same world lays out the same map whatever it is built of.
     */
    @Test
    void whatAPlaceIsDoesNotMoveWhereItIs() {
        List<Precinct> homes = PrecinctPlan.derive(world(5, EconomicFunction.HABITATION),
                W, H, new Random(7L)).precincts();
        List<Precinct> works = PrecinctPlan.derive(world(5, EconomicFunction.MINING),
                W, H, new Random(7L)).precincts();
        assertEquals(homes.size(), works.size());
        for (int i = 0; i < homes.size(); i++) {
            assertEquals(homes.get(i).seedX(), works.get(i).seedX(), homes.get(i).name());
            assertEquals(homes.get(i).seedY(), works.get(i).seedY(), homes.get(i).name());
        }
    }

    /** A mission's own answer wins, and a place told nothing is a town. */
    @Test
    void aStatedCharacterWins() {
        Precinct depot = Precinct.settlement("x", 100, 100, GrownTrunkPlan.Profile.hamlet(),
                PrecinctCharacter.DEPOT);
        assertSame(PrecinctCharacter.DEPOT, depot.character());
        assertSame(PrecinctCharacter.TOWN,
                Precinct.settlement("y", 100, 100, GrownTrunkPlan.Profile.hamlet()).character());

        Precinct resolved = PrecinctBrief.settlement("z", MapPlacement.NORTH,
                GrownTrunkPlan.Profile.hamlet(), PrecinctCharacter.SUBURB)
                .resolve(W, H, 30, new Random(1L));
        assertSame(PrecinctCharacter.SUBURB, resolved.character(),
                "a brief lost its character on the way to being a precinct");
    }

    /** A zoned place with nothing said about it has nothing to theme its parcels from. */
    @Test
    void aZonedPlaceWithoutACharacterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Precinct("x", 10, 10,
                GrownTrunkPlan.Profile.hamlet(), null, Precinct.Boundary.OPEN, null, null));
    }

    /** A programmed place is packed, so a character would say nothing and is a mistake. */
    @Test
    void aProgrammedPlaceWithACharacterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Precinct("x", 10, 10,
                GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison(),
                Precinct.Boundary.WALLED, Fortification.GARRISON, PrecinctCharacter.TOWN));
    }

    /** A character with nothing in its mix is hinterland, which is not a character. */
    @Test
    void aCharacterBuildsSomething() {
        assertThrows(IllegalArgumentException.class,
                () -> new PrecinctCharacter("nothing", Map.of(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PrecinctCharacter("nothing", Map.of(MapDistrictTheme.CIVIC, 0), null));
    }

    /** No signal changes nothing, so a caller holding a maybe-neutral economy need not branch. */
    @Test
    void leaningOnNothingIsTheSameCharacter() {
        assertEquals(PrecinctCharacter.TOWN, PrecinctCharacter.TOWN.leaning(null));
        assertTrue(!PrecinctCharacter.TOWN.equals(
                PrecinctCharacter.TOWN.leaning(MapDistrictTheme.INDUSTRIAL)));
    }
}
