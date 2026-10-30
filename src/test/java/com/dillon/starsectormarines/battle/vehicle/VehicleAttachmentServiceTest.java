package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleAttachmentServiceTest {

    @Test void aFollowerStandsBehindItsSquadAlongItsAxisAndToItsSide() {
        float[] behind = VehicleAttachmentService.followPoint(20f, 10f, 1f, 0f, 10f, 0f);
        assertEquals(10f, behind[0], 1e-4);
        assertEquals(10f, behind[1], 1e-4);
        float[] left = VehicleAttachmentService.followPoint(20f, 10f, 1f, 0f, 4f, 3f);
        assertEquals(16f, left[0], 1e-4);
        assertEquals(13f, left[1], 1e-4, "left of an eastward axis is north");
    }

    @Test void followersAlternateSidesOutwardFromTheTrack() {
        float step = VehicleAttachmentService.SIDE_STEP_CELLS;
        assertEquals(0f, VehicleAttachmentService.sideOffset(0));
        assertEquals(step, VehicleAttachmentService.sideOffset(1));
        assertEquals(-step, VehicleAttachmentService.sideOffset(2));
        assertEquals(2 * step, VehicleAttachmentService.sideOffset(3));
    }
}
