package com.dillon.starsectormarines.battle.derelict;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void theRoundKeepsOnlyThePlacesNearestTheNode() {
        List<int[]> many = new ArrayList<>();
        for (int d = 1; d <= 8; d++) many.add(new int[] {10 + (d % 2 == 0 ? d : -d), 10});
        List<int[]> round = PatrolRound.order(ZONE, many);
        assertEquals(PatrolRound.MAX_PLACES, round.size());
        for (int[] place : round) assertTrue(Math.abs(place[0] - 10) <= PatrolRound.MAX_PLACES, "the far places go");
    }

    @Test
    void aWalkerThatGetsNoNearerGivesThePlaceUp() {
        PatrolRound round = new PatrolRound(List.of(new int[] {0, 0}, new int[] {9, 0}));
        round.advance(5f, 0.5f, 1f);
        assertArrayEquals(new int[] {0, 0}, round.advance(3f, 0.5f, PatrolRound.STUCK_SECONDS - 1f),
                "getting nearer: headway resets the clock");
        assertArrayEquals(new int[] {0, 0}, round.advance(3f, 0.5f, PatrolRound.STUCK_SECONDS - 1f));
        assertArrayEquals(new int[] {9, 0}, round.advance(3f, 0.5f, 1f), "no headway long enough: on to the next");
    }
}
