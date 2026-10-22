package com.dillon.starsectormarines.battle.world.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwingDoorTest {
    /** A doorway at cell (10, 5) whose jambs lie either side along x: people pass along y. */
    @Test void aBodyInLineSwingsTheDoorAsItComesThrough() {
        assertEquals(0f, SwingDoor.pushedBy(10, 5, true, 10.5f, 8f), "Still down the street");
        float approaching = SwingDoor.pushedBy(10, 5, true, 10.5f, 6.6f);
        assertTrue(approaching > 0f && approaching < 1f, "Easing open: " + approaching);
        assertEquals(1f, SwingDoor.pushedBy(10, 5, true, 10.5f, 5.5f), "In the doorway");
        assertEquals(1f, SwingDoor.pushedBy(10, 5, true, 10.4f, 4.95f), "Just through");
    }

    @Test void aBodyBesideTheDoorwayBehindTheWallLeavesItShut() {
        assertEquals(0f, SwingDoor.pushedBy(10, 5, true, 12f, 5.5f));
    }

    @Test void theOtherAxisReadsAcrossX() {
        assertEquals(1f, SwingDoor.pushedBy(10, 5, false, 10.5f, 5.5f));
        assertEquals(0f, SwingDoor.pushedBy(10, 5, false, 10.5f, 7.5f), "Beside, behind the wall");
        assertTrue(SwingDoor.pushedBy(10, 5, false, 11.6f, 5.5f) > 0f, "Approaching across x");
    }
}
