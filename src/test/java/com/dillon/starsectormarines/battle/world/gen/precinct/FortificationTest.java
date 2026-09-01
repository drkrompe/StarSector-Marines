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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How hard a place is to take is stated, not discovered.
 *
 * <p>It has two failure modes and they pull opposite ways: a wall a handful of
 * low-tier squads cannot breach or flank is a refusal rather than a fight, and a
 * wall a thousand marines walk through is not a climax. Neither is fixed by
 * tuning one number harder, because it is the same number pointed at different
 * forces — so whoever knows what is being sent says, and the generator obeys.
 */
class FortificationTest {

    private static final int W = 560;
    private static final int H = 336;

    private record Built(GenContext ctx, int[][] claim, int[][] road) { }

    private static Built ward(Fortification fortification) {
        List<Precinct> precincts = List.of(
                Precinct.garrison("garrison", W / 3, H / 2, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison(), fortification),
                Precinct.settlement("town", 2 * W / 3, H / 3,
                        GrownTrunkPlan.Profile.town()));
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(42L),
                precincts.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(precincts, grown.owner(), W, H);
        int[][] claim =
                PrecinctClaim.byKind(precincts).assign(grown.owner(), W, H, allowance);

        GenContext ctx = new GenContext(new NavigationGrid(W, H), new CellTopology(W, H),
                new Random(42L), W, H, 42L);
        ctx.put(BspKeys.PRECINCTS, PrecinctPlan.authored(precincts));
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        ctx.put(BspKeys.PRECINCT_ROAD, grown.owner());
        new PrecinctWardStage().run(ctx);
        return new Built(ctx, claim, grown.owner());
    }

    private static int openings(Built built, Fortification fortification) {
        return PrecinctBoundary.openGates(built.claim(), built.road(), 0, W, H,
                fortification.gates()).size();
    }

    /** Fewer gates allowed, fewer ways in. */
    @Test
    void aTighterFortificationLeavesFewerWaysIn() {
        int picket = openings(ward(Fortification.PICKET), Fortification.PICKET);
        int citadel = openings(ward(Fortification.CITADEL), Fortification.CITADEL);
        assertTrue(citadel < picket, "a citadel left " + citadel + " ways in against a "
                + "picket's " + picket + ", so the gate cap is not reaching the wall");
        assertTrue(citadel >= 1, "a citadel sealed itself completely");
    }

    /** The cap is a maximum, and cannot conjure gates growth never made. */
    @Test
    void theCapCannotManufactureGates() {
        Fortification greedy = Fortification.PICKET.withGates(50);
        int all = PrecinctBoundary.gates(ward(greedy).claim(), ward(greedy).road(),
                0, W, H).size();
        assertTrue(openings(ward(greedy), greedy) <= all,
                "asking for fifty gates produced more crossings than the roads made");
    }

    /**
     * Something can always drive out.
     *
     * <p>A walled installation its own armour cannot leave is a defect rather
     * than a difficulty, so one drivable crossing survives whatever the cap
     * says — the same obligation the artery enforces one step earlier.
     */
    @Test
    void aSealedPlaceStillLetsItsOwnArmourOut() {
        Built built = ward(Fortification.CITADEL);
        List<PrecinctBoundary.Gate> open = PrecinctBoundary.openGates(
                built.claim(), built.road(), 0, W, H, Fortification.CITADEL.gates());
        assertTrue(open.stream().anyMatch(PrecinctBoundary.Gate::drivable),
                "a citadel kept only " + open.size() + " gate(s) and none wide enough to "
                        + "drive through, so its own vehicles are scenery");
    }

    /** A harder wall costs more to breach. */
    @Test
    void aHarderWallIsWorthMoreHitPoints() {
        int picket = wallHp(ward(Fortification.PICKET));
        int citadel = wallHp(ward(Fortification.CITADEL));
        assertTrue(citadel > picket, "a citadel wall is worth " + citadel + " against a "
                + "picket's " + picket + ", so the strength dial reaches the geometry and "
                + "not the materiel");
    }

    private static int wallHp(Built built) {
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (built.ctx().topology.isWall(x, y)) {
                    return built.ctx().grid.getWallHp(x, y);
                }
            }
        }
        return 0;
    }

    /** A walled place with no strength stated is a hole in the dial. */
    @Test
    void aWalledPlaceMustSayHowHardItIs() {
        assertThrows(IllegalArgumentException.class,
                () -> new Precinct("x", 10, 10, GrownTrunkPlan.Profile.hamlet(),
                        FortressProgram.garrison(), Precinct.Boundary.WALLED, null));
        assertThrows(IllegalArgumentException.class, () -> new Fortification(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new Fortification(1, 0));
    }
}
