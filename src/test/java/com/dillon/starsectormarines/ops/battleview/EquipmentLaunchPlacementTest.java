package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.EquipmentLayerDef.Occlusion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentLaunchPlacementTest {
    private final EquipmentLayerComposer.Placement carried =
            new EquipmentLayerComposer.Placement(20f, 40f, 10f, 24f, 350f, Occlusion.OVER_BODY);

    @Test
    void authoredOffCenterMuzzleRegistersBothAxes() {
        var released = EquipmentLayerComposer.launch(carried, 100f, 200f,
                0f, 0f, 50f, .2f, .25f);
        assertEquals(103f, released.centerX(), .0001f);
        assertEquals(194f, released.centerY(), .0001f);
    }

    @Test
    void releaseRegistersNorthEdgeWithPhysicalMuzzleAtIndependentBearing() {
        var released = EquipmentLayerComposer.launch(carried, 100f, 200f, 90f, 0f, 50f, .5f, 20f / 192f);
        assertEquals(109.5f, released.centerX(), .0001f);
        assertEquals(200f, released.centerY(), .0001f);
        assertEquals(90f, released.angleDegrees());
        assertEquals(carried.width(), released.width());
    }

    @Test
    void committedPulseKicksAndReturnsToCarriedJoint() {
        var kicked = EquipmentLayerComposer.launch(carried, 100f, 200f, 0f, .3f, 50f, .5f, 20f / 192f);
        assertTrue(kicked.centerY() < 190.5f);
        var settled = EquipmentLayerComposer.launch(carried, 100f, 200f, 0f, 1f, 50f, .5f, 20f / 192f);
        assertEquals(carried.centerX(), settled.centerX(), .0001f);
        assertEquals(carried.centerY(), settled.centerY(), .0001f);
        assertEquals(-10f, settled.angleDegrees(), .0001f);
        assertEquals(carried.occlusion(), settled.occlusion());
    }
}
