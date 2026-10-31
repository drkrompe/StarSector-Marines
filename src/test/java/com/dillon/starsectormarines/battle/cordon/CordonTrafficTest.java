package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonTraffic.Mover;
import com.dillon.starsectormarines.battle.cordon.CordonTraffic.Stretch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The convoys' tight stretches as reservations ({@link CordonTraffic}). */
class CordonTrafficTest {

    private static final int B = CordonTraffic.HOLD_BACK;

    private static Mover in(int number, int... at) { return new Mover(number, 1, at); }

    private static Mover out(int number, int... at) { return new Mover(number, -1, at); }

    @Test
    void aConvoyHoldsShortOfTheYardWhileAnotherIsInItAndGoesOnceItIsClear() {
        CordonTraffic yard = new CordonTraffic(List.of(new Stretch(100, 130, true)));
        assertTrue(yard.update(List.of(in(1, 95, 85))).isEmpty(), "the yard is free: the first drives in");
        assertTrue(yard.holds(0, 1));
        Map<Integer, Integer> holds = yard.update(List.of(in(1, 128, 118), in(2, 100 - B, 100 - B - 10)));
        assertEquals(100 - B, holds.get(2), "the next holds at the hold point");
        assertNull(holds.get(1));
        // The first loads, turns for home and drives out past the yard's mouth; then the second goes in.
        assertEquals(100 - B, yard.update(List.of(out(1, 110, 120), in(2, 100 - B, 76))).get(2));
        assertTrue(yard.update(List.of(out(1, 60, 70), in(2, 100 - B, 76))).isEmpty(), "released once clear");
        assertTrue(yard.holds(0, 2));
        assertFalse(yard.holds(0, 1));
    }

    @Test
    void outOfAOneWayYardTheNextConvoyGoesInOnceTheLastTruckIsBackOnTheRoadAndOnlyOneLoadsAtATime() {
        // The yard 100..130; its exit rejoins the road at 90, short of the yard's mouth.
        CordonTraffic yard = new CordonTraffic(List.of(new Stretch(100, 130, true)));
        yard.update(List.of(in(1, 95, 85, 75)));
        assertEquals(100 - B, yard.update(List.of(in(1, 130, 126, 122), in(2, 100 - B, 76, 66))).get(2),
                "one convoy loads at a time");
        // Loaded and out of the exit: two trucks back on the road below the mouth, the last still in the yard.
        assertEquals(100 - B, yard.update(List.of(out(1, 90, 88, 128), in(2, 100 - B, 76, 66))).get(2));
        assertTrue(yard.update(List.of(out(1, 70, 80, 90), in(2, 100 - B, 76, 66))).isEmpty(),
                "all out: the next drives in while the first goes on home down the road");
    }

    @Test
    void goingHomeGoesFirstWhenTwoMeetAtEitherEndOfANarrowStretchAndNeitherIsStuck() {
        CordonTraffic narrow = new CordonTraffic(List.of(new Stretch(100, 120, false)));
        Map<Integer, Integer> holds = narrow.update(List.of(in(1, 100 - B, 100 - B - 8), out(2, 120 + B, 120 + B + 8)));
        assertEquals(100 - B, holds.get(1), "the one coming in waits");
        assertNull(holds.get(2), "the one going home drives through");
        assertTrue(narrow.holds(0, 2));
        // Through and out the far end: the one waiting goes.
        narrow.update(List.of(in(1, 100 - B, 78), out(2, 110, 118)));
        assertTrue(narrow.update(List.of(in(1, 100 - B, 78), out(2, 80, 90))).isEmpty());
        assertTrue(narrow.holds(0, 1));
    }

    @Test
    void convoysGoingTheSameWayShareANarrowStretchButNeverTheYard() {
        CordonTraffic narrow = new CordonTraffic(List.of(new Stretch(100, 120, false)));
        narrow.update(List.of(in(1, 105, 95)));
        assertTrue(narrow.update(List.of(in(1, 112, 102), in(2, 100 - B, 80))).isEmpty());
        CordonTraffic yard = new CordonTraffic(List.of(new Stretch(100, 120, true)));
        yard.update(List.of(in(1, 105, 95)));
        assertEquals(100 - B, yard.update(List.of(in(1, 112, 102), in(2, 100 - B, 80))).get(2));
    }

    @Test
    void aConvoyKeepsTheStretchUntilItsLastTruckIsThroughSoItIsNeverSplit() {
        CordonTraffic narrow = new CordonTraffic(List.of(new Stretch(100, 120, false)));
        narrow.update(List.of(out(1, 118, 128)));
        narrow.update(List.of(out(1, 95, 105)));
        assertTrue(narrow.holds(0, 1), "its second truck is still in it");
        Map<Integer, Integer> holds = narrow.update(List.of(out(1, 95, 105), in(2, 100 - B, 80), out(3, 120 + B)));
        assertNull(holds.get(3), "another going home shares it");
        assertEquals(100 - B, holds.get(2), "one coming in waits for both");
        narrow.update(List.of(out(1, 80, 90)));
        assertFalse(narrow.holds(0, 1));
    }

    @Test
    void stretchesCloserThanAConvoyAndTwoHoldBacksAreOneSoNoHoldPointFallsInsideAnother() {
        List<Stretch> merged = CordonTraffic.merged(List.of(new Stretch(250, 280, true), new Stretch(100, 110, false),
                new Stretch(140, 150, false)), 2 * B + 30);
        assertEquals(List.of(new Stretch(100, 150, false), new Stretch(250, 280, true)), merged);
        assertEquals(List.of(new Stretch(100, 230, true)),
                CordonTraffic.merged(List.of(new Stretch(100, 150, false), new Stretch(170, 230, true)), 2 * B + 30));
    }

    @Test
    void theNarrowRunsAreWhereTheRoadIsTooNarrowToPass() {
        assertEquals(List.of(new Stretch(2, 3, false), new Stretch(6, 6, false)),
                CordonTraffic.narrows(new int[]{9, 9, 4, 5, 9, 9, 3, 9}, 6));
    }
}
