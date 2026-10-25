package com.dillon.starsectormarines.battle.cordon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a built redoubt's wall teams share its posts ({@link CordonGarrisons#stretches}, {@link CordonGarrisons#hold}). */
class CordonWallTeamsTest {

    @Test
    void eachTeamHoldsItsOwnStretchOfTheFace() {
        List<int[]> posts = new ArrayList<>();
        for (int x = 108; x >= 80; x -= 4) posts.add(new int[]{x, 198}); // 8 posts, given east to west
        posts.add(new int[]{84, 197});
        List<List<int[]>> stretches = CordonGarrisons.stretches(posts, 3);
        assertEquals(3, stretches.size());
        for (List<int[]> s : stretches) assertEquals(3, s.size());
        int lastEast = Integer.MIN_VALUE;
        for (List<int[]> s : stretches) {
            for (int[] c : s) assertTrue(c[0] >= lastEast, "stretches do not overlap along the face");
            lastEast = s.get(s.size() - 1)[0];
        }
    }

    @Test
    void aTeamHoldsAtThePostNearestItsMiddleOutToItsFarthest() {
        int[] hold = CordonGarrisons.hold(List.of(new int[]{80, 198}, new int[]{84, 198}, new int[]{88, 198}));
        assertArrayEquals(new int[]{84, 198, 4}, hold);
        assertEquals(1, CordonGarrisons.hold(List.of(new int[]{80, 198}))[2], "never less than a cell");
    }
}
