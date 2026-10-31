package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonTown.State;
import com.dillon.starsectormarines.battle.evacuation.CivilDefenceCompound;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;
import com.dillon.starsectormarines.battle.world.gen.layout.LaidLayout;
import com.dillon.starsectormarines.battle.world.gen.precinct.GateYardLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ain Tafrant's staging area ({@code cordon-nouns.md}, Cut-off town), and where its defenders are seated. */
class CordonTownTest {

    @Test
    void theStagingAreaIsPressedWhileTheSwarmIsInItAndOverrunOnlyOnceNobodyHasHeldItForTheWindow() {
        assertEquals(State.HOLDING, CordonTown.next(State.HOLDING, false, false, 0));
        assertEquals(State.PRESSED, CordonTown.next(State.HOLDING, true, true, 0));
        // Nobody holds it, but not for long enough yet.
        assertEquals(State.PRESSED, CordonTown.next(State.PRESSED, true, false, CordonTown.OVERRUN_TICKS - 1));
        assertEquals(State.OVERRUN, CordonTown.next(State.PRESSED, true, false, CordonTown.OVERRUN_TICKS));
        // Cleared again, it holds.
        assertEquals(State.HOLDING, CordonTown.next(State.PRESSED, false, false, 0));
        // Overrun is final.
        assertEquals(State.OVERRUN, CordonTown.next(State.OVERRUN, false, true, 0));
    }

    @Test
    void theWindowRunsOnlyWhileNobodyHoldsIt() {
        CordonStagingArea staging = CordonStagingArea.of((x, y) -> true, 0, 0, 10, 10, 5, 11);
        CordonTown town = new CordonTown(0, 0, 10, 10, 5, 11, staging, List.of(), List.of(), 16);
        town.observe(3, 2, false, 100);
        // A Marine comes back before the window closes: the clock starts again.
        town.observe(3, 2, true, 100 + CordonTown.OVERRUN_TICKS - 1);
        town.observe(3, 2, false, 100 + CordonTown.OVERRUN_TICKS);
        assertEquals(State.PRESSED, town.state());
        town.observe(3, 2, false, 100 + 2 * CordonTown.OVERRUN_TICKS);
        assertEquals(State.OVERRUN, town.state());
        assertFalse(town.holds());
        assertTrue(town.holdsFrom(5, 10 - CordonStagingArea.REACH - CordonTown.HOLD_MARGIN));
        assertFalse(town.holdsFrom(5, 10 - CordonStagingArea.REACH - CordonTown.HOLD_MARGIN - 1));
    }

    @Test
    void theTownCallsWhenBreachedOrHalfItsDefendersAreDown() {
        CordonStagingArea staging = CordonStagingArea.of((x, y) -> true, 0, 0, 10, 10, 5, 11);
        CordonTown town = new CordonTown(0, 0, 10, 10, 5, 11, staging, List.of(), List.of(), 16);
        assertFalse(town.calling(9));
        assertTrue(town.calling(8));
        town.observe(1, 0, true, 10);
        assertTrue(town.calling(16));
    }

    @Test
    void defendersSitBehindTheFrontSlitsFirstAndNeverOnTheStagingArea() {
        // A compound whose inside runs 1..19 by 11..28, front at row 11 (low y), its loading gate on the rear wall at (10, 29).
        CordonStagingArea staging = CordonStagingArea.of((x, y) -> true, 1, 11, 19, 28, 10, 29);
        List<int[]> wall = new ArrayList<>();
        for (int x = 1; x <= 19; x += 3) {
            wall.add(new int[]{x, 11});
            wall.add(new int[]{x, 28});
        }
        List<int[]> yard = new ArrayList<>();
        for (int y = 11; y <= 28; y++) for (int x = 1; x <= 19; x++) yard.add(new int[]{x, y});
        List<int[]> seats = CordonTownGarrison.seats(wall, yard, staging, new int[]{10, 31}, 11, 10, 20);
        assertEquals(20, seats.size());
        for (int i = 0; i < 7; i++) assertEquals(11, seats.get(i)[1], "the front wall's posts first");
        for (int[] s : seats) {
            assertFalse(staging.near(s[0], s[1], CordonTownGarrison.STAGING_CLEAR), "nobody stands on the people's ground");
        }
        // Then the rear wall's posts clear of the staging area (x 1 and 19), then the yard from its front row.
        assertEquals(28, seats.get(7)[1]);
        assertEquals(28, seats.get(8)[1]);
        assertEquals(11, seats.get(9)[1]);
    }

    @Test
    void theTruckYardIsReadOffTheMapAndKeptClearOfDefendersAndOfTheStagingArea() {
        // A yard on the rear wall at row 28, its gate at (10, 28), running in to row 20 over x 6..14.
        List<int[]> cells = new ArrayList<>();
        for (int y = 20; y <= 27; y++) for (int x = 6; x <= 14; x++) cells.add(new int[]{x, y});
        List<int[]> beside = new ArrayList<>();
        for (int y = 20; y <= 27; y++) for (int x = 15; x <= 18; x++) beside.add(new int[]{x, y});
        Laid piece = new Laid(GateYardLayout.pieceName(CivilDefenceCompound.PLACE), Laid.Kind.YARD, Laid.Status.LAID,
                "", false, cells, List.of(new int[]{10, 28}, new int[]{10, 27}, new int[]{10, 24}, new int[]{13, 24}),
                beside);
        CordonGateYard yard = CordonGateYard.of(new LaidLayout(List.of(piece)));
        assertNotNull(yard);
        assertNull(CordonGateYard.of(LaidLayout.EMPTY));
        assertEquals(0, yard.inwardX());
        assertEquals(-1, yard.inwardY(), "in from the rear gate");
        assertTrue(yard.contains(yard.stopX(), yard.stopY()));
        assertEquals(4 * 8, yard.beside().size());
        // The staging area beside it, from where a truck stops: never on the yard.
        CordonStagingArea staging = CordonStagingArea.of((x, y) -> !yard.contains(x, y), 1, 11, 19, 27,
                yard.stopX(), yard.stopY());
        for (int[] c : staging.cells()) assertFalse(yard.contains(c[0], c[1]), "beside the yard, not on it");
        List<int[]> wall = new ArrayList<>();
        for (int x = 1; x <= 19; x += 2) wall.add(new int[]{x, 27});
        List<int[]> open = new ArrayList<>();
        for (int y = 11; y <= 27; y++) for (int x = 1; x <= 19; x++) open.add(new int[]{x, y});
        List<int[]> seats = CordonTownGarrison.seats(wall, open, staging, new int[]{10, 31}, yard, 11, 10, 20);
        assertEquals(20, seats.size());
        for (int[] s : seats) assertFalse(yard.contains(s[0], s[1]), "nobody stands where the trucks turn");
    }
}
