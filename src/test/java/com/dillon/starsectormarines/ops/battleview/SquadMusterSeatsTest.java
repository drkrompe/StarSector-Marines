package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A lost marine's seat stays empty where it stood, until a recruit takes the earliest one. */
public class SquadMusterSeatsTest {

    private static List<String> members(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add("m" + i);
        return ids;
    }

    @Test
    public void aLostMarineLeavesItsSeatEmptyAndNobodyMoves() {
        Set<String> lost = Set.of("m7", "m9");
        List<String> seats = SquadMuster.seats(members(12), id -> !lost.contains(id));

        assertNull(seats.get(7));
        assertNull(seats.get(9));
        assertEquals("m8", seats.get(8));
        assertEquals("m11", seats.get(11));
    }

    @Test
    public void aRecruitTakesTheEarliestSeatLeftEmpty() {
        Set<String> lost = Set.of("m7", "m9");
        List<String> seats = SquadMuster.seats(members(13), id -> !lost.contains(id));

        assertEquals("m12", seats.get(7));
        assertNull(seats.get(9));
    }

    @Test
    public void seatsNeverOverflowTheSquad() {
        List<String> seats = SquadMuster.seats(members(14), id -> true);

        assertEquals(12, seats.size());
        assertEquals("m11", seats.get(11));
    }
}
