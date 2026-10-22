package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.UiImage;
import com.dillon.starsectormarines.ui.retained.UiSpriteCache;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.io.File;
import java.util.Iterator;

/**
 * Static thumbnail of one vehicle hull and its turret, nose to the left as the sheet draws it,
 * on the same dark pad the chassis previews use so a lance reads as one row of machines.
 */
public final class VehiclePreviewCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x12, 0x1B);
    private static final Color PAD = new Color(0x0D, 0x20, 0x2C);
    /** A sheet whose size cannot be read draws its frames at the APC sheets' proportion. */
    private static final float DEFAULT_FRAME_ASPECT = 0.75f;
    /** The canvas turn that brings the sheet's left-pointing nose to the top of the screen. */
    private static final float NOSE_UP_DEGREES = 90f;

    private final VehicleType type;
    private final float frameAspect;

    public VehiclePreviewCanvas(VehicleType type) {
        if (type == null) throw new IllegalArgumentException("vehicle type required");
        this.type = type;
        this.frameAspect = frameAspect(type);
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        float inset = Math.max(3f, Math.min(width, height) * 0.055f);
        context.fillRect(0f, 0f, width, height, BACKGROUND);
        context.fillRect(inset, inset, width - inset * 2f, height - inset * 2f, PAD);

        float hullWidth = Math.min((width - inset * 2f) * 0.92f, (height - inset * 2f) * 0.92f / frameAspect);
        drawAt(context, width * 0.5f, height * 0.5f, hullWidth / type.visualLengthCells, false, 1f);
    }

    /**
     * Draws the hull and turret at a canvas point. The sheet's nose points left; {@code noseUp}
     * turns the whole vehicle a quarter so it stands on a pad the way the mechs do.
     */
    void drawAt(CanvasContext context, float centerX, float centerY, float cellPx, boolean noseUp, float alpha) {
        float hullWidth = type.visualLengthCells * cellPx;
        float hullHeight = hullWidth * frameAspect;
        float angle = noseUp ? NOSE_UP_DEGREES : 0f;
        float[] tint = type.hullTint();
        Color hullColor = tint == null ? new Color(1f, 1f, 1f, alpha)
                : new Color(clamp(tint[0]), clamp(tint[1]), clamp(tint[2]), alpha);
        context.sprite(type.spritePath, null, centerX, centerY, hullWidth, hullHeight, angle, hullColor,
                CanvasSpriteRegion.frame(type.frameCount, 1, type.spriteFrame), CanvasBlend.NORMAL);
        if (type.turretFrame >= 0 && !type.turretArtFromMount()) {
            float turretWidth = type.turretVisualCells * cellPx;
            // The chassis' forward (+Y) runs along the nose: left on the sheet, up when turned.
            float turretX = noseUp ? centerX + type.turretMountX * cellPx : centerX - type.turretMountY * cellPx;
            float turretY = noseUp ? centerY - type.turretMountY * cellPx : centerY - type.turretMountX * cellPx;
            context.sprite(type.spritePath, null, turretX, turretY, turretWidth, turretWidth * frameAspect, angle,
                    new Color(1f, 1f, 1f, alpha), CanvasSpriteRegion.frame(type.frameCount, 1, type.turretFrame),
                    CanvasBlend.NORMAL);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** One frame's height over its width: the live sprite's size, else the PNG header off disk. */
    private static float frameAspect(VehicleType type) {
        try {
            UiImage image = UiSpriteCache.shared().resolve(type.spritePath);
            if (image != null && image.width() > 0f) return image.height() / (image.width() / type.frameCount);
        } catch (RuntimeException | LinkageError ignored) {
            // No Starsector runtime: read the sheet's header instead.
        }
        File sheet = new File("mod", type.spritePath);
        if (!sheet.isFile()) return DEFAULT_FRAME_ASPECT;
        try (ImageInputStream input = ImageIO.createImageInputStream(sheet)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return DEFAULT_FRAME_ASPECT;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return (float) reader.getHeight(0) / (reader.getWidth(0) / (float) type.frameCount);
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return DEFAULT_FRAME_ASPECT;
        }
    }
}
