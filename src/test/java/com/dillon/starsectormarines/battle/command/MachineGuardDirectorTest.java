package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The commander-tier machine guard: who is attached to which manned place, how many, and when they stand down. */
class MachineGuardDirectorTest {

    /** Place 1 at x 100, place 2 at x 200, both on row 50. */
    private static final IntBinaryOperator PLACE_AT = (x, y) -> x == 100 ? 1 : x == 200 ? 2 : -1;
    private static final MachineGuardDirector.Place HOT_ONE = new MachineGuardDirector.Place(1, 100, 50, true);
    private static final MachineGuardDirector.Place HOT_TWO = new MachineGuardDirector.Place(2, 200, 50, true);
    private static final MachineGuardDirector.Place COOL_ONE = new MachineGuardDirector.Place(1, 100, 50, false);

    private static MachineGuardDirector.Machine lance(int id, int x) {
        return new MachineGuardDirector.Machine(id, x, 50, 2, 2);
    }

    @Test
    void theCapIsHalfTheLancesRoundedUpAndNeverAboveTwo() {
        assertEquals(0, MachineGuardDirector.cap(0));
        assertEquals(1, MachineGuardDirector.cap(1), "a lone lance may guard");
        assertEquals(1, MachineGuardDirector.cap(2), "two keep one roving");
        assertEquals(2, MachineGuardDirector.cap(3));
        assertEquals(2, MachineGuardDirector.cap(9));
    }

    @Test
    void theNearestFreeLanceGoesToAPressedMannedPlaceOnePerPulse() {
        MachineGuardDirector director = new MachineGuardDirector();
        List<MachineGuardDirector.Attach> sent = director.attach(
                List.of(lance(7, 160), lance(5, 120), lance(9, 190)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT);
        assertEquals(1, sent.size(), "one a pulse");
        assertEquals(5, sent.get(0).squadId(), "the nearest to the most pressed place");
        assertEquals(1, sent.get(0).garrisonable());
        assertTrue(director.holds(5));

        List<MachineGuardDirector.Attach> next = director.attach(
                List.of(lance(7, 160), lance(9, 190)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT);
        assertEquals(1, next.size());
        assertEquals(2, next.get(0).garrisonable(), "one lance a place");
        assertEquals(9, next.get(0).squadId());

        assertTrue(director.attach(List.of(lance(7, 160)), List.of(HOT_ONE, HOT_TWO), 4, PLACE_AT).isEmpty(),
                "two on guard is the most at once");
    }

    @Test
    void aCoolPlaceAFarLanceOrAWeakLanceIsNotSent() {
        MachineGuardDirector director = new MachineGuardDirector();
        assertTrue(director.attach(List.of(lance(5, 120)), List.of(COOL_ONE), 2, PLACE_AT).isEmpty(),
                "manned but not pressed");
        assertTrue(director.attach(List.of(lance(5, 2000)), List.of(HOT_ONE), 2, PLACE_AT).isEmpty(),
                "too far to arrive while the pressure lasts");
        assertTrue(director.attach(List.of(new MachineGuardDirector.Machine(5, 120, 50, 1, 3)), List.of(HOT_ONE), 2,
                PLACE_AT).isEmpty(), "below useful strength");
        assertTrue(director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 0, PLACE_AT).isEmpty(),
                "a side with no lances has no cap");
    }

    @Test
    void aGuardStandsDownWhenThePlaceIsNoLongerMannedOrHasCooledOrItsLanceIsTakenAway() {
        MachineGuardDirector director = new MachineGuardDirector();
        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        Map<Integer, MachineGuardDirector.Machine> guarding = Map.of(5, lance(5, 100));

        assertTrue(director.review(guarding, List.of(HOT_ONE), PLACE_AT).isEmpty(), "pressed and manned: stands");
        for (int pulse = 1; pulse < MachineGuardDirector.COOL_PULSES; pulse++) {
            assertTrue(director.review(guarding, List.of(COOL_ONE), PLACE_AT).isEmpty(), "cooling, within the dwell");
        }
        assertEquals(List.of(5), director.review(guarding, List.of(COOL_ONE), PLACE_AT), "cooled for the dwell");
        assertFalse(director.holds(5));

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(guarding, List.of(), PLACE_AT), "the garrison has gone");

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(Map.of(), List.of(HOT_ONE), PLACE_AT),
                "taken up by higher-value work: the guard is withdrawn");

        director.attach(List.of(lance(5, 120)), List.of(HOT_ONE), 2, PLACE_AT);
        assertEquals(List.of(5), director.review(Map.of(5, new MachineGuardDirector.Machine(5, 100, 50, 1, 4)),
                List.of(HOT_ONE), PLACE_AT), "below useful strength");
    }

    @Test
    void usefulIsHalfTheLanceAndOneAtLeast() {
        assertTrue(MachineGuardDirector.useful(1, 1));
        assertTrue(MachineGuardDirector.useful(1, 2));
        assertFalse(MachineGuardDirector.useful(1, 3));
        assertFalse(MachineGuardDirector.useful(0, 0));
    }
}
