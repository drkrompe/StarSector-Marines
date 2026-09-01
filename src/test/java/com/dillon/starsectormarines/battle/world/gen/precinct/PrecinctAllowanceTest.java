package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How big a place is has to be a property of the place.
 *
 * <p>Left to nearest-road, it is a property of its neighbours instead: measured
 * over three seeds at 560x336, a garrison claimed between 37885 and 86044 cells
 * depending only on how its neighbours happened to grow. Nothing about the
 * garrison changed. Derived from its program it claims between 8566 and 9908,
 * and the variation left is the road that grew through it.
 */
class PrecinctAllowanceTest {

    private static final int W = 300;
    private static final int H = 200;

    private static List<Precinct> twoPlaces(FortressProgram program) {
        return List.of(
                Precinct.garrison("garrison", 80, 100,
                        GrownTrunkPlan.Profile.hamlet(), program),
                Precinct.settlement("town", 210, 80, GrownTrunkPlan.Profile.town()));
    }

    private static GrownTrunkPlan.Grown grow(List<Precinct> precincts, long seed) {
        List<GrownTrunkPlan.Seed> seeds = precincts.stream()
                .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                .toList();
        return GrownTrunkPlan.grow(W, H, new Random(seed), seeds);
    }

    /**
     * A programmed precinct is sized by what it owes, so ordering more of
     * something makes it bigger — and nothing else does.
     */
    @Test
    void aProgrammedPrecinctIsAsBigAsWhatItHolds() {
        FortressProgram small = FortressProgram.garrison();
        FortressProgram large = small.with(RoomPurpose.BARRACKS, 8);

        GrownTrunkPlan.Grown grown = grow(twoPlaces(small), 42L);
        int[] tight = PrecinctAllowance.derive(twoPlaces(small), grown.owner(), W, H);
        int[] roomy = PrecinctAllowance.derive(twoPlaces(large), grown.owner(), W, H);

        assertTrue(roomy[0] > tight[0], "five more barrack blocks bought no extra ground, so "
                + "the program is not sizing the place and the packer will be handed an "
                + "envelope its buildings do not fit in");
        assertEquals(tight[1], roomy[1],
                "changing the garrison's program changed the town's allowance");
        assertEquals(roomy[0] - tight[0], large.envelopeArea() - small.envelopeArea(),
                "the extra ground is not the extra program, so something else is being "
                        + "scaled along with it");
    }

    /**
     * A zoned precinct has no program to size it, so it is as big as the ground
     * along its own streets — and only its own.
     */
    @Test
    void aZonedPrecinctIsAsBigAsTheGroundAlongItsStreets() {
        List<Precinct> places = twoPlaces(FortressProgram.garrison());
        GrownTrunkPlan.Grown grown = grow(places, 42L);
        int[] allowance = PrecinctAllowance.derive(places, grown.owner(), W, H);

        int townRoad = 0;
        for (int[] column : grown.owner()) {
            for (int who : column) {
                if (who == 1) townRoad++;
            }
        }
        assertTrue(allowance[1] > townRoad,
                "the town was allowed its roads and no ground to build along them");
    }

    /**
     * The allowance is a cap the claim honours, and what it does not cover is
     * open country rather than anybody's.
     */
    @Test
    void whatNoPrecinctIsAllowedStaysOpenCountry() {
        List<Precinct> places = twoPlaces(FortressProgram.garrison());
        GrownTrunkPlan.Grown grown = grow(places, 42L);
        int[] allowance = PrecinctAllowance.derive(places, grown.owner(), W, H);
        int[][] claim = PrecinctClaim.budgeted().assign(grown.owner(), W, H, allowance);
        int[] sizes = PrecinctClaim.sizes(claim, places.size());

        for (int i = 0; i < sizes.length; i++) {
            assertTrue(sizes[i] <= allowance[i] + 4, places.get(i).name() + " claimed "
                    + sizes[i] + " against an allowance of " + allowance[i]);
        }
        assertTrue(sizes[0] + sizes[1] < W * H,
                "the two places between them took the whole map, so there is no country "
                        + "for them to be places in");
    }

    /**
     * The point of deriving it: a place's size stops depending on its
     * neighbours. Same garrison, same seed, a neighbour that grows differently.
     */
    @Test
    void aPrecinctSizeDoesNotDependOnItsNeighbour() {
        FortressProgram program = FortressProgram.garrison();
        List<Precinct> withTown = twoPlaces(program);
        List<Precinct> withHamlet = List.of(withTown.get(0),
                Precinct.settlement("hamlet", 210, 80, GrownTrunkPlan.Profile.hamlet()));

        int[] a = PrecinctAllowance.derive(withTown, grow(withTown, 42L).owner(), W, H);
        int[] b = PrecinctAllowance.derive(withHamlet, grow(withHamlet, 42L).owner(), W, H);
        int drift = Math.abs(a[0] - b[0]);
        assertTrue(drift < program.envelopeArea() / 4, "the garrison's allowance moved by "
                + drift + " cells because its neighbour changed size; the road through it "
                + "may differ, but its ground is supposed to be its program's");
    }
}
