package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission may say roughly where the place it is about goes.
 *
 * <p>Two halves, and the second is the reason the first is written down. The
 * placement overload seeds the garrison inside the region it was given, which
 * means drawing it <em>before</em> the settlement — a settlement drawing first
 * would push the objective out of the region the mission named. That reordering
 * is exactly the kind of change that silently re-rolls every existing map, so
 * the six-argument overload is pinned cell for cell against what it produced
 * before the placement overload existed.
 */
class PrecinctPlanPlacementTest {

    private static final int W = 560;
    private static final int H = 336;

    /** A size-7, rating-6 world: a settlement, a garrison, one outlying place. */
    private static final TargetProfile WORLD = new TargetProfile(
            7, 50, 6, 3, "", EnumSet.noneOf(EconomicFunction.class),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    /**
     * What the six-argument derive produced at 560x336 on this world, recorded
     * from {@code c4eb77137} before the placement overload was written, as
     * {@code settlement, garrison, outlying-1} seeds per row.
     */
    private static final long[] SEEDS = {1L, 42L, 777L};
    private static final int[][][] BEFORE = {
            {{515, 238}, {377, 111}, {284, 46}},
            {{160, 57}, {278, 86}, {500, 103}},
            {{160, 60}, {332, 162}, {285, 43}},
    };

    /**
     * The derivation nobody stated a placement for is the one it always was.
     *
     * <p>Every checked-in fixture and every piece of recorded evidence sits on
     * these draws, so a plan that merely looked equivalent would still be a
     * different map everywhere.
     */
    @Test
    void anUnstatedDeriveIsUnchanged() {
        for (int i = 0; i < SEEDS.length; i++) {
            PrecinctPlan plan = PrecinctPlan.derive(WORLD, PrecinctPlan.Sprawl.BALANCED,
                    Fortification.Demand.UNSTATED, W, H, new Random(SEEDS[i]));
            assertEquals(BEFORE[i].length, plan.precincts().size(),
                    "seed " + SEEDS[i] + " derived " + plan.precincts().size()
                            + " places where it used to derive " + BEFORE[i].length);
            for (int p = 0; p < BEFORE[i].length; p++) {
                Precinct precinct = plan.precincts().get(p);
                assertEquals(BEFORE[i][p][0], precinct.seedX(),
                        "seed " + SEEDS[i] + ": " + precinct.name() + " moved on x");
                assertEquals(BEFORE[i][p][1], precinct.seedY(),
                        "seed " + SEEDS[i] + ": " + precinct.name() + " moved on y");
            }
        }
    }

    /**
     * A stated objective lands in the region it was stated in, whatever the seed.
     *
     * <p>Ten seeds rather than one because the failure this guards against is a
     * draw order: a garrison seeded after the settlement lands wherever the
     * rejection sampler could still fit it, which is inside the region often
     * enough that a single seed proves nothing.
     */
    @Test
    void aStatedObjectiveIsSeededInsideItsPlacement() {
        int[] region = MapPlacement.NORTH.bounds(W, H);
        for (long seed = 1; seed <= 10; seed++) {
            PrecinctPlan plan = PrecinctPlan.derive(WORLD, PrecinctPlan.Sprawl.BALANCED,
                    Fortification.Demand.UNSTATED, MapPlacement.NORTH, MapPlacement.SOUTH,
                    W, H, new Random(seed));
            Precinct objective = plan.objective();
            assertTrue(objective != null, "seed " + seed + " derived no programmed place at all");
            assertTrue(objective.seedX() >= region[0] && objective.seedX() <= region[2]
                            && objective.seedY() >= region[1] && objective.seedY() <= region[3],
                    "seed " + seed + " put the objective at " + objective.seedX() + ","
                            + objective.seedY() + ", outside the northern region it was "
                            + "stated in");
            assertSame(MapPlacement.SOUTH, plan.attackerFrom(),
                    "seed " + seed + " lost the attacker placement the mission stated");
        }
    }

    /** The other places still exist, and still keep away from the objective. */
    @Test
    void aStatedObjectiveDoesNotSwallowTheRestOfTheMap() {
        PrecinctPlan plan = PrecinctPlan.derive(WORLD, PrecinctPlan.Sprawl.BALANCED,
                Fortification.Demand.UNSTATED, MapPlacement.NORTH, MapPlacement.SOUTH,
                W, H, new Random(42L));
        assertEquals(3, plan.precincts().size(),
                "a stated placement changed how many places the world derives");
        Precinct objective = plan.objective();
        for (Precinct precinct : plan.precincts()) {
            if (precinct == objective) continue;
            int dx = precinct.seedX() - objective.seedX();
            int dy = precinct.seedY() - objective.seedY();
            assertTrue(dx * dx + dy * dy
                            >= PrecinctPlan.MIN_SEED_SEPARATION * PrecinctPlan.MIN_SEED_SEPARATION,
                    precinct.name() + " was seeded " + Math.round(Math.sqrt(dx * dx + dy * dy))
                            + " cells from the objective, inside the separation two places owe "
                            + "each other");
        }
    }
}
