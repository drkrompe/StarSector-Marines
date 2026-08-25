package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabDollCanvasTest {

    @Test
    void dropTargetDimensionsFollowTheDollRatherThanTheBattleCell() {
        SocketDef core = MechFittingLayout.forVariant(MechVariant.BULWARK)
                .socket(SocketId.CORE);

        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(core, 500f, 300f, 160f, 160f);

        assertEquals(60.8f, target.width(), 1e-4f);
        assertEquals(57.6f, target.height(), 1e-4f);
        assertEquals(500f, target.centerX(), 1e-4f);
        assertEquals(304.8f, target.centerY(), 1e-4f);
        assertTrue(target.contains(target.centerX(), target.centerY()));
    }

    @Test
    void oneSlotSocketRetainsAPracticalMinimumDropAreaOnSmallDolls() {
        SocketDef miniFab = MechFittingLayout.forVariant(MechVariant.HOUND)
                .socket(SocketId.MINI_FAB);

        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(miniFab, 200f, 160f, 80f, 80f);

        assertEquals(40f, target.width(), 1e-4f);
        assertEquals(32f, target.height(), 1e-4f);
        assertEquals(1, target.capacity());
    }

    @Test
    void capacityCellsAreContiguousOrderedPlacementUnitsInsideTheDropTarget() {
        SocketDef arms = MechFittingLayout.forVariant(MechVariant.BULWARK)
                .socket(SocketId.ARMS);
        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(arms, 400f, 300f, 160f, 160f);

        List<MechLabDollCanvas.CapacityCell> cells =
                MechLabDollCanvas.capacityCells(target);

        assertEquals(4, cells.size());
        for (int index = 0; index < cells.size(); index++) {
            MechLabDollCanvas.CapacityCell cell = cells.get(index);
            assertEquals(index, cell.index());
            assertTrue(cell.width() > 0f);
            assertTrue(cell.height() > 0f);
            assertTrue(target.contains(cell.x(), cell.y()));
            assertTrue(target.contains(cell.x() + cell.width(), cell.y() + cell.height()));
            if (index > 0) {
                MechLabDollCanvas.CapacityCell prior = cells.get(index - 1);
                assertTrue(cell.x() > prior.x() + prior.width());
            }
        }
    }
}
