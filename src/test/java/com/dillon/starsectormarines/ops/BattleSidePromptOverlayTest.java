package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where the world prompt goes: only for a place in view and clear of the rail, and never under the rail. */
class BattleSidePromptOverlayTest {
    private static final float INF = Float.POSITIVE_INFINITY;

    @Test void aPlaceOutOfViewHasNoPrompt() {
        assertNull(BattleSidePromptOverlay.place(-20f, 300f, 200f, 60f, 0f, 0f, 1000f, 600f, INF, INF, 36f));
        assertNull(BattleSidePromptOverlay.place(500f, 700f, 200f, 60f, 0f, 0f, 1000f, 600f, INF, INF, 36f));
    }

    @Test void aPlaceUnderTheRailHasNoPrompt() {
        assertNull(BattleSidePromptOverlay.place(900f, 550f, 200f, 60f, 0f, 0f, 1000f, 600f, 800f, 300f, 36f));
        assertNotNull(BattleSidePromptOverlay.place(900f, 200f, 200f, 60f, 0f, 0f, 1000f, 600f, 800f, 300f, 36f),
                "below the rail's bottom edge the place is clear");
    }

    @Test void thePromptHangsCenteredAboveThePlaceAndFlipsBelowWhenThereIsNoRoom() {
        assertArrayEquals(new float[] {400f, 336f},
                BattleSidePromptOverlay.place(500f, 300f, 200f, 60f, 0f, 0f, 1000f, 600f, INF, INF, 36f));
        assertArrayEquals(new float[] {400f, 464f},
                BattleSidePromptOverlay.place(500f, 560f, 200f, 60f, 0f, 0f, 1000f, 600f, INF, INF, 36f));
    }

    @Test void thePromptStaysInViewAndLeftOfTheRail() {
        float[] edge = BattleSidePromptOverlay.place(20f, 300f, 200f, 60f, 0f, 0f, 1000f, 600f, INF, INF, 36f);
        assertTrue(edge[0] >= 8f, "kept inside the left edge");
        float[] nearRail = BattleSidePromptOverlay.place(780f, 400f, 200f, 60f, 0f, 0f, 1000f, 600f, 800f, 450f, 36f);
        assertTrue(nearRail[0] + 200f <= 800f - 8f, "kept clear of the rail it would run under");
    }
}
