package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallDepthTest {

    /** The 3D walls stand until the player turns them off, with or without a campaign, and the save keeps the choice. */
    @Test
    void wallsStandUntilThePlayerLaysThemFlat() {
        assertTrue(WallDepth.enabled(null), "no campaign");
        Map<String, Object> saved = new HashMap<>();
        assertTrue(WallDepth.enabled(saved), "a new game");
        WallDepth.setEnabled(saved, false);
        assertFalse(WallDepth.enabled(saved), "turned off");
        WallDepth.setEnabled(saved, true);
        assertTrue(WallDepth.enabled(saved), "turned back on");
    }
}
