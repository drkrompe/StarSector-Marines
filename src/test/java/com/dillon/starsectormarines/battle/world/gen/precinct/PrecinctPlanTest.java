package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What places a map gets, and the two ways of saying so.
 *
 * <p>The derivation is a default rather than a model of anything, so what is
 * pinned is the shape of the answer — that it moves with the world, that it
 * never produces places on top of each other, and that a mission can override
 * it outright — rather than any particular curve.
 */
class PrecinctPlanTest {

    private static final int W = 560;
    private static final int H = 336;

    private static TargetProfile world(int marketSize, int defenceLevel) {
        return new TargetProfile(marketSize, 5, defenceLevel, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /** A battle happens somewhere, whatever the world behind it is. */
    @Test
    void everyWorldGetsSomewhereToFight() {
        for (int size = 0; size <= 10; size++) {
            PrecinctPlan plan = PrecinctPlan.derive(world(size, 0), W, H, new Random(42L));
            assertTrue(!plan.precincts().isEmpty(),
                    "a size-" + size + " world produced a map with no places on it");
        }
    }

    /** Defences are what a garrison is, so a defended world has one and an open one does not. */
    @Test
    void aDefendedWorldGetsAWalledGarrison() {
        PrecinctPlan defended = PrecinctPlan.derive(world(6, 3), W, H, new Random(42L));
        assertEquals(1, defended.precincts().stream()
                .filter(p -> p.boundary() == Precinct.Boundary.WALLED).count());
        assertTrue(defended.precincts().stream().anyMatch(Precinct::isProgrammed));

        PrecinctPlan open = PrecinctPlan.derive(world(6, 0), W, H, new Random(42L));
        assertEquals(0, open.precincts().stream()
                .filter(p -> p.boundary() == Precinct.Boundary.WALLED).count(),
                "an undefended world was given a walled installation to defend");
    }

    /**
     * A bigger world is not one bigger town.
     *
     * <p>This is the whole reason for precincts rather than one settlement with
     * a larger budget, so it is asserted rather than left to the curve.
     */
    @Test
    void aBiggerWorldGetsMorePlacesNotJustABiggerOne() {
        int small = PrecinctPlan.derive(world(2, 0), W, H, new Random(42L)).precincts().size();
        int large = PrecinctPlan.derive(world(9, 0), W, H, new Random(42L)).precincts().size();
        assertTrue(large > small, "a size-9 world got " + large + " places against a size-2 "
                + "world's " + small + ", so growth of the world only enlarges one town");
    }

    /** Two places on the same spot are one place with two names. */
    @Test
    void placesAreNeverSeededOnTopOfEachOther() {
        for (long seed = 1; seed <= 20; seed++) {
            List<Precinct> places =
                    PrecinctPlan.derive(world(10, 5), W, H, new Random(seed)).precincts();
            for (int i = 0; i < places.size(); i++) {
                for (int j = i + 1; j < places.size(); j++) {
                    int dx = places.get(i).seedX() - places.get(j).seedX();
                    int dy = places.get(i).seedY() - places.get(j).seedY();
                    int gap = (int) Math.sqrt(dx * dx + dy * dy);
                    assertTrue(gap >= PrecinctPlan.MIN_SEED_SEPARATION, "seed " + seed + ": "
                            + places.get(i).name() + " and " + places.get(j).name()
                            + " are " + gap + " cells apart, under the "
                            + PrecinctPlan.MIN_SEED_SEPARATION + " that keeps them separate "
                            + "places rather than one with a pocket in it");
                }
            }
        }
    }

    /** Every seed leaves its place room to grow in both directions. */
    @Test
    void everySeedHasRoomAroundIt() {
        for (long seed = 1; seed <= 20; seed++) {
            for (Precinct place :
                    PrecinctPlan.derive(world(10, 5), W, H, new Random(seed)).precincts()) {
                assertTrue(place.seedX() > 0 && place.seedX() < W - 1
                                && place.seedY() > 0 && place.seedY() < H - 1,
                        place.name() + " was seeded off the map at "
                                + place.seedX() + "," + place.seedY());
            }
        }
    }

    /**
     * The heavier the defences the more air the garrison keeps — a rating that
     * changes nothing about the map is a rating nobody can read.
     */
    @Test
    void heavierDefencesBuyMoreAirfields() {
        int light = airfields(PrecinctPlan.derive(world(6, 1), W, H, new Random(42L)));
        int heavy = airfields(PrecinctPlan.derive(world(6, 6), W, H, new Random(42L)));
        assertTrue(heavy > light,
                "a defence rating of 6 keeps the same " + light + " airfield as a rating of 1");
    }

    private static int airfields(PrecinctPlan plan) {
        return plan.precincts().stream().filter(Precinct::isProgrammed)
                .mapToInt(p -> p.program().airfields()).sum();
    }

    /** A mission's own answer wins, including one the campaign could never derive. */
    @Test
    void anAuthoredPlanOverridesTheDerivedOne() {
        PrecinctPlan twoGarrisons = PrecinctPlan.authored(List.of(
                Precinct.garrison("north", 150, 100, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison().withAirfields(2)),
                Precinct.garrison("south", 400, 240, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison().withAirfields(0))));

        assertEquals(2, twoGarrisons.precincts().size());
        assertEquals(2, twoGarrisons.precincts().stream()
                .filter(p -> p.boundary() == Precinct.Boundary.WALLED).count(),
                "two garrisons on one map is the thing precincts exist to make expressible");
        assertEquals(2, twoGarrisons.precincts().get(0).program().airfields());
        assertEquals(0, twoGarrisons.precincts().get(1).program().airfields());
    }

    @Test
    void aMapWithNoPlacesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PrecinctPlan.authored(List.of()));
    }
}
