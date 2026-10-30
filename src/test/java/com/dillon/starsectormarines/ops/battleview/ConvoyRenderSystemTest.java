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
    void firingKicksAHeadsGunBackAlongItsBearingWhileTheHousingStays() {
        // Bearing 0 faces +Y: the gun stands 2 cells ahead of the head's centre at rest.
        float[] rest = ConvoyRenderSystem.gunLayerCentre(10f, 20f, 0f, 2f, 0f);
        assertEquals(10f, rest[0], 1e-5);
        assertEquals(22f, rest[1], 1e-5);
        float[] kicked = ConvoyRenderSystem.gunLayerCentre(10f, 20f, 0f, 2f, .18f);
        assertEquals(10f, kicked[0], 1e-5);
        assertEquals(21.82f, kicked[1], 1e-5, "the gun comes back by its kick");
        // Facing +X (bearing -90), the kick runs back along -X.
        float[] east = ConvoyRenderSystem.gunLayerCentre(0f, 0f, -90f, 2f, .5f);
        assertEquals(1.5f, east[0], 1e-5);
        assertEquals(0f, east[1], 1e-5);
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
