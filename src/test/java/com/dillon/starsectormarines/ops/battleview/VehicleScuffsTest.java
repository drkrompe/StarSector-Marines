package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.HullLocations.Plate;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The placement rule for worn plate ({@code combat-durability-nouns.md}), on a synthetic hull. */
class VehicleScuffsTest {

    /** A 192x96 frame holding a 160x64 hull, nose to the image's left as the vehicle sheets draw it. */
    private static final int W = 192;
    private static final int H = 96;

    private static BufferedImage hull() {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 16; y < 80; y++) {
            for (int x = 16; x < 176; x++) image.setRGB(x, y, 0xFF808080);
        }
        // A notch in the left side, so a mark must keep clear of an inside edge too.
        for (int y = 64; y < 80; y++) {
            for (int x = 80; x < 112; x++) image.setRGB(x, y, 0);
        }
        return image;
    }

    private static VehicleScuffs.Mask mask(BufferedImage image) {
        return VehicleScuffs.build(image, new SpriteSheetFrames.Frame(0, 0, W, H), -90f, true);
    }

    private static List<float[]> marks(VehicleScuffs.Mask mask, Plate plate, float wear) {
        List<float[]> out = new ArrayList<>();
        VehicleScuffs.place(mask, plate.ordinal(), wear, 1234L,
                (x, y, kind, variant, spin, alpha) -> out.add(new float[] {x, y, kind, spin}));
        return out;
    }

    @Test
    void wholePlateShowsNothingAndAStrippedOneShowsEverySlot() {
        VehicleScuffs.Mask mask = mask(hull());
        for (Plate plate : Plate.values()) {
            assertTrue(mask.count(plate.ordinal()) > 0, plate + " has room for marks");
            assertEquals(0, marks(mask, plate, 0f).size());
            assertEquals(VehicleScuffs.SLOTS, marks(mask, plate, 1f).size());
        }
    }

    @Test
    void aRegionOfFewSlotsStillRunsThroughEveryStage() {
        VehicleScuffs.Mask mask = mask(hull());
        List<Integer> kinds = new ArrayList<>();
        VehicleScuffs.place(mask, Plate.FRONT.ordinal(), 4, 1f, 99L,
                (x, y, kind, variant, spin, alpha) -> kinds.add(kind));
        assertEquals(List.of(VehicleScuffs.SCRAPE, VehicleScuffs.SCRAPE, VehicleScuffs.SCORCH, VehicleScuffs.GOUGE),
                kinds);
    }

    @Test
    void marksBuildUpInPlace() {
        VehicleScuffs.Mask mask = mask(hull());
        for (Plate plate : Plate.values()) {
            List<float[]> early = marks(mask, plate, 0.4f);
            List<float[]> late = marks(mask, plate, 0.8f);
            assertTrue(early.size() < late.size(), plate + " shows more as it wears");
            for (int i = 0; i < early.size(); i++) {
                assertEquals(early.get(i)[0], late.get(i)[0]);
                assertEquals(early.get(i)[1], late.get(i)[1]);
                assertEquals(early.get(i)[2], late.get(i)[2]);
                assertEquals(early.get(i)[3], late.get(i)[3]);
            }
        }
    }

    @Test
    void aTurnedMarkNeverLeavesTheSilhouette() {
        BufferedImage image = hull();
        VehicleScuffs.Mask mask = mask(image);
        float reach = mask.decalPx * VehicleScuffs.MARK_REACH;
        for (Plate plate : Plate.values()) {
            for (float[] m : marks(mask, plate, 1f)) {
                for (int y = (int) (m[1] - reach); y <= m[1] + reach; y++) {
                    for (int x = (int) (m[0] - reach); x <= m[0] + reach; x++) {
                        if (Math.hypot(x + 0.5 - m[0], y + 0.5 - m[1]) > reach) continue;
                        assertFalse((image.getRGB(x, y) >>> 24) < VehicleScuffs.OPAQUE_ALPHA,
                                "mark at " + m[0] + "," + m[1] + " reaches clear pixel " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void aMarkLiesOnItsOwnPlatesSide() {
        VehicleScuffs.Mask mask = mask(hull());
        // Nose to the image's left, so the image's top is the hull's right side.
        for (float[] m : marks(mask, Plate.FRONT, 1f)) assertTrue(m[0] < W / 2f);
        for (float[] m : marks(mask, Plate.REAR, 1f)) assertTrue(m[0] > W / 2f);
        for (float[] m : marks(mask, Plate.RIGHT, 1f)) assertTrue(m[1] < H / 2f);
        for (float[] m : marks(mask, Plate.LEFT, 1f)) assertTrue(m[1] > H / 2f);
    }
}
