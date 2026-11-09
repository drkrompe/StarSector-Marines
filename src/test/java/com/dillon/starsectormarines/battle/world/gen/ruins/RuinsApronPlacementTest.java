package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where an entrance's apron stands against its door ({@code mapgen-nouns.md}, Ruins ground). */
class RuinsApronPlacementTest {

    @Test
    void theApronIsOpenGroundInFrontOfTheDoorClearOfTheFace() {
        Area door = new Area(120, 40, 120, 43);
        Area apron = RuinsDelveStage.apronFor(door);
        // West of the door, with room for the hull face to jog a cell either way before it.
        assertTrue(apron.right() <= door.left() - 3, "apron runs into the face: " + apron);
        assertTrue(apron.bottom() <= door.bottom() && apron.top() >= door.top(), "apron does not front the door");
        // Two 5x5 berths side by side fit inside it.
        assertTrue(apron.width() >= 2 * 5 + 1 && apron.height() >= 5, "apron too small for two berths: " + apron);
    }
}
