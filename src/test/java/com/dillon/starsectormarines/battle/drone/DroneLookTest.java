package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hub's drone is built in the hub's tech family. */
class DroneLookTest {

    @Test
    void aHubsDroneIsBuiltInItsTechFamily() {
        assertEquals(DroneLook.HUB_LOW_TECH, DroneLook.forHub(EmplacementStyle.BRUTALIST));
        assertEquals(DroneLook.HUB_LOW_TECH, DroneLook.forHub(EmplacementStyle.EARTHWORK));
        assertEquals(DroneLook.HUB_MIDLINE, DroneLook.forHub(EmplacementStyle.MODULAR));
        assertEquals(DroneLook.HUB_MIDLINE, DroneLook.forHub(EmplacementStyle.BLOCKWORK));
        assertEquals(DroneLook.HUB_HIGH_TECH, DroneLook.forHub(EmplacementStyle.COMPOSITE));
        assertEquals(DroneLook.HUB_HIGH_TECH, DroneLook.forHub(EmplacementStyle.DEPLOYED));
    }

    @Test
    void oldWorksAndTheirPatchesAreLowTech() {
        assertEquals(DroneLook.HUB_LOW_TECH, DroneLook.forHub(EmplacementStyle.DERELICT));
        assertEquals(DroneLook.HUB_LOW_TECH, DroneLook.forHub(EmplacementStyle.PATCHWORK));
        assertEquals(DroneLook.HUB_LOW_TECH, DroneLook.forHub(null));
    }
}
