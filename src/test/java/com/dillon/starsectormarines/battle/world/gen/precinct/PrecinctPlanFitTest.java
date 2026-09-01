package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plan fits the map it is given, at every size a battle is played on.
 *
 * <p>The derivation was written against the size it was measured on and the
 * fixed values it carries are most of a skirmish map: at 112x64 a 30-cell edge
 * margin leaves a four-cell band to seed in, the rejection sampler never found
 * a second seed, and the garrison branch dereferenced the null it got back. A
 * defended world losing its objective to arithmetic about the map is the fault
 * this pins shut, from both ends — the derivation must not throw, and it must
 * still produce the thing the mission is about.
 *
 * <p>The measured size is pinned as well, because the whole model in
 * {@code precincts.md} was measured at 560x336 and a scaling rule that moved
 * those numbers would have quietly invalidated it.
 */
class PrecinctPlanFitTest {

    /** Every size a battle is actually played at, plus the size the model was measured at. */
    private static final int[][] SIZES = {{112, 64}, {144, 80}, {280, 168}, {560, 336}};

    private static TargetProfile world(int marketSize, int defenceLevel) {
        return new TargetProfile(marketSize, 5, defenceLevel, 1, "independent",
                EnumSet.of(EconomicFunction.HABITATION),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
    }

    /**
     * No world and no map size is a combination the derivation cannot answer.
     *
     * <p>Exhaustive over the space rather than sampled from it, because the
     * failure was a corner of it and a sampled sweep is exactly what would have
     * missed the corner.
     */
    @Test
    void everyWorldLaysOutOnEveryMapSize() {
        for (int[] size : SIZES) {
            for (int marketSize = 0; marketSize <= 10; marketSize++) {
                for (int rating = 0; rating <= 7; rating++) {
                    PrecinctPlan plan = PrecinctPlan.derive(world(marketSize, rating),
                            size[0], size[1], new Random(42L));
                    assertTrue(!plan.precincts().isEmpty(),
                            "a size-" + marketSize + " world at rating " + rating + " on a "
                                    + size[0] + "x" + size[1] + " map produced a map with no "
                                    + "places on it");
                }
            }
        }
    }

    /**
     * A defended world has exactly one thing to take, whatever map it is on.
     *
     * <p>Zero is a mission with nothing to attack; two is two climaxes, and
     * {@link PrecinctPlan#objective()} would have to pick between them.
     */
    @Test
    void aDefendedWorldAlwaysHasSomethingToTake() {
        for (int[] size : SIZES) {
            for (int marketSize = 0; marketSize <= 10; marketSize++) {
                for (int rating = 1; rating <= 7; rating++) {
                    PrecinctPlan plan = PrecinctPlan.derive(world(marketSize, rating),
                            size[0], size[1], new Random(42L));
                    long programmed = plan.precincts().stream()
                            .filter(Precinct::isProgrammed).count();
                    assertEquals(1, programmed, "a rating-" + rating + ", size-" + marketSize
                            + " world on a " + size[0] + "x" + size[1] + " map got "
                            + programmed + " installations to attack");
                }
            }
        }
    }

    /**
     * The size the model was measured at is untouched.
     *
     * <p>The margin and the separation only shrink for a map that cannot afford
     * them, so at 560x336 they are still the stated 30 and 60 and every
     * measurement in {@code precincts.md} still describes the maps this
     * produces.
     */
    @Test
    void theMeasuredSizeKeepsTheMarginAndSeparationItWasMeasuredWith() {
        int width = 560;
        int height = 336;
        for (long seed = 1; seed <= 20; seed++) {
            List<Precinct> places = PrecinctPlan.derive(world(10, 5), width, height,
                    new Random(seed)).precincts();
            for (Precinct place : places) {
                int fromEdge = Math.min(Math.min(place.seedX(), width - place.seedX()),
                        Math.min(place.seedY(), height - place.seedY()));
                assertTrue(fromEdge >= 30, "seed " + seed + ": " + place.name()
                        + " was seeded " + fromEdge + " cells from the map edge, inside the "
                        + "30-cell margin the 560x336 measurements were taken with");
            }
            for (int i = 0; i < places.size(); i++) {
                for (int j = i + 1; j < places.size(); j++) {
                    int dx = places.get(i).seedX() - places.get(j).seedX();
                    int dy = places.get(i).seedY() - places.get(j).seedY();
                    int gap = (int) Math.sqrt(dx * dx + dy * dy);
                    assertTrue(gap >= PrecinctPlan.MIN_SEED_SEPARATION, "seed " + seed + ": "
                            + places.get(i).name() + " and " + places.get(j).name() + " came "
                            + "out " + gap + " cells apart at 560x336, under the "
                            + PrecinctPlan.MIN_SEED_SEPARATION + " the measurements assumed");
                }
            }
        }
    }
}
