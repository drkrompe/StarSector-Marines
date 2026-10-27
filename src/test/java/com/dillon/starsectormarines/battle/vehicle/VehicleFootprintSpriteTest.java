package com.dillon.starsectormarines.battle.vehicle;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hull's footprint is its sprite's: what the simulation collides, routes, crushes and is struck by is as wide as the
 * hull it draws. The sprite's drawn width comes from its frame's aspect, not from {@link VehicleType#visualWidthCells},
 * so the two can drift apart unseen; a tank once kept a 3.9-cell footprint round a 2.2-cell hull, and the swarm clawed
 * at the air beside it.
 */
class VehicleFootprintSpriteTest {

    /** How far a footprint may run past the hull's opaque pixels (wheels, fenders, soft edges), and fall short. */
    static final float WIDEST = 1.3f, NARROWEST = .9f;

    @Test
    void everyFootprintIsAsWideAsTheHullItDraws() throws IOException {
        for (VehicleType type : VehicleType.values()) {
            Path sheet = Path.of("mod", type.spritePath);
            if (!Files.isRegularFile(sheet)) continue;
            float drawn = drawnWidthCells(ImageIO.read(sheet.toFile()), type);
            float ratio = type.visualWidthCells / drawn;
            assertTrue(ratio >= NARROWEST && ratio <= WIDEST, String.format(
                    "%s: footprint %.2f cells wide round a hull drawn %.2f wide", type, type.visualWidthCells, drawn));
        }
    }

    /** The opaque extent across the hull's live frame, in cells at the scale the hull is drawn. */
    static float drawnWidthCells(BufferedImage sheet, VehicleType type) {
        int frameWidth = sheet.getWidth() / type.frameCount, x0 = type.spriteFrame * frameWidth;
        int top = Integer.MAX_VALUE, bottom = -1;
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = x0; x < x0 + frameWidth; x++) {
                if ((sheet.getRGB(x, y) >>> 24) > 32) {
                    top = Math.min(top, y);
                    bottom = y;
                    break;
                }
            }
        }
        // The frame's long side is drawn visualLengthCells long; its short side, across the hull, at the same scale.
        return bottom < 0 ? Float.NaN : (bottom - top + 1) * type.visualLengthCells / frameWidth;
    }
}
