package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordering more of something gets more ground, and what still will not fit is
 * said out loud.
 *
 * <p>Both halves matter and they fail differently. Ground that does not track
 * the program gives a place built for a garrison it is not getting. Ground that
 * tracks it and then silently drops what will not fit gives an
 * under-provisioned installation indistinguishable from a small one, which is
 * the fault {@code compound-programs.md} exists to remove.
 */
class PrecinctProgramFitTest {

    private static final int W = 400;
    private static final int H = 260;

    private static List<Precinct> places(FortressProgram program, int w, int h) {
        return List.of(
                Precinct.garrison("garrison", w / 3, h / 2,
                        GrownTrunkPlan.Profile.hamlet(), program),
                Precinct.settlement("town", 2 * w / 3, h / 3,
                        GrownTrunkPlan.Profile.town()));
    }

    private static int allowanceFor(FortressProgram program) {
        List<Precinct> precincts = places(program, W, H);
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(42L),
                precincts.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        return PrecinctAllowance.derive(precincts, grown.owner(), W, H)[0];
    }

    /** More barracks, more ground — the district is sized from what it owes. */
    @Test
    void aBiggerProgramIsGivenMoreGround() {
        FortressProgram base = FortressProgram.garrison();
        int small = allowanceFor(base);
        int large = allowanceFor(base.with(RoomPurpose.BARRACKS, 12));
        assertTrue(large > small, "twelve barrack blocks were allowed the same "
                + small + " cells as three, so the district is not sized from its program");
    }

    /** Airfields are ground too, even though no building stands on one. */
    @Test
    void moreAirfieldsBuyMoreGround() {
        FortressProgram base = FortressProgram.garrison();
        assertTrue(allowanceFor(base.withAirfields(4)) > allowanceFor(base),
                "four airfields were allowed the same ground as one");
    }

    /**
     * A place that could not build what it owed names the parts it is short.
     *
     * <p>Granted ground is not usable ground: a claim is one shape and a
     * program is a set of footprints, so a district given every cell it asked
     * for can still fail to seat a sixteen-by-six block. Measured on a cramped
     * map, a garrison owed six of them, was granted its whole allowance, and
     * built three.
     */
    @Test
    void whatCouldNotBeBuiltIsRecorded() {
        int w = 160;
        int h = 110;
        FortressProgram huge = FortressProgram.garrison()
                .with(RoomPurpose.BARRACKS, 40)
                .with(RoomPurpose.VEHICLE_BAY, 8);

        Map<String, List<FortressBuilding>> unbuilt = runWard(huge, w, h);
        assertNotNull(unbuilt, "the ward stage bound nothing, so a caller cannot tell "
                + "\"nothing was short\" from \"nobody asked\"");
        assertTrue(unbuilt.containsKey("garrison"), "a garrison owed forty barrack blocks on "
                + "a " + w + "x" + h + " map reported no shortfall at all");
        assertTrue(unbuilt.get("garrison").size() > 0);
    }

    /** A program that fits reports an empty shortfall, not a missing one. */
    @Test
    void aPlaceThatBuiltEverythingSaysSo() {
        Map<String, List<FortressBuilding>> unbuilt =
                runWard(FortressProgram.garrison(), 560, 336);
        assertNotNull(unbuilt, "the ward stage bound nothing on a map with room to spare");
        assertTrue(unbuilt.isEmpty(), "a garrison with room for its whole program still "
                + "reported " + unbuilt + " unbuilt");
    }

    /** Runs the ward stage over a grown, claimed map and returns what it recorded. */
    private static Map<String, List<FortressBuilding>> runWard(FortressProgram program,
                                                               int w, int h) {
        List<Precinct> precincts = places(program, w, h);
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
        return ctx.get(BspKeys.UNPLACED_PROGRAM);
    }
}
