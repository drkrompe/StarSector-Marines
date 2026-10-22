package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechEquipmentGridCanvasTest {

    @Test
    void catalogItemsResolveExactFootprintDimensionsWithoutGhostFrames() {
        for (MechWeaponComponent component : MechWeaponComponent.values()) {
            var metrics = MechEquipmentGridCanvas.resolveGridMetrics(null, component,
                    120f, 80f, 6f, 3f);
            assertEquals(component.footprintColumns, metrics.columns(), component.name());
            assertEquals(component.footprintRows, metrics.rows(), component.name());
            assertTrue(metrics.cellWidth() > 0f);
            assertTrue(metrics.cellHeight() > 0f);
        }
    }
}
