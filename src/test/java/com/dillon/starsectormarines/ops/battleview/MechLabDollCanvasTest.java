package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabDollCanvasTest {

    @Test
    void vacantActionCoversTheWholeFiveBySevenFabricationPad() {
        Gantry north = Gantry.covering(12, 8, 3, 6,
                Gantry.Facing.NORTH, Gantry.Holds.MACHINE);

        MechLabDollCanvas.PadBounds pad = MechLabDollCanvas.padBounds(north);

        assertEquals(11f, pad.left());
        assertEquals(7f, pad.bottom());
        assertEquals(16f, pad.right());
        assertEquals(14f, pad.top());
        assertEquals(5f, pad.width());
        assertEquals(7f, pad.height());
        assertEquals(13.5f, north.worldCenterX());
        assertEquals(11f, north.worldCenterY());
    }

    @Test
    void fabricationPadTurnsWithAnEastFacingBerth() {
        Gantry east = Gantry.covering(20, 30, 6, 3,
                Gantry.Facing.EAST, Gantry.Holds.MACHINE);

        MechLabDollCanvas.PadBounds pad = MechLabDollCanvas.padBounds(east);

        assertEquals(19f, pad.left());
        assertEquals(29f, pad.bottom());
        assertEquals(26f, pad.right());
        assertEquals(34f, pad.top());
        assertEquals(7f, pad.width());
        assertEquals(5f, pad.height());
    }

    @Test
    void dropTargetUsesTheGantryAroundTheDollWhileLeaderRetainsPhysicalAnchor() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        SocketDef core = layout.socket(SocketId.CORE);

        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), core,
                        500f, 300f, 160f, 160f);

        assertEquals(272f, target.width(), 1e-4f);
        assertEquals(108.8f, target.height(), 1e-4f);
        assertEquals(500f, target.anchorX(), 1e-4f);
        assertEquals(304.8f, target.anchorY(), 1e-4f);
        assertEquals(500f, target.centerX(), 1e-4f);
        assertEquals(60f, target.centerY(), 1e-4f);
        assertTrue(target.contains(target.centerX(), target.centerY()));
        assertTrue(target.bottom() < target.anchorY());
    }

    @Test
    void oneSlotSocketRetainsAPracticalMinimumDropAreaOnSmallDolls() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.HOUND);
        SocketDef miniFab = layout.socket(SocketId.MINI_FAB);

        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), miniFab,
                        200f, 160f, 80f, 80f);

        assertEquals(128f, target.width(), 1e-4f);
        assertEquals(76f, target.height(), 1e-4f);
        assertEquals(1, target.capacity());
    }

    @Test
    void capacityCellsAreContiguousOrderedPlacementUnitsInsideTheDropTarget() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        SocketDef arms = layout.socket(SocketId.ARMS);
        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), arms,
                        400f, 300f, 160f, 160f);

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

    @Test
    void dropTargetAndLeaderConsumeTheAuthoredDollFacing() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        SocketDef core = layout.socket(SocketId.CORE);
        DollDef eastFacing = new DollDef(90f, List.of(core));

        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(eastFacing, core,
                        500f, 300f, 160f, 160f);

        assertEquals(495.2f, target.anchorX(), 1e-3f);
        assertEquals(300f, target.anchorY(), 1e-3f);
        assertEquals(740f, target.centerX(), 1e-3f);
        assertEquals(300f, target.centerY(), 1e-3f);
    }
}
