package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRoll;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.objective.optional.OptionalObjective;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Cordon's beats as optional objectives: the pools, the forcing lever and each beat's rule. */
class CordonObjectivesTest {

    private static Laid laid(String name) {
        return new Laid(name, Laid.Kind.SITE, Laid.Status.LAID, "", false, List.of(new int[]{1, 1}, new int[]{4, 4}),
                List.of(), List.of());
    }

    private static List<String> keys(List<OptionalObjective> objectives) {
        List<String> out = new ArrayList<>();
        for (OptionalObjective o : objectives) out.add(o.key());
        return out;
    }

    @Test
    void eachVariantCarriesItsSixBeats() {
        assertEquals(List.of("stragglers", "guns_dry", "switch_line", "bore_behind", "last_hamlet", "burn_granary"),
                keys(CordonObjectives.pool(CordonVariant.LINE, null)));
        assertEquals(List.of("clear_road", "truck_down", "last_loading", "militia", "maw_on_road", "gap_falls"),
                keys(CordonObjectives.pool(CordonVariant.ROAD, null)));
    }

    @Test
    void theWreckBeatIsOfferedOnlyWhereItsGroundWasHeld() {
        List<OptionalObjective> road = CordonObjectives.pool(CordonVariant.ROAD, null);
        OptionalObjective clear = road.get(0);
        assertFalse(ObjectiveRoll.fits(clear, LaidLayout.EMPTY));
        assertTrue(ObjectiveRoll.fits(clear, new LaidLayout(List.of(laid(CordonPlan.CORRIDOR_WRECK)))));
        for (long seed = 0; seed < 50; seed++) {
            assertFalse(keys(ObjectiveRoll.pick(road, LaidLayout.EMPTY, seed, 2, 3)).contains("clear_road"));
        }
    }

    @Test
    void forcingNamesBeatsByKeyOrCodeAndStillReadsTheMap() {
        List<OptionalObjective> line = CordonObjectives.pool(CordonVariant.LINE, null);
        LaidLayout farm = new LaidLayout(List.of(laid(CordonSetPieces.FARMSTEAD), laid(CordonSetPieces.AMMO_DUMP)));
        assertEquals(List.of(), CordonObjectives.forced("none", line, farm));
        assertEquals(List.of("stragglers", "guns_dry"), keys(CordonObjectives.forced("L1, guns_dry, L5", line, farm)));
        assertEquals(List.of("stragglers", "guns_dry"), keys(CordonObjectives.forced("all", line, farm)));
    }

    @Test
    void aCityRollsFewerBeats() {
        assertEquals(2, CordonObjectives.range(CordonPlan.Density.URBAN)[1]);
        assertEquals(3, CordonObjectives.range(CordonPlan.Density.RURAL)[1]);
    }

    @Test
    void stragglersAreInWhenNobodyIsLeftOutAndSomeoneMadeIt() {
        assertEquals(Status.ACTIVE, StragglersBeat.rule(6, 6, 0));
        assertEquals(Status.ACTIVE, StragglersBeat.rule(6, 2, 3));
        assertEquals(Status.SUCCEEDED, StragglersBeat.rule(6, 0, 4));
        assertEquals(Status.FAILED, StragglersBeat.rule(6, 0, 0));
    }

    @Test
    void theHamletIsCaughtAtItsFirstLoss() {
        assertEquals(Status.ACTIVE, HamletBeat.rule(6, 6, 0, 0));
        assertEquals(Status.FAILED, HamletBeat.rule(6, 5, 0, 1));
        assertEquals(Status.SUCCEEDED, HamletBeat.rule(6, 0, 6, 0));
    }

    @Test
    void theAmmoTruckMustArriveBeforeItOrTheRedoubtIsLost() {
        assertEquals(Status.SUCCEEDED, GunsDryBeat.rule(true, false, true));
        assertEquals(Status.FAILED, GunsDryBeat.rule(false, true, true));
        assertEquals(Status.FAILED, GunsDryBeat.rule(false, false, false));
        assertEquals(Status.ACTIVE, GunsDryBeat.rule(false, false, true));
    }

    @Test
    void theSwitchLineWantsMostOfTheGarrison() {
        assertEquals(Status.ACTIVE, SwitchLineBeat.rule(12, 12, 6));
        assertEquals(Status.SUCCEEDED, SwitchLineBeat.rule(12, 9, 7));
        assertEquals(Status.FAILED, SwitchLineBeat.rule(12, 6, 2));
        assertEquals(Status.LAPSED, SwitchLineBeat.rule(0, 0, 0));
    }

    @Test
    void eachTeamHoldsItsOwnStretchOfTheSwitchLine() {
        // A line 30 wide and 2 deep: three teams across it, each in the middle of its third, on the line.
        int[] line = {100, 50, 130, 52};
        assertArrayEquals(new int[]{105, 51}, SwitchLineBeat.stretch(line, 0, 3));
        assertArrayEquals(new int[]{115, 51}, SwitchLineBeat.stretch(line, 1, 3));
        assertArrayEquals(new int[]{125, 51}, SwitchLineBeat.stretch(line, 2, 3));
        // Run north to south, along its height.
        assertArrayEquals(new int[]{51, 105}, SwitchLineBeat.stretch(new int[]{50, 100, 52, 120}, 0, 2));
    }

    @Test
    void aBoreMustFallWhileAFissure() {
        assertEquals(Status.ACTIVE, BoreBeat.rule(false, false, null));
        assertEquals(Status.ACTIVE, BoreBeat.rule(true, false, BoreTier.FISSURE));
        assertEquals(Status.SUCCEEDED, BoreBeat.rule(true, true, BoreTier.FISSURE));
        assertEquals(Status.FAILED, BoreBeat.rule(true, false, BoreTier.BORE));
    }

    @Test
    void theGranaryBurnsAtHalfItsWallsAndLapsesUntried() {
        assertTrue(GranaryBeat.burnt(20, 10));
        assertFalse(GranaryBeat.burnt(20, 11));
        assertEquals(Status.SUCCEEDED, GranaryBeat.rule(true, false, true));
        assertEquals(Status.FAILED, GranaryBeat.rule(false, true, true));
        assertEquals(Status.LAPSED, GranaryBeat.rule(false, true, false));
    }

    @Test
    void theRoadIsClearedBeforeTheConvoyNeedsIt() {
        assertEquals(Status.SUCCEEDED, ClearRoadBeat.rule(true, true, false));
        assertEquals(Status.FAILED, ClearRoadBeat.rule(false, false, false));
        assertEquals(Status.FAILED, ClearRoadBeat.rule(false, true, true));
        assertEquals(Status.ACTIVE, ClearRoadBeat.rule(false, true, false));
    }

    @Test
    void theRoadBeatsReadTheirConvoys() {
        assertEquals(Status.SUCCEEDED, TruckDownBeat.rule(4, 4, 1));
        assertEquals(Status.FAILED, TruckDownBeat.rule(4, 2, 1));
        assertEquals(Status.ACTIVE, TruckDownBeat.rule(4, 2, 2));
        assertEquals(Status.SUCCEEDED, LastLoadingBeat.rule(true, false));
        assertEquals(Status.FAILED, LastLoadingBeat.rule(false, true));
        assertEquals(Status.SUCCEEDED, MilitiaBeat.rule(5, 1));
        assertEquals(Status.FAILED, MilitiaBeat.rule(0, 0));
        assertEquals(Status.FAILED, GapFallsBeat.rule(false, 1, 2, 3));
        assertEquals(Status.FAILED, GapFallsBeat.rule(true, 0, 2, 3));
        assertEquals(Status.SUCCEEDED, GapFallsBeat.rule(false, 0, 3, 3));
    }
}
