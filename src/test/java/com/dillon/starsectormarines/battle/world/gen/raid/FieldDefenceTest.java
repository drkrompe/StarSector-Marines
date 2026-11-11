package com.dillon.starsectormarines.battle.world.gen.raid;

import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.DefenceDoctrine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A refinery field's approaches and dues ({@link FieldDefence}): on its edge, stepping out, two posts each. */
class FieldDefenceTest {

    /** Each approach stands on the field's edge, and its step leads out of the field, across its whole width. */
    @Test
    void anApproachStandsOnTheEdgeAndStepsOut() {
        boolean[][] field = FieldDefence.mask(80, 80, 30, 30, 50, 60);
        for (DefenceDoctrine.Gate g : new DefenceDoctrine.Gate[]{FieldDefence.spineEnd(38, 42, 30, -1),
                FieldDefence.spineEnd(38, 42, 60, 1), FieldDefence.laneMouth(34, 38, 30, -1),
                FieldDefence.laneMouth(34, 38, 50, 1)}) {
            int alongX = -g.outY(), alongY = g.outX();
            for (int t = -g.halfWidth(); t <= g.halfWidth(); t++) {
                int x = g.x() + alongX * t, y = g.y() + alongY * t;
                assertTrue(field[x][y], "on the field's edge");
                assertFalse(field[x + g.outX()][y + g.outY()], "and one step out is the country");
            }
        }
    }

    /** A field owes two posts per approach, of the kind it is given, and nothing with no approach. */
    @Test
    void eachApproachIsOwedTwoPosts() {
        assertEquals(Map.of(DefensePostKind.MEDIUM, 6), FieldDefence.owed(DefensePostKind.MEDIUM, 3));
        assertTrue(FieldDefence.owed(DefensePostKind.LIGHT, 0).isEmpty());
    }
}
