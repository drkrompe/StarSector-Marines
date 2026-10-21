package com.dillon.starsectormarines.battle.world.model;

import com.dillon.starsectormarines.battle.world.tiles.GridBlockDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rings a defense post is built from, and the split that separated one of
 * them from a ground surface.
 *
 * <p>{@code road.courtyard} was two things at once: the courtyard surface the
 * generator paves yards and runways with, and — through the same neighbour mask
 * — the outward-bowed cover ringing a WEDGE or TRAPEZOID turret. Redrawing a
 * courtyard as flatter paving would have quietly changed what a turret hides
 * behind, and nothing would have said so.
 *
 * <p>They are two blocks now, sharing their art. What the split buys is that
 * they are free to differ later, and this test will not mind when they do — it
 * compares what the ring resolves to against the block the ring is declared
 * from, not against a frozen picture.
 */
public class TurretRingBlocksTest {

    /** The eight cells around a turret; the centre is the turret itself. */
    private static List<int[]> ringOffsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int relY = 1; relY >= -1; relY--) {
            for (int relX = -1; relX <= 1; relX++) {
                if (relX == 0 && relY == 0) continue;
                offsets.add(new int[]{relX, relY});
            }
        }
        return offsets;
    }

    @Test
    void theBowOutRingComesFromItsOwnBlock() {
        GridBlockDef revetment = TileRegistry.installed().block("road.revetment");
        assertNotNull(revetment, "road.revetment should be a block of its own");

        for (int[] offset : ringOffsets()) {
            TileManifest.TileFrame frame = TileManifest.turretBowOut(offset[0], offset[1]);
            assertTrue(within(revetment, frame),
                    "the ring cell for " + offset[0] + "," + offset[1] + " should lie in "
                            + "road.revetment's patch, not somewhere else on the sheet");
        }
    }

    /** And the embankment ring still comes from its own, unchanged by the split. */
    @Test
    void theEmbankmentRingStillComesFromItsOwnBlock() {
        GridBlockDef embankment = TileRegistry.installed().block("road.embankment");
        assertNotNull(embankment);
        for (int[] offset : ringOffsets()) {
            assertTrue(within(embankment, TileManifest.turretEmbankment(offset[0], offset[1])),
                    "the embankment ring should lie in road.embankment's patch");
        }
    }

    private static boolean within(GridBlockDef block, TileManifest.TileFrame frame) {
        int span = block.layout == null ? 1 : block.layout.span();
        return frame.col >= block.originCol && frame.col < block.originCol + span
                && frame.row >= block.originRow && frame.row < block.originRow + span;
    }

}
