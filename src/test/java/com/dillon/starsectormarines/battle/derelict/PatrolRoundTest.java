package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PatrolRoundTest {

    private static final SecurityZone ZONE = new SecurityZone(0, 10, 10, 8);

    @Test
    void theRoundKeepsThePlacesInsideThePerimeterInOrderRoundTheNode() {
        List<int[]> round = PatrolRound.order(ZONE, List.of(new int[] {10, 16}, new int[] {16, 10},
                new int[] {4, 10}, new int[] {40, 40}, new int[] {16, 10}));
        assertEquals(3, round.size(), "outside the perimeter and repeated places are dropped");
        assertArrayEquals(new int[] {16, 10}, round.get(0), "east first");
        assertArrayEquals(new int[] {10, 16}, round.get(1), "then counter-clockwise");
        assertArrayEquals(new int[] {4, 10}, round.get(2));
    }

    @Test
    void withNoPlaceInsideTheRoundIsTheNode() {
        assertArrayEquals(new int[] {10, 10}, PatrolRound.order(ZONE, List.of(new int[] {40, 40})).get(0));
    }

    @Test
    void theWalkerPausesAtEachPlaceThenGoesOnAndTheLoopCloses() {
        PatrolRound round = new PatrolRound(List.of(new int[] {0, 0}, new int[] {9, 0}));
        assertArrayEquals(new int[] {0, 0}, round.advance(5f, 0.5f, 10f), "not there yet: no pause counts");
        assertArrayEquals(new int[] {0, 0}, round.advance(0.5f, 0.5f, PatrolRound.PAUSE_SECONDS - 1f));
        assertArrayEquals(new int[] {9, 0}, round.advance(0.5f, 0.5f, 1f), "paused long enough: on to the next");
        round.advance(9.5f, 0.5f, PatrolRound.PAUSE_SECONDS);
        assertArrayEquals(new int[] {0, 0}, round.current(), "back to the first");
    }
}
