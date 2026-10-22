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
        float hullHeight = hullWidth * frameAspect;
        float cellPx = hullWidth / type.visualLengthCells;
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float[] tint = type.hullTint();
        Color hullColor = tint == null ? Color.WHITE : new Color(clamp(tint[0]), clamp(tint[1]), clamp(tint[2]));
        context.sprite(type.spritePath, null, centerX, centerY, hullWidth, hullHeight, 0f, hullColor,
                CanvasSpriteRegion.frame(type.frameCount, 1, type.spriteFrame), CanvasBlend.NORMAL);
        if (type.turretFrame >= 0 && !type.turretArtFromMount()) {
            float turretWidth = type.turretVisualCells * cellPx;
            // The sheet's nose points left, so the chassis' forward (+Y) runs to -X on screen.
            float turretX = centerX - type.turretMountY * cellPx;
            float turretY = centerY - type.turretMountX * cellPx;
            context.sprite(type.spritePath, null, turretX, turretY, turretWidth, turretWidth * frameAspect, 0f,
                    Color.WHITE, CanvasSpriteRegion.frame(type.frameCount, 1, type.turretFrame), CanvasBlend.NORMAL);
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
