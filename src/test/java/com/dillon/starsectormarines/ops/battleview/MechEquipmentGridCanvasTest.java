package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechEquipmentGridCanvasTest {

    @Test
    void catalogItemsResolveExactFootprintDimensionsWithoutThreeByTwoGhostFrames() {
        // 2x2 missile weapon
        var srm5 = MechEquipmentGridCanvas.resolveGridMetrics(null, MechWeaponComponent.SRM_5,
                120f, 80f, 6f, 3f);
        assertEquals(2, srm5.columns());
        assertEquals(2, srm5.rows());
        assertTrue(srm5.cellWidth() > 0f);
        assertTrue(srm5.cellHeight() > 0f);

        // 2x2 demolition cannon
        var demo = MechEquipmentGridCanvas.resolveGridMetrics(null, MechWeaponComponent.DEMOLITION_CANNON,
                120f, 80f, 6f, 3f);
        assertEquals(2, demo.columns());
        assertEquals(2, demo.rows());

        // 3x2 heavy cannon
        var heavy = MechEquipmentGridCanvas.resolveGridMetrics(null, MechWeaponComponent.SINGLE_HEAVY_CANNON,
                120f, 80f, 6f, 3f);
        assertEquals(3, heavy.columns());
        assertEquals(2, heavy.rows());
    }
}
