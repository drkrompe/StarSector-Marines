package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFlankTest {

    @Test
    void platformsLeaveAtRightAnglesToAlternatingSides() {
        float forward = 0.3f;
        float even = BloomFlank.outward(forward, 0);
        float odd = BloomFlank.outward(forward, 1);
        assertEquals(Math.PI / 2.0, even - forward, 1e-5);
        assertEquals(-Math.PI / 2.0, odd - forward, 1e-5);
    }

    @Test
    void theBulgeFollowsTheSpecsApproachButStaysWithinWhatALegAllows() {
        assertEquals(0.5f, BloomFlank.loop(47f, 45f), 0.06f);
        assertEquals(BloomFlank.MAX_LOOP, BloomFlank.loop(47f, 10f), 0f);
        assertEquals(BloomFlank.MIN_LOOP, BloomFlank.loop(1f, 100f), 0f);
    }

    @Test
    void aFlankingPathSwingsAbeamAndArrivesFromTheSide() {
        float chord = 40f;
        BloomPath straight = BloomPath.plan(1L, 0f, 0f, chord, 0f, 3f, 0f, 0f, 0f);
        BloomPath flank = BloomPath.plan(1L, 0f, 0f, chord, 0f, 3f, BloomFlank.outward(0f, 0),
                BloomFlank.loop(47f, chord), 0f);
        float widest = 0f;
        for (float t = 0f; t <= 3f; t += 0.05f) widest = Math.max(widest, Math.abs(flank.y(t)));
        assertTrue(widest > 0.2f * chord, "swings abeam: " + widest);
        assertEquals(0f, Math.abs(straight.y(1.5f)), 1e-4f);
        // The nose at the last instant is well off the line of fire: it comes in from the side.
        float arrival = Math.abs(AirBody.facingToward(
                flank.x(3f) - flank.x(2.95f), flank.y(3f) - flank.y(2.95f))
                - AirBody.facingToward(1f, 0f));
        float off = Math.min(arrival, 360f - arrival);
        assertTrue(off > 30f, "arrives " + off + " degrees off the line of fire");
        assertEquals(chord, flank.x(3f), 1e-3f);
        assertEquals(0f, flank.y(3f), 1e-3f);
    }
}
