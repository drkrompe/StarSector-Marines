package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.DoorStance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuinsDoorStatesTest {

    private static RuinsDelve.LeafDoor door(int x, DoorStance stance) {
        return new RuinsDelve.LeafDoor(new RuinsDelve.Area(x, 0, x + 2, 0), true, false, stance);
    }

    @Test
    void aDoorStandsAsItWasLaidUntilTheBattleChangesIt() {
        RuinsDelve.LeafDoor dead = door(0, DoorStance.DEAD), answers = door(10, DoorStance.ANSWERS);
        RuinsDoorStates states = new RuinsDoorStates(List.of(dead, answers));
        assertEquals(DoorStance.DEAD, states.stanceOf(dead));
        int before = states.version();
        states.force(dead);
        assertEquals(DoorStance.FORCED, states.stanceOf(dead));
        assertEquals(DoorStance.ANSWERS, states.stanceOf(answers));
        assertNotEquals(before, states.version());
    }

    @Test
    void aDoorTornWiderStandsAtItsNewSpanOnlyAlongItsOwnLine() {
        RuinsDelve.LeafDoor hall = new RuinsDelve.LeafDoor(new RuinsDelve.Area(4, 7, 5, 7), true, false,
                DoorStance.DEAD);
        RuinsDoorStates states = new RuinsDoorStates(List.of(hall));
        assertEquals(hall.span(), states.spanOf(hall));
        assertThrows(IllegalArgumentException.class, () -> states.tear(hall, new RuinsDelve.Area(3, 6, 6, 7)));
        assertThrows(IllegalArgumentException.class, () -> states.tear(hall, new RuinsDelve.Area(5, 7, 8, 7)));
        RuinsDelve.Area wide = new RuinsDelve.Area(3, 7, 6, 7);
        states.tear(hall, wide);
        assertEquals(DoorStance.TORN, states.stanceOf(hall));
        assertEquals(wide, states.spanOf(hall));
    }

    @Test
    void anAddedDoorIsHeldAfterTheLaidOnesAndASealedOneOpensToAnswer() {
        RuinsDelve.LeafDoor laid = door(0, DoorStance.ANSWERS), bulkhead = door(20, DoorStance.ANSWERS);
        RuinsDoorStates states = new RuinsDoorStates(List.of(laid));
        states.add(bulkhead, DoorStance.SEALED);
        assertEquals(List.of(laid, bulkhead), states.doors());
        assertEquals(DoorStance.SEALED, states.stanceOf(bulkhead));
        states.open(bulkhead);
        assertEquals(DoorStance.ANSWERS, states.stanceOf(bulkhead));
        assertThrows(IllegalArgumentException.class, () -> states.open(bulkhead));
        assertThrows(IllegalArgumentException.class, () -> states.force(door(40, DoorStance.DEAD)));
    }
}
