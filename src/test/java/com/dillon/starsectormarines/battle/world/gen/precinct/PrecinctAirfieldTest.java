package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ordered airfield is built, not merely paid for.
 *
 * <p>The count reached the sizing before it reached anything else: asking for
 * four widened the garrison by five thousand cells of apron and put nothing on
 * it, because this path never placed a lot at all. Ground bought and not used
 * is worse than ground not bought — the place is the right size for an air arm
 * it does not have.
 *
 * <p>Asserted through what a lot actually emits — runways, berths, shelters —
 * rather than through the reservation, because a reservation is exactly what
 * the broken version had.
 */
class PrecinctAirfieldTest {

    private static final int W = 560;
    private static final int H = 336;

    private record Built(GenContext ctx, Map<String, Integer> shortfall) { }

    private static Built ward(int airfields, int w, int h) {
        FortressProgram program = FortressProgram.garrison().withAirfields(airfields);
        List<Precinct> precincts = List.of(
                Precinct.garrison("garrison", w / 3, h / 2,
                        GrownTrunkPlan.Profile.hamlet(), program),
                Precinct.settlement("town", 2 * w / 3, h / 3,
                        GrownTrunkPlan.Profile.town()));
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(w, h, new Random(42L),
                precincts.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(precincts, grown.owner(), w, h);
        int[][] claim =
                PrecinctClaim.byKind(precincts).assign(grown.owner(), w, h, allowance);

        GenContext ctx = new GenContext(new NavigationGrid(w, h), new CellTopology(w, h),
                new Random(42L), w, h, 42L);
        ctx.put(BspKeys.PRECINCTS, PrecinctPlan.authored(precincts));
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        ctx.put(BspKeys.PRECINCT_ROAD, grown.owner());
        new PrecinctWardStage().run(ctx);
        return new Built(ctx, ctx.get(BspKeys.UNPLACED_AIRFIELDS));
    }

    /** One airfield ordered, one airfield on the ground. */
    @Test
    void anOrderedAirfieldIsActuallyBuilt() {
        Built built = ward(1, W, H);
        assertTrue(built.ctx().landingPads.size() > 0,
                "a garrison ordered an airfield and has no berths on it");
        assertTrue(built.shortfall().isEmpty(),
                "an airfield that was built was also reported missing");
    }

    /**
     * A second airfield is a second airfield.
     *
     * <p>Berth count is the reading rather than lot count, because the sizes
     * ladder down: two fields that both came out as strips is a different
     * outcome from one station, and only the berths distinguish them.
     */
    @Test
    void moreAirfieldsMeanMoreOfThemOnTheGround() {
        int one = ward(1, W, H).ctx().landingPads.size();
        int three = ward(3, W, H).ctx().landingPads.size();
        assertTrue(three > one, "three airfields produced " + three + " berths against one "
                + "airfield's " + one + ", so the extra ground is still empty apron");
    }

    /**
     * The size ladder absorbs pressure rather than refusing.
     *
     * <p>Measured on a 200x140 map, berths by airfields ordered run 2, 4, 7, 9,
     * 12, 13 — growth all the way, and short of what six of the largest field
     * would give, because a place that cannot seat another station seats a field
     * or a pad instead. Thirteen berths for six fields rather than eighteen is
     * the ladder doing its job.
     *
     * <p><b>The reference for "the largest size" is a map with room, not this
     * one.</b> A cramped garrison's single field is itself already laddered
     * down: 200x140 seats a two-berth field where 560x336 seats a three-berth
     * station, so measuring six against six of the cramped one compares against
     * something that is not the top of the ladder — and reads as a regression
     * the moment the claim gets a little rounder or a little smaller.
     */
    @Test
    void theSizeLadderAbsorbsPressureRatherThanRefusing() {
        int largest = ward(1, W, H).ctx().landingPads.size();
        int one = ward(1, 200, 140).ctx().landingPads.size();
        Built six = ward(6, 200, 140);
        int many = six.ctx().landingPads.size();

        assertTrue(many > one, "six airfields produced " + many + " berths against one "
                + "airfield's " + one + ", so the extra orders bought nothing");
        assertTrue(six.shortfall().isEmpty(), "six airfields on a 200x140 map reported "
                + six.shortfall() + " missing, where the ladder should have seated them all "
                + "at smaller sizes");
        assertTrue(many < 6 * largest, "six airfields produced " + many + " berths, six "
                + "times the " + largest + " a field gets on a map with room, so every one "
                + "of them came out the largest size and the ladder is not being walked at "
                + "all");
    }

    /** A field that had nowhere to go at all is counted, not dropped. */
    @Test
    void anAirfieldWithNoRoomIsReported() {
        Built built = ward(60, 200, 140);
        assertNotNull(built.shortfall(), "nothing was bound, so a caller cannot tell "
                + "\"all sixty were built\" from \"nobody asked for any\"");
        assertTrue(built.shortfall().containsKey("garrison"),
                "sixty airfields on a 200x140 map all found room, which they cannot have");
        assertTrue(built.shortfall().get("garrison") > 0);
    }

    /** Nothing is reported missing when there was room for everything. */
    @Test
    void aGarrisonWithRoomReportsNoShortfall() {
        assertEquals(Map.of(), ward(1, W, H).shortfall());
    }
}
