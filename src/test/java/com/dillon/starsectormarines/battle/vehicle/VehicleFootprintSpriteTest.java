package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hull's footprint is its painted hull: what the simulation collides, routes, crushes and is struck by lies within
 * the hull the sprite draws, and takes in most of it. It may fall short where a blade or wheels jut past the body,
 * never run past the paint. The sprite is drawn at {@link VehicleType#drawnLengthCells}, not at the footprint, so the
 * two can drift apart unseen; a tank once kept a 3.9-cell footprint round a 2.2-cell hull, and the swarm clawed at the
 * air beside it.
 */
class VehicleFootprintSpriteTest {

    /** How far a footprint may run past the painted hull (soft edges), and how far short of it (blade, wheels). */
    static final float WIDEST = 1.02f, NARROWEST = .75f;

    @Test
    void everyFootprintLiesWithinThePaintedHull() throws IOException {
        for (VehicleType type : VehicleType.values()) {
            Path sheet = Path.of("mod", type.spritePath);
            if (!Files.isRegularFile(sheet)) continue;
            float[] painted = paintedCells(ImageIO.read(sheet.toFile()), type);
            check(type, "long", type.visualLengthCells, painted[0]);
            check(type, "wide", type.visualWidthCells, painted[1]);
        }
    }

    /**
     * A hull with a nose inset ({@link VehicleType#noseInsetCells}) has its painted nose overhang the footprint's front
     * edge by that much: the frames point their noses left, and the hull is drawn centred on its footprint.
     */
    @Test
    void anInsetNoseOverhangsTheFootprintByItsInset() throws IOException {
        for (VehicleType type : VehicleType.values()) {
            Path sheet = Path.of("mod", type.spritePath);
            if (type.noseInsetCells() <= 0f || !Files.isRegularFile(sheet)) continue;
            float overhang = paintedNoseAheadCells(ImageIO.read(sheet.toFile()), type) - type.visualLengthCells / 2f;
            assertTrue(Math.abs(overhang - type.noseInsetCells()) <= NOSE_TOLERANCE, String.format(
                    "%s: nose overhangs its footprint %.2f cells, inset %.2f", type, overhang, type.noseInsetCells()));
        }
    }

    /** How near the overhang must come to the inset, cells: under a hundredth of the hull's length. */
    static final float NOSE_TOLERANCE = .03f;

    /** How far ahead of the frame's centre the live frame's paint runs, cells; the frames point their noses left. */
    static float paintedNoseAheadCells(BufferedImage sheet, VehicleType type) {
        int frameWidth = sheet.getWidth() / type.frameCount, x0 = type.spriteFrame * frameWidth;
        for (int x = x0; x < x0 + frameWidth; x++) {
            for (int y = 0; y < sheet.getHeight(); y++) {
                if ((sheet.getRGB(x, y) >>> 24) > 32) return (frameWidth / 2f - (x - x0)) * type.drawnLengthCells / frameWidth;
            }
        }
        return Float.NaN;
    }

    private static void check(VehicleType type, String axis, float footprint, float painted) {
        float ratio = footprint / painted;
        assertTrue(ratio >= NARROWEST && ratio <= WIDEST, String.format(
                "%s: footprint %.2f cells %s round a hull painted %.2f %s", type, footprint, axis, painted, axis));
    }

    /** The opaque extent of the hull's live frame, {long, wide}, in cells at the scale the hull is drawn. */
    static float[] paintedCells(BufferedImage sheet, VehicleType type) {
        int frameWidth = sheet.getWidth() / type.frameCount, x0 = type.spriteFrame * frameWidth;
        int left = Integer.MAX_VALUE, right = -1, top = Integer.MAX_VALUE, bottom = -1;
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = x0; x < x0 + frameWidth; x++) {
                if ((sheet.getRGB(x, y) >>> 24) <= 32) continue;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = y;
            }
        }
        // The frame's long side is drawn drawnLengthCells long; its short side, across the hull, at the same scale.
        float cellsPerPixel = type.drawnLengthCells / frameWidth;
        return bottom < 0 ? new float[]{Float.NaN, Float.NaN}
                : new float[]{(right - left + 1) * cellsPerPixel, (bottom - top + 1) * cellsPerPixel};
    }
}
