package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void schematicBuildsTheMachineAroundItsEngineCoreWithoutOverlaps() {
        for (MechVariant variant : MechVariant.values()) {
            MechFittingLayout layout = MechFittingLayout.forVariant(variant);
            List<MechLabDollCanvas.SocketDropTarget> targets =
                    MechLabDollCanvas.schematicTargets(layout.sockets(), 620f, 880f);
            assertEquals(layout.sockets().size(), targets.size());
            MechLabDollCanvas.SocketDropTarget core = find(targets, SocketId.CORE);
            assertEquals(310f, core.centerX(), 1e-3f, "the core is the middle of the panel");
            MechLabDollCanvas.SocketDropTarget arms = find(targets, SocketId.ARMS);
            if (arms != null) assertTrue(arms.bottom() < core.top(), "arms ahead of the core on " + variant);
            MechLabDollCanvas.SocketDropTarget left = find(targets, SocketId.LEFT_SHOULDER);
            MechLabDollCanvas.SocketDropTarget right = find(targets, SocketId.RIGHT_SHOULDER);
            if (left != null) assertTrue(left.right() < core.left(), "left shoulder beside the core on " + variant);
            if (right != null) assertTrue(right.left() > core.right(), "right shoulder beside the core on " + variant);
            MechLabDollCanvas.SocketDropTarget compartment = find(targets, SocketId.AUXILIARY);
            if (compartment != null) assertTrue(compartment.top() > core.bottom(), "compartment behind the core on " + variant);
            for (int i = 0; i < targets.size(); i++) {
                MechLabDollCanvas.SocketDropTarget a = targets.get(i);
                assertTrue(a.left() >= 0f && a.right() <= 620f && a.top() >= 0f && a.bottom() <= 880f,
                        a.id() + " leaves the panel on " + variant);
                for (int j = i + 1; j < targets.size(); j++) {
                    MechLabDollCanvas.SocketDropTarget b = targets.get(j);
                    boolean overlaps = Math.min(a.right(), b.right()) > Math.max(a.left(), b.left())
                            && Math.min(a.bottom(), b.bottom()) > Math.max(a.top(), b.top());
                    assertFalse(overlaps, a.id() + " overlaps " + b.id() + " on " + variant);
                }
            }
        }
    }

    @Test
    void everyWireRunsOrthogonallyFromItsPartToTheCoresEdge() {
        MechFittingLayout layout = MechFittingLayout.forVariant(MechVariant.BULWARK);
        List<MechLabDollCanvas.SocketDropTarget> targets =
                MechLabDollCanvas.schematicTargets(layout.sockets(), 620f, 880f);
        MechLabDollCanvas.SocketDropTarget core = find(targets, SocketId.CORE);
        for (MechLabDollCanvas.SocketDropTarget part : targets) {
            if (part == core) continue;
            List<float[]> wire = MechLabDollCanvas.wireToCore(part, core);
            float[] end = wire.get(wire.size() - 1);
            boolean onCoreEdge = (Math.abs(end[1] - core.top()) < 1e-3f || Math.abs(end[1] - core.bottom()) < 1e-3f
                    || Math.abs(end[0] - core.left()) < 1e-3f || Math.abs(end[0] - core.right()) < 1e-3f);
            assertTrue(onCoreEdge, part.id() + " wire ends off the core");
            for (int i = 1; i < wire.size(); i++) {
                float[] a = wire.get(i - 1), b = wire.get(i);
                assertTrue(a[0] == b[0] || a[1] == b[1], part.id() + " wire has a diagonal leg");
            }
        }
    }

    private static MechLabDollCanvas.SocketDropTarget find(List<MechLabDollCanvas.SocketDropTarget> targets,
                                                           SocketId id) {
        return targets.stream().filter(target -> target.id() == id).findFirst().orElse(null);
    }
}
