package com.dillon.starsectormarines.battle.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A maneuver against a contact is bounded: a contact that never dies (a fort nest nobody can reach) must not hold a
 * squad on its flank cell for ever.
 */
class FlankAimManeuverTest {

    @Test
    void aManeuverAgainstOneContactIsSpentAfterTheLimit() {
        FlankAimMemo memo = new FlankAimMemo();
        boolean spent = false;
        for (int tick = 100; tick < 100 + 1800; tick++) spent = memo.maneuverSpent(7L, tick, 1800);
        assertFalse(spent, "one tick short of the limit");
        assertTrue(memo.maneuverSpent(7L, 1900, 1800));
        assertTrue(memo.maneuverSpent(7L, 1901, 1800), "and it stays spent while the maneuver continues");
    }

    @Test
    void aNewContactOrALongGapStartsANewManeuver() {
        FlankAimMemo memo = new FlankAimMemo();
        for (int tick = 0; tick <= 2000; tick++) memo.maneuverSpent(7L, tick, 1800);
        assertFalse(memo.maneuverSpent(8L, 2001, 1800), "another contact is a fresh maneuver");
        for (int tick = 2002; tick <= 4000; tick++) memo.maneuverSpent(8L, tick, 1800);
        assertTrue(memo.maneuverSpent(8L, 4001, 1800));
        assertFalse(memo.maneuverSpent(8L, 4100, 1800), "a gap of more than thirty ticks restarts it");
    }
}
