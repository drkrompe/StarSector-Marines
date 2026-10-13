package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cells that disclose a fort from outside are its works: the wall, the gates and the bastion nests. They must be
 * the same cells at every turn the fort can be placed in, and never the inner buildings or the centrepiece, which are
 * what the wall hides.
 */
class FortExteriorCellsTest {

    private static Set<Long> packed(int[] cells) {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i + 1 < cells.length; i += 2) out.add(((long) cells[i] << 32) | (cells[i + 1] & 0xffffffffL));
        return out;
    }

    @Test
    void theWorksAreTheSameSetAtEveryTurnAndLeaveTheInsideOut() {
        for (FortKind kind : FortKind.values()) {
            FortPlan plan = FortPlan.of(kind);
            int baseline = -1;
            for (int turn = 0; turn < 4; turn++) {
                FortFrame frame = new FortFrame(plan, 100, 100, turn);
                int[] cells = frame.exteriorCells();
                Set<Long> set = packed(cells);
                assertEquals(0, cells.length % 2, kind + " cells come in pairs");
                assertTrue(set.size() > 250, kind + " turn " + turn + ": a real trace, not a token few cells");
                if (baseline < 0) baseline = set.size();
                assertEquals(baseline, set.size(), kind + " turn " + turn + " is the same fort turned");

                int[] centre = plan.centrepiece();
                int[] platform = frame.cell(centre[0], centre[1]);
                assertFalse(set.contains(((long) platform[0] << 32) | platform[1]),
                        kind + ": the centrepiece is inside the wall and is not part of what shows");
                for (int[] gate : plan.gateCells()) {
                    int[] at = frame.cell(gate[0], gate[1]);
                    assertTrue(set.contains(((long) at[0] << 32) | at[1]), kind + " gate cell shows");
                }
                for (FortAspect aspect : plan.aspects()) {
                    int[] core = plan.coreCentre(aspect);
                    int[] at = frame.cell(core[0], core[1]);
                    assertFalse(set.contains(((long) at[0] << 32) | at[1]), kind + " " + aspect + " core is inside");
                }
            }
        }
    }
}
