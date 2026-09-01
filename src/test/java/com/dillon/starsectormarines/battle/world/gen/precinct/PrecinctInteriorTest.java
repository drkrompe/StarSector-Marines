package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A zoned precinct's character reaches what is built in it.
 *
 * <p>Through the generator, because the claim is about the whole chain — the
 * character is stated on the plan, laid over the district map, read by the
 * labeller and built by the fillers — and the unit tests pin each link without
 * being able to show that a depot comes out full of depots. Two places on one
 * small map, attributed to the nearer seed the way the original measurement
 * was; the country between them holds nothing, so the attribution is exact
 * enough for a categorical reading.
 *
 * <p>Both directions are asserted. Different characters must differ, and the
 * same character must agree with itself, because a change that merely added
 * noise would pass the first alone.
 */
class PrecinctInteriorTest {

    private static final int W = 280;
    private static final int H = 168;

    /** No spaceport and no defences, so nothing but the two characters shapes the mix. */
    private static TargetProfile world() {
        return new TargetProfile(9, 5, 0, 0, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    private static final int WEST_X = 70;
    private static final int EAST_X = 210;
    private static final int Y = 84;

    private static Map<PointOfInterest.Kind, Integer>[] build(PrecinctCharacter west,
                                                              PrecinctCharacter east) {
        PrecinctPlan plan = PrecinctPlan.authored(List.of(
                Precinct.settlement("west", WEST_X, Y, GrownTrunkPlan.Profile.town(), west),
                Precinct.settlement("east", EAST_X, Y, GrownTrunkPlan.Profile.town(), east)));
        MapResult map = new BspCityGenerator().usePrecincts(plan).generate(W, H, 42L, null, world());
        @SuppressWarnings("unchecked")
        Map<PointOfInterest.Kind, Integer>[] mixes = new Map[]{
                new EnumMap<>(PointOfInterest.Kind.class),
                new EnumMap<>(PointOfInterest.Kind.class)};
        for (PointOfInterest poi : map.pointsOfInterest) {
            int nearer = Math.abs(poi.centerX() - WEST_X) <= Math.abs(poi.centerX() - EAST_X)
                    ? 0 : 1;
            mixes[nearer].merge(poi.kind, 1, Integer::sum);
        }
        return mixes;
    }

    private static int count(Map<PointOfInterest.Kind, Integer> mix, PointOfInterest.Kind kind) {
        return mix.getOrDefault(kind, 0);
    }

    private static String describe(Map<PointOfInterest.Kind, Integer> mix) {
        return mix.toString();
    }

    /** A dormitory and a depot on one map are different places. */
    @Test
    void differentCharactersBuildDifferentPlaces() {
        Map<PointOfInterest.Kind, Integer>[] mixes =
                build(PrecinctCharacter.SUBURB, PrecinctCharacter.DEPOT);
        Map<PointOfInterest.Kind, Integer> suburb = mixes[0];
        Map<PointOfInterest.Kind, Integer> depot = mixes[1];
        assertTrue(count(suburb, PointOfInterest.Kind.RESIDENTIAL)
                        > count(suburb, PointOfInterest.Kind.DEPOT),
                "the suburb is not mostly housing: " + describe(suburb));
        assertTrue(count(depot, PointOfInterest.Kind.DEPOT)
                        > count(depot, PointOfInterest.Kind.RESIDENTIAL),
                "the depot is not mostly depots: " + describe(depot));
    }

    /** Two depots on one map are the same kind of place. */
    @Test
    void theSameCharacterBuildsTheSameKindOfPlace() {
        Map<PointOfInterest.Kind, Integer>[] mixes =
                build(PrecinctCharacter.DEPOT, PrecinctCharacter.DEPOT);
        for (Map<PointOfInterest.Kind, Integer> depot : mixes) {
            assertTrue(count(depot, PointOfInterest.Kind.DEPOT)
                            > count(depot, PointOfInterest.Kind.RESIDENTIAL),
                    "a depot is not mostly depots: " + describe(depot));
        }
    }
}
