package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ReactiveWatch.Candidate;
import com.dillon.starsectormarines.battle.command.ReactiveWatch.Keep;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveWatchTest {

    private static final float HOT = GarrisonDirector.START_PRESSURE;
    private static final float WARM = (GarrisonDirector.START_PRESSURE + GarrisonDirector.RELEASE_PRESSURE) / 2f;
    private static final float COOL = GarrisonDirector.RELEASE_PRESSURE / 2f;

    private static Candidate place(long key, int gap, float pressure) {
        return new Candidate(key, gap, 60, pressure, false, false, false);
    }

    @Test
    void watchIsTheDefendAreaRing() {
        assertEquals(SquadMoveOrderService.DEFEND_AREA_RADIUS_CELLS, ReactiveWatch.WATCH_RADIUS_CELLS);
    }

    @Test
    void aQuietWatchMansNothing() {
        assertEquals(-1, ReactiveWatch.choose(List.of(place(1, 3, WARM), place(2, 5, COOL)), 6, 6));
    }

    @Test
    void mansTheMostPressedThenTheNearer() {
        List<Candidate> places = List.of(place(1, 2, HOT), place(2, 9, HOT + 4), place(3, 4, HOT + 4));
        assertEquals(2, ReactiveWatch.choose(places, 6, 6));
        assertEquals(1, ReactiveWatch.choose(List.of(place(7, 3, HOT), place(5, 3, HOT)), 6, 6),
                "equal pressure and distance falls to the lower key");
    }

    @Test
    void skipsTakenEnemyHeldLockedOutDistantAndUnreachablePlaces() {
        List<Candidate> places = List.of(
                new Candidate(1, 2, 60, HOT + 9, true, false, false),
                new Candidate(2, 2, 60, HOT + 9, false, true, false),
                new Candidate(3, 2, 60, HOT + 9, false, false, true),
                new Candidate(4, ReactiveWatch.WATCH_RADIUS_CELLS + 1, 60, HOT + 9, false, false, false),
                new Candidate(5, 2, GarrisonDirector.MAX_ETA_TICKS + 1, HOT + 9, false, false, false),
                place(6, 12, HOT));
        assertEquals(5, ReactiveWatch.choose(places, 6, 6));
    }

    @Test
    void aSquadBelowUsefulStrengthIsNeverSent() {
        assertEquals(-1, ReactiveWatch.choose(List.of(place(1, 1, HOT + 9)), 2, 8));
        assertEquals(0, ReactiveWatch.choose(List.of(place(1, 1, HOT + 9)), 4, 8));
    }

    @Test
    void releaseNeedsTheWholeDwellBelowTheLowerLevel() {
        int cool = 0;
        for (int pulse = 1; pulse < GarrisonDirector.DWELL_PULSES; pulse++) {
            cool = ReactiveWatch.cooled(cool, COOL);
            assertEquals(Keep.STAY, ReactiveWatch.keep(cool, false, 6, 6, false), "pulse " + pulse);
        }
        // Pressure between the levels resets the dwell: nothing is given back on wavering pressure.
        assertEquals(0, ReactiveWatch.cooled(cool, WARM));
        cool = ReactiveWatch.cooled(cool, COOL);
        assertEquals(Keep.RELEASE, ReactiveWatch.keep(cool, false, 6, 6, false));
    }

    @Test
    void neverReleasedInAFight() {
        assertEquals(Keep.STAY, ReactiveWatch.keep(GarrisonDirector.DWELL_PULSES + 5, true, 6, 6, false));
    }

    @Test
    void breachOrDepletionFallsBack() {
        assertEquals(Keep.FALL_BACK, ReactiveWatch.keep(0, true, 6, 6, true));
        assertEquals(Keep.FALL_BACK, ReactiveWatch.keep(0, false, 2, 8, false));
    }

    @Test
    void aBreachIsNearTheSquadOnceItHasArrived() {
        int far = GarrisonDirector.BREACH_RANGE_CELLS + 1;
        assertTrue(ReactiveWatch.breached(false, 0, 0, far, 0), "anywhere in the place before arriving");
        assertFalse(ReactiveWatch.breached(true, 0, 0, far, 0), "the far end of a long run is another stretch");
        assertTrue(ReactiveWatch.breached(true, 0, 0, far - 1, 3));
    }

    @Test
    void gapAndDrift() {
        assertEquals(0, ReactiveWatch.gap(5, 5, 4, 4, 8, 8));
        assertEquals(3, ReactiveWatch.gap(1, 6, 4, 4, 8, 8));
        assertEquals(2, ReactiveWatch.gap(10, 2, 4, 4, 8, 8));
        assertFalse(ReactiveWatch.drifted(0, 0, ReactiveWatch.WATCH_RADIUS_CELLS, 0));
        assertTrue(ReactiveWatch.drifted(0, 0, 0, ReactiveWatch.WATCH_RADIUS_CELLS + 1));
    }
}
