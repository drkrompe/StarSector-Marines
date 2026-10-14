package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AreaReservations.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The per-area reservation table on hand-built commitments: no world, no battle. */
class AreaReservationsTest {

    @Test
    void aSquadIsCountedOnceWhateverItIsReservedAs() {
        AreaReservations table = new AreaReservations(2);
        table.commit(7, 0, Kind.RALLIED);
        table.commit(7, 0, Kind.MANNED);

        assertEquals(1, table.reserved(0));
        assertEquals(0, table.count(0, Kind.RALLIED));
        assertEquals(1, table.count(0, Kind.MANNED));
        assertEquals(1, table.totalReserved());
    }

    @Test
    void committingASquadElsewhereMovesItsReservation() {
        AreaReservations table = new AreaReservations(2);
        table.commit(7, 0, Kind.STAGED);
        table.commit(7, 1, Kind.RALLIED);

        assertEquals(0, table.reserved(0));
        assertEquals(1, table.reserved(1));
        assertEquals(1, table.areaOf(7));
        assertEquals(Kind.RALLIED, table.kindOf(7));
    }

    @Test
    void anUnknownAreaReleasesAndReleaseIsIdempotent() {
        AreaReservations table = new AreaReservations(1);
        table.commit(7, 0, Kind.RALLIED);
        table.commit(7, -1, Kind.RALLIED);
        table.release(7);
        table.release(99);

        assertEquals(0, table.totalReserved());
        assertEquals(-1, table.areaOf(7));
        assertNull(table.kindOf(7));
        assertEquals(0, table.reserved(5), "an area off the table has nothing reserved");
    }

    @Test
    void whatIsUnmetIsTheDemandLessReservationsAndSquadsAlreadyNear() {
        AreaReservations table = new AreaReservations(1);
        table.commit(1, 0, Kind.RALLIED);
        table.commit(2, 0, Kind.RALLIED);
        table.commit(3, 0, Kind.STAGED);

        assertEquals(1, table.unmet(0, 5, 1));
        assertEquals(0, table.unmet(0, 3, 0));
        assertEquals(0, table.unmet(0, 2, 0), "never negative");
        assertEquals(2, table.unmet(0, 5, -4), "a negative near count is ignored");
    }

    @Test
    void anEmptyTableIsHarmless() {
        AreaReservations table = new AreaReservations(0);
        table.commit(1, 0, Kind.MANNED);

        assertEquals(0, table.totalReserved());
        assertEquals(0, table.areas());
    }
}
