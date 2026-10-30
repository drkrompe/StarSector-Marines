package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvoyRenderSystemTest {

    @Test
    void aRaisedRackPitchesToTheSlopeItsRoundsDrawnArcLeavesAt() {
        // The arc climbs 4 * arcHeight over the run: 5 cells of arc at 20 cells leaves at 45 degrees.
        assertEquals(Math.PI / 4, ConvoyRenderSystem.launchPitch(5f, 20f), 1e-5);
        assertTrue(ConvoyRenderSystem.launchPitch(5f, 10f) > ConvoyRenderSystem.launchPitch(5f, 40f),
                "the same lob at a nearer target leaves steeper");
        assertEquals(Math.toRadians(70), ConvoyRenderSystem.launchPitch(5f, 0.5f), 1e-5);
        assertEquals(0f, ConvoyRenderSystem.launchPitch(0f, 20f));
    }

    @Test
    void aRackFiringTowardTheBottomOfTheScreenStandsOnlyAsTallAsStillPointsAtItsTarget() {
        float south = ConvoyRenderSystem.readablePitch(-1f);
        assertEquals(ConvoyRenderSystem.MIN_RAISED_ALONG, Math.cos(south) - Math.sin(south), 1e-5);
        assertTrue(ConvoyRenderSystem.readablePitch(0f) > south, "across the screen it may stand taller");
        assertTrue(ConvoyRenderSystem.readablePitch(1f) > Math.PI / 2, "firing north it never binds");
    }

    @Test
    void wornTurretPlateIsMarkedOnlyOverATurretDrawnFromTheSheet() {
        assertTrue(ConvoyRenderSystem.wornPlateOnTurretFrame(false, false, false), "a fixed hull's own turret");
        assertTrue(ConvoyRenderSystem.wornPlateOnTurretFrame(true, false, false), "a modular hull's head");
        assertFalse(ConvoyRenderSystem.wornPlateOnTurretFrame(true, true, false), "a bare ring has no turret to mark");
        assertFalse(ConvoyRenderSystem.wornPlateOnTurretFrame(true, false, true), "nor a weapon drawn as itself");
        assertFalse(ConvoyRenderSystem.wornPlateOnTurretFrame(false, false, true));
    }
}
