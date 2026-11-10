package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcanicGroundLookTest {

    private static CellTopology ground(GroundKind kind, int size) {
        CellTopology t = new CellTopology(size, size);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) t.setGroundKind(x, y, kind);
        return t;
    }

    @Test
    void openWildGroundGlowsAndDriftsTheSameEveryTime() {
        List<VolcanicGroundLook.Mark> marks = VolcanicGroundLook.marks(ground(GroundKind.STONE, 96));
        assertTrue(marks.stream().anyMatch(m -> m.kind() == VolcanicGroundLook.Kind.ASH));
        assertTrue(marks.stream().anyMatch(m -> m.kind() != VolcanicGroundLook.Kind.ASH));
        assertEquals(marks, VolcanicGroundLook.marks(ground(GroundKind.STONE, 96)));
    }

    @Test
    void nothingMarksGroundThatIsNotWildStoneOrSand() {
        assertTrue(VolcanicGroundLook.marks(ground(GroundKind.STREET, 96)).isEmpty());
        CellTopology built = ground(GroundKind.STONE, 96);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
            if ((x / 2 + y / 2) % 2 == 0) built.setWall(x, y, true);
        }
        // Walls in a 2x2 checkerboard: every 3x3 footing takes in one.
        assertTrue(VolcanicGroundLook.marks(built).isEmpty());
    }

    @Test
    void aMarksFootingIsAllOpenGround() {
        CellTopology t = ground(GroundKind.STONE, 96);
        t.setBuildingId(40, 40, 3);
        assertFalse(VolcanicGroundLook.footing(t, 41, 41, 1));
        assertTrue(VolcanicGroundLook.footing(t, 42, 42, 1));
        assertFalse(VolcanicGroundLook.footing(t, 0, 10, 1));
        for (VolcanicGroundLook.Mark m : VolcanicGroundLook.marks(t)) {
            int r = m.kind() == VolcanicGroundLook.Kind.ASH ? VolcanicGroundLook.ASH_FOOTING : VolcanicGroundLook.GLOW_FOOTING;
            assertTrue(VolcanicGroundLook.footing(t, (int) m.x(), (int) m.y(), r));
        }
    }
}
