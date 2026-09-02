package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.JobBoard;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechLabDollCanvasTest {

    @Test
    void weldingFxFollowWorkInTheFramedBayRatherThanAnActorBodyType() {
        int bay = 7;

        assertTrue(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay, Affordance.SERVICE, 2), bay, false));
        assertTrue(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay, Affordance.FABRICATE), bay, false));
        assertTrue(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay, Affordance.REPAIR), bay, true));
        assertFalse(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay, Affordance.REPAIR), bay, false));
        assertFalse(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay, Affordance.READOUT), bay, true));
        assertFalse(MechLabDollCanvas.isWeldingJob(
                JobBoard.group(bay + 1, Affordance.SERVICE, 2), bay, true));
        assertFalse(MechLabDollCanvas.isWeldingJob(null, bay, true));
    }

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
    void repairFocusOnTheServicePadIsNotPresentedAsMachineWelding() {
        Gantry berth = Gantry.covering(12, 8, 3, 6,
                Gantry.Facing.NORTH, Gantry.Holds.MACHINE);

        assertTrue(MechLabDollCanvas.focusInsideServicePad(
                List.of(berth), 13.5f, 8.5f));
        assertFalse(MechLabDollCanvas.focusInsideServicePad(
                List.of(berth), 18.5f, 8.5f));
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
        assertEquals(1, target.gridColumns());
        assertEquals(1, target.gridRows());
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

        assertEquals(6, cells.size());
        for (int index = 0; index < cells.size(); index++) {
            MechLabDollCanvas.CapacityCell cell = cells.get(index);
            assertEquals(index, cell.index());
            assertTrue(cell.width() > 0f);
            assertTrue(cell.height() > 0f);
            assertTrue(target.contains(cell.x(), cell.y()));
            assertTrue(target.contains(cell.x() + cell.width(), cell.y() + cell.height()));
            assertTrue(cell.active());
            if (index > 0 && cell.row() == cells.get(index - 1).row()) {
                MechLabDollCanvas.CapacityCell prior = cells.get(index - 1);
                assertTrue(cell.x() > prior.x() + prior.width());
            }
        }
    }

    @Test
    void houndArmGridShowsFourUsableCellsInsideTheCommonSixCellFrame() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.HOUND);
        SocketDef arms = layout.socket(SocketId.ARMS);
        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), arms,
                        400f, 300f, 160f, 160f);

        List<MechLabDollCanvas.CapacityCell> cells =
                MechLabDollCanvas.capacityCells(target);

        assertEquals(6, cells.size());
        assertEquals(4, cells.stream().filter(
                MechLabDollCanvas.CapacityCell::active).count());
        assertFalse(cells.get(2).active());
        assertFalse(cells.get(5).active());
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

    @Test
    void leaderDogLegPathOrthogonalGeometryKeepsCenterClear() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        SocketDef left = layout.socket(SocketId.MINI_FAB);
        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), left,
                        500f, 300f, 160f, 160f);

        List<float[]> points = MechLabDollCanvas.leaderDogLegPath(target.anchorX(), target.anchorY(), target);
        assertFalse(points.isEmpty());
        // First point is the anchor
        assertEquals(target.anchorX(), points.get(0)[0], 1e-3f);
        assertEquals(target.anchorY(), points.get(0)[1], 1e-3f);
        // Last point terminates on dock edge
        float[] last = points.get(points.size() - 1);
        assertTrue(last[0] >= target.left() - 1e-3f && last[0] <= target.right() + 1e-3f);
        assertTrue(last[1] >= target.top() - 1e-3f && last[1] <= target.bottom() + 1e-3f);
    }

    @Test
    void socketAtDetectsHitOnTarget() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        SocketDef arms = layout.socket(SocketId.ARMS);
        MechLabDollCanvas.SocketDropTarget target =
                MechLabDollCanvas.socketDropTarget(layout.doll(), arms,
                        400f, 300f, 160f, 160f);

        assertTrue(target.contains(target.centerX(), target.centerY()));
        assertFalse(target.contains(target.centerX() + 500f, target.centerY()));
    }

    @Test
    void resolveExplodedDropTargetsGuaranteesZeroPairwiseOverlapsAcrossAllVariants() {
        for (MechVariant variant : MechVariant.values()) {
            MechFittingLayout layout = MechFittingLayout.forVariant(variant);
            // Standard resolution canvas
            List<MechLabDollCanvas.SocketDropTarget> targets =
                    MechLabDollCanvas.resolveExplodedDropTargets(layout.doll(), layout.sockets(),
                            450f, 290f, 160f, 160f, 900f, 580f);

            assertEquals(layout.sockets().size(), targets.size());

            // Check no pairwise overlaps
            for (int i = 0; i < targets.size(); i++) {
                MechLabDollCanvas.SocketDropTarget a = targets.get(i);
                // Bounds check
                assertTrue(a.left() >= 0f, "Target " + a.id() + " outside left on " + variant);
                assertTrue(a.right() <= 900f, "Target " + a.id() + " outside right on " + variant);
                assertTrue(a.top() >= 0f, "Target " + a.id() + " outside top on " + variant);
                assertTrue(a.bottom() <= 580f, "Target " + a.id() + " outside bottom on " + variant);

                for (int j = i + 1; j < targets.size(); j++) {
                    MechLabDollCanvas.SocketDropTarget b = targets.get(j);
                    float overlapX = Math.min(a.right(), b.right()) - Math.max(a.left(), b.left());
                    float overlapY = Math.min(a.bottom(), b.bottom()) - Math.max(a.top(), b.top());
                    boolean overlaps = overlapX > 0f && overlapY > 0f;
                    assertFalse(overlaps, "Targets " + a.id() + " and " + b.id()
                            + " overlap on " + variant + ": overlapX=" + overlapX + ", overlapY=" + overlapY);
                }
            }
        }
    }
}
