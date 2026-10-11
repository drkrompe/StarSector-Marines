package com.dillon.starsectormarines.battle.world.gen.forest;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ForestFieldTest {
    @Test void sparseDragIsContinuousAndErasingClearsAllOverhangingCanopies() throws Exception {
        ForestPlan original = ForestBrushTest.plan();
        ForestPlan erased = original.append(new ForestPlan.Stroke(ForestPlan.Mode.ERASE, 2,
                List.of(new ForestPlan.Point(0, 12), new ForestPlan.Point(23, 12))));
        assertEquals(erased, ForestPlan.read(erased.json()));
        ForestField a = ForestField.build(original), b = ForestField.build(erased);
        assertEquals(a.trees(), ForestField.build(original).trees());
        assertTrue(a.blocked(12, 12));
        for (int x = 0; x < 24; x++) for (int y = 10; y <= 14; y++) {
            assertFalse(b.blocked(x, y));
            for (var tree : b.trees()) assertFalse(tree.blocks(x, y));
        }
        assertFalse(b.trees().isEmpty());
        // Later painting restores the erased corridor to eligibility.
        ForestPlan repainted = erased.append(new ForestPlan.Stroke(ForestPlan.Mode.PAINT, 8,
                List.of(new ForestPlan.Point(12, 12))));
        assertTrue(ForestField.build(repainted).blocked(12, 12));
    }

    @Test void speciesChoiceAndRotationsKeepTheDeclaredFootprint() {
        for (var woodland : ForestPlan.Woodland.values()) {
            ForestPlan input = ForestBrushTest.plan();
            ForestField field = ForestField.build(new ForestPlan(24, 24, woodland, input.seed(), input.strokes()));
            for (var tree : field.trees()) assertTrue(woodland.includes(tree.canopy()));
        }
        for (var canopy : TreeCanopy.values()) for (int turn = 0; turn < 4; turn++) {
            int expected = 0, actual = 0;
            for (int y = 0; y < canopy.height; y++) for (int x = 0; x < canopy.width; x++)
                if (canopy.blocks(x, y, 0)) expected++;
            for (int y = 0; y < canopy.height(turn); y++) for (int x = 0; x < canopy.width(turn); x++)
                if (canopy.blocks(x, y, turn)) actual++;
            assertEquals(expected, actual);
        }
    }
}
