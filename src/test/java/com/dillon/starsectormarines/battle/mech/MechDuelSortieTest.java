package com.dillon.starsectormarines.battle.mech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechDuelSortieTest {
    private static final int[] POST = {100, 20};
    private static final float LEASH = MechAssignmentBoundary.EXACT_CELL_LEASH;

    @Test
    void aPostedMechSortiesOnlyAgainstAnOutReachingShooterItCannotAnswerFromWhereItStands() {
        // A 30-cell Hound on its post, a 38-cell Warden 35 cells off: it shells the leash and nothing answers.
        assertEquals(LEASH + 38f, MechDuelRange.sortieRadius(POST, 65.5f, 20.5f, true, 35f, 30f, 38f), 0.0001f);
        // Inside the leash and inside its own reach: it fights from the post.
        assertEquals(0f, MechDuelRange.sortieRadius(POST, 75.5f, 20.5f, true, 25f, 30f, 38f), 0.0001f);
        // Already out on a sortie and closing: it stays out while the shooter can still shell the leash.
        assertEquals(LEASH + 38f, MechDuelRange.sortieRadius(POST, 65.5f, 20.5f, false, 20f, 30f, 38f), 0.0001f);
        // The shooter has fallen back beyond reach of the leash: the sortie is over.
        assertEquals(0f, MechDuelRange.sortieRadius(POST, 50.5f, 20.5f, false, 20f, 30f, 38f), 0.0001f);
        // An equal or shorter reach never draws a mech off its post.
        assertEquals(0f, MechDuelRange.sortieRadius(POST, 65.5f, 20.5f, true, 35f, 30f, 30.5f), 0.0001f);
    }
}
