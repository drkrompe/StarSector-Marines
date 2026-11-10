package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorTrimRenderSystemTest {

    private static final String RAISED = "ruins-ground.raised-floor", STAGE = "ruins-ground.stage-floor";

    @Test
    void aBuiltFloorStepsDownToAnyOtherOpenFloorButNotToItselfOrAWall() {
        assertTrue(FloorTrimRenderSystem.steps(RAISED, true, "sky-port.deck-plate"));
        assertTrue(FloorTrimRenderSystem.steps(STAGE, true, null), "to a floor of no surface of its own");
        assertTrue(FloorTrimRenderSystem.steps(RAISED, true, STAGE), "one built floor against another");
        assertFalse(FloorTrimRenderSystem.steps(RAISED, true, RAISED), "inside the panel");
        assertFalse(FloorTrimRenderSystem.steps(RAISED, false, null), "against a wall");
        assertFalse(FloorTrimRenderSystem.steps("sky-port.deck-plate", true, RAISED), "the slab has no trim");
    }
}
