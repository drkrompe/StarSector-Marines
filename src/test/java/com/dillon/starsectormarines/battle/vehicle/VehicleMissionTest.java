package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleMissionTest {

    @Test
    void landingPoseFacesTheOutboundCorridor() {
        VehicleMission mission = new VehicleMission(
                new float[]{5.5f, 10.5f}, new float[]{10.5f, 10.5f},
                new float[]{10.5f, 10.5f, 10.5f}, new float[]{10.5f, 10.5f, 20.5f},
                0f, 4);

        assertEquals(0f, mission.lzDepartureFacingDeg, 0.01f,
                "docking should align the parked vehicle north for its northbound departure");
    }
}
