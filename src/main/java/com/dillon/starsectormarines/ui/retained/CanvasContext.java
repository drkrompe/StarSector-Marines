package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/** Backend-neutral drawing surface exposed to retained canvas producers. */
public abstract class CanvasContext {

    private final CanvasMetrics metrics;
    private final Rect visibleBounds;
    private final float alphaMult;

    protected CanvasContext(CanvasMetrics metrics, Rect visibleBounds, float alphaMult) {
        if (metrics == null || visibleBounds == null) {
            throw new IllegalArgumentException("metrics and visible bounds are required");
        }
        this.metrics = metrics;
        this.visibleBounds = visibleBounds;
        this.alphaMult = alphaMult;
    }

    public final CanvasMetrics metrics() {
        return metrics;
    }

    /** Visible portion of this paint in canvas-local coordinates. */
    public final Rect visibleBounds() {
        return visibleBounds;
    }

    public final float alphaMult() {
        return alphaMult;
    }

    /**
     * Runs a bounded renderer-owned pass when this canvas is painting to the
     * target host. Returns false when the target cannot interpret this pass so
     * the caller can emit ordinary canvas primitives as deterministic evidence.
     */
    public final boolean hostPass(CanvasHostPass pass) {
        if (pass == null) throw new IllegalArgumentException("host pass is required");
        return drawHostPass(pass);
    }

    public final void fillRect(float x, float y, float width, float height, Color color) {
        requireRect(x, y, width, height);
        drawFillRect(x, y, width, height, requireColor(color));
    }

    /** Fills one arbitrary quad in canvas-local coordinates. */
    public final void fillQuad(float x0, float y0, float x1, float y1,
                               float x2, float y2, float x3, float y3,
                               Color color) {
        requireFinite(x0, y0, x1, y1, x2, y2, x3, y3);
        drawFillQuad(x0, y0, x1, y1, x2, y2, x3, y3,
                requireColor(color));
    }

    public final void strokeRect(float x, float y, float width, float height,
                                 Color color, float strokeWidth) {
        requireRect(x, y, width, height);
        requirePositive(strokeWidth, "stroke width");
        line(x, y, x + width, y, color, strokeWidth);
        line(x + width, y, x + width, y + height, color, strokeWidth);
        line(x + width, y + height, x, y + height, color, strokeWidth);
        line(x, y + height, x, y, color, strokeWidth);
    }

    public final void line(float x1, float y1, float x2, float y2,
                           Color color, float strokeWidth) {
        requireFinite(x1, y1, x2, y2);
        requirePositive(strokeWidth, "stroke width");
        drawLine(x1, y1, x2, y2, requireColor(color), strokeWidth);
    }

    public final void text(BitmapFont font, String text, float x, float y, Color color) {
        if (font == null || text == null) throw new IllegalArgumentException("font and text required");
        requireFinite(x, y);
        drawText(font, text, x, y, requireColor(color));
    }

    /**
     * Draws one whole-texture asset. The path is the headless authority while
     * the optional live sprite is the Starsector rendering handle.
     */
    public final void sprite(String sourcePath, SpriteAPI liveSprite,
                             float centerX, float centerY, float width, float height,
                             float angleDegrees, Color tint) {
        sprite(sourcePath, liveSprite, centerX, centerY, width, height,
                angleDegrees, tint, CanvasSpriteRegion.FULL, CanvasBlend.NORMAL);
    }

    /**
     * Draws a normalized source region with explicit blend intent. The source
     * region uses top-left, Y-down image coordinates just like the canvas.
     */
    public final void sprite(String sourcePath, SpriteAPI liveSprite,
                             float centerX, float centerY, float width, float height,
                             float angleDegrees, Color tint, CanvasSpriteRegion region,
                             CanvasBlend blend) {
        if ((sourcePath == null || sourcePath.isBlank()) && liveSprite == null) {
            throw new IllegalArgumentException("sprite path or live sprite required");
        }
        requireFinite(centerX, centerY, width, height, angleDegrees);
        if (width < 0f || height < 0f) {
            throw new IllegalArgumentException("sprite extent cannot be negative");
        }
        if (region == null || blend == null) {
            throw new IllegalArgumentException("sprite region and blend are required");
        }
        drawSprite(sourcePath, liveSprite, centerX, centerY, width, height,
                angleDegrees, requireColor(tint), region, blend);
    }

    /** Compatibility overload for producers that do not yet retain an asset path. */
    public final void sprite(SpriteAPI liveSprite, float centerX, float centerY,
                             float width, float height, float angleDegrees, Color tint) {
        sprite(null, liveSprite, centerX, centerY, width, height, angleDegrees, tint);
    }

    protected abstract void drawFillRect(float x, float y, float width, float height,
                                         Color color);

    protected abstract void drawFillQuad(float x0, float y0, float x1, float y1,
                                         float x2, float y2, float x3, float y3,
                                         Color color);

    protected abstract void drawLine(float x1, float y1, float x2, float y2,
                                     Color color, float strokeWidth);

    protected abstract void drawText(BitmapFont font, String text, float x, float y,
                                     Color color);

    protected abstract void drawSprite(String sourcePath, SpriteAPI liveSprite,
                                       float centerX, float centerY, float width, float height,
                                       float angleDegrees, Color tint,
                                       CanvasSpriteRegion region, CanvasBlend blend);

    /** Default backend behavior: native renderer passes are unavailable. */
    protected boolean drawHostPass(CanvasHostPass pass) {
        return false;
    }

    /** Document scaling of a line's normal under anisotropic canvas stretching. */
    protected static float strokeScale(CanvasMetrics metrics, float deltaX, float deltaY) {
        float length = (float) Math.hypot(deltaX, deltaY);
        if (!(length > 0f)) {
            return (float) Math.sqrt(metrics.scaleX() * metrics.scaleY());
        }
        float normalX = -deltaY / length;
        float normalY = deltaX / length;
        return (float) Math.hypot(normalX * metrics.scaleX(),
                normalY * metrics.scaleY());
    }

    private static Color requireColor(Color color) {
        if (color == null) throw new IllegalArgumentException("color required");
        return color;
    }

    private static void requireRect(float x, float y, float width, float height) {
        requireFinite(x, y, width, height);
        if (width < 0f || height < 0f) {
            throw new IllegalArgumentException("rectangle extent cannot be negative");
        }
    }

    private static void requirePositive(float value, String label) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("canvas coordinates must be finite");
            }
        }
    }
}
