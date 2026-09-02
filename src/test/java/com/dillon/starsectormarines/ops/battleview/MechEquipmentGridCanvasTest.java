package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechEquipmentGridCanvasTest {

    @Test
    void catalogItemsResolveExactFootprintDimensionsWithoutThreeByTwoGhostFrames() {
        // 1x1 missile weapon
        var srm5 = MechEquipmentGridCanvas.resolveGridMetrics(null, MechWeaponComponent.SRM_5,
                120f, 80f, 6f, 3f);
        assertEquals(1, srm5.columns());
        assertEquals(1, srm5.rows());
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

    @Test
    void socketDefinitionsResolveExactAuthoredDimensions() {
        MechFittingLayout hound = MechFittingLayout.forVariant(MechVariant.HOUND);

        // 2x2 Nose mount
        SocketDef nose = hound.socket(SocketId.ARMS);
        var noseMetrics = MechEquipmentGridCanvas.resolveGridMetrics(nose, null, 120f, 80f, 6f, 3f);
        assertEquals(2, noseMetrics.columns());
        assertEquals(2, noseMetrics.rows());

        // 2x2 Shoulder mount
        SocketDef shoulder = hound.socket(SocketId.LEFT_SHOULDER);
        var shoulderMetrics = MechEquipmentGridCanvas.resolveGridMetrics(shoulder, null, 120f, 80f, 6f, 3f);
        assertEquals(2, shoulderMetrics.columns());
        assertEquals(2, shoulderMetrics.rows());

        // 2x1 Ammo Reserve
        SocketDef ammo = hound.socket(SocketId.AMMO_RESERVE);
        var ammoMetrics = MechEquipmentGridCanvas.resolveGridMetrics(ammo, null, 120f, 80f, 6f, 3f);
        assertEquals(2, ammoMetrics.columns());
        assertEquals(1, ammoMetrics.rows());

        // 3x2 Sirocco Nose Cannon
        MechFittingLayout sirocco = MechFittingLayout.forVariant(MechVariant.SIROCCO);
        SocketDef siroccoNose = sirocco.socket(SocketId.ARMS);
        var siroccoMetrics = MechEquipmentGridCanvas.resolveGridMetrics(siroccoNose, null, 120f, 80f, 6f, 3f);
        assertEquals(3, siroccoMetrics.columns());
        assertEquals(2, siroccoMetrics.rows());

        // 1x1 Mini-Fab
        SocketDef miniFab = sirocco.socket(SocketId.MINI_FAB);
        var miniFabMetrics = MechEquipmentGridCanvas.resolveGridMetrics(miniFab, null, 120f, 80f, 6f, 3f);
        assertEquals(1, miniFabMetrics.columns());
        assertEquals(1, miniFabMetrics.rows());
    }
}
