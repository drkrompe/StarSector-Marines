package com.dillon.starsectormarines.battle.world.gen.fit;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pose is the half of a placement the mask cannot carry, so its arithmetic
 * has to be right in both directions and for cells outside the footprint —
 * doors are authored on the bulkhead ring, which is exactly there.
 */
class RoomPoseTest {

    private static final int WIDTH = 40;
    private static final int HEIGHT = 16;

    @Test
    void everyPoseRoundTrips() {
        for (RoomPose pose : RoomPose.all()) {
            for (int x = -1; x <= WIDTH; x++) {
                for (int y = -1; y <= HEIGHT; y++) {
                    int[] posed = pose.map(x, y, WIDTH, HEIGHT);
                    int[] back = pose.unmap(posed[0], posed[1], WIDTH, HEIGHT);
                    assertEquals(x, back[0], pose + " lost x for " + x + "," + y);
                    assertEquals(y, back[1], pose + " lost y for " + x + "," + y);
                }
            }
        }
    }

    @Test
    void poseCarriesTheFootprintOntoItsOwnExtents() {
        for (RoomPose pose : RoomPose.all()) {
            int width = pose.posedWidth(WIDTH, HEIGHT);
            int height = pose.posedHeight(WIDTH, HEIGHT);
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    int[] posed = pose.map(x, y, WIDTH, HEIGHT);
                    assertTrue(posed[0] >= 0 && posed[0] < width
                                    && posed[1] >= 0 && posed[1] < height,
                            pose + " put " + x + "," + y + " outside the room");
                }
            }
        }
    }

    /**
     * The reason poses exist at all. A rectangle's mask is the same flipped, so
     * a placer choosing among masks can only ever offer four of these eight —
     * and the four it drops are the ones that put a bay's doors at the other
     * end.
     */
    @Test
    void flippingARectangleChangesThePoseAndNotTheMask() {
        RoomShape shape = RoomShape.rectangle(WIDTH, HEIGHT);
        Set<RoomShape> masks = new HashSet<>();
        Set<int[]> ignored = new HashSet<>();
        ignored.size();
        for (RoomPose pose : RoomPose.all()) {
            masks.add(shape.posed(pose));
        }
        assertEquals(2, masks.size(), "a rectangle has two masks and eight poses");
        assertEquals(8, RoomPose.all().size());

        RoomPose upright = new RoomPose(0, false);
        RoomPose flipped = new RoomPose(0, true);
        assertEquals(shape.posed(upright), shape.posed(flipped), "the masks agree");
        assertEquals(0, upright.map(0, 0, WIDTH, HEIGHT)[0]);
        assertEquals(WIDTH - 1, flipped.map(0, 0, WIDTH, HEIGHT)[0], "and the contents do not");
    }

    /** An asymmetric shape does distinguish its flips, and must not lose any. */
    @Test
    void anAsymmetricShapeYieldsEightDistinctMasks() {
        RoomShape shape = RoomShape.of(
                "####",
                "#...",
                "#...");
        Set<RoomShape> masks = new HashSet<>();
        for (RoomPose pose : RoomPose.all()) {
            masks.add(shape.posed(pose));
        }
        assertEquals(8, masks.size(), "an L has eight distinct ways to lie");
    }

    @Test
    void directionsTurnWithTheRoom() {
        RoomPose upright = new RoomPose(0, false);
        assertEquals(0, upright.mapDirection(0, 1)[0]);
        assertEquals(1, upright.mapDirection(0, 1)[1]);

        // A quarter turn takes "toward high y" to "toward low x", matching the
        // way the mask itself is turned.
        RoomPose turned = new RoomPose(1, false);
        assertEquals(-1, turned.mapDirection(0, 1)[0]);
        assertEquals(0, turned.mapDirection(0, 1)[1]);

        // Flipping reverses across, and leaves along alone.
        RoomPose flipped = new RoomPose(0, true);
        assertEquals(-1, flipped.mapDirection(1, 0)[0]);
        assertEquals(1, flipped.mapDirection(0, 1)[1]);
    }
}
