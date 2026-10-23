package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.render2d.texture.TextureUsage;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
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

    /**
     * Where this canvas's content box sits on the render host, or null when this
     * backend runs no host passes.
     *
     * <p>A producer that draws canvas primitives which have to line up with what
     * a {@link #hostPass} draws needs this, because the two spaces genuinely
     * differ. A host pass is projected in host pixels with its Y axis running
     * <em>up</em>; canvas coordinates are surface units with Y running
     * <em>down</em>, and the surface is stretched onto the content box by a
     * factor that is not the same on both axes. Anything placed in one space and
     * drawn in the other has to convert, and the numbers to convert with are
     * exactly this rectangle's.
     *
     * <p>Answerable before any pass has run, so a backdrop can be positioned and
     * then painted over.
     */
    public CanvasHostViewport hostViewport() {
        return null;
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

    /**
     * Fills one quad with a colour at each corner, blended across it: the gradient primitive.
     * Corners are in the same order as {@link #fillQuad}. With {@code c0 == c1} and
     * {@code c2 == c3} it is a straight gradient from the {@code 0-1} edge to the {@code 3-2}
     * edge, which every target draws exactly.
     */
    public final void fillQuadGradient(float x0, float y0, float x1, float y1,
                                       float x2, float y2, float x3, float y3,
                                       Color c0, Color c1, Color c2, Color c3) {
        requireFinite(x0, y0, x1, y1, x2, y2, x3, y3);
        drawFillQuadGradient(x0, y0, x1, y1, x2, y2, x3, y3,
                requireColor(c0), requireColor(c1), requireColor(c2), requireColor(c3));
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

    /** Draws one immutable mesh without allocating transformed vertex arrays. */
    public final void mesh(CanvasMesh mesh, float x, float y, float scale, Color color) {
        if (mesh == null) throw new IllegalArgumentException("mesh required");
        requireFinite(x, y, scale);
        if (scale < 0f) throw new IllegalArgumentException("mesh scale cannot be negative");
        if (scale == 0f || mesh.vertexCount() == 0) return;
        drawMesh(mesh, x, y, scale, requireColor(color));
    }

    /** Draws cached meshes in paint order; targets may submit the entire batch once. */
    public final void meshBatch(CanvasMeshBatch batch, float x, float y, float scale, Color tint) {
        if (batch == null) throw new IllegalArgumentException("mesh batch required");
        requireFinite(x, y, scale);
        requireColor(tint);
        if (scale < 0f) throw new IllegalArgumentException("mesh scale cannot be negative");
        if (scale == 0f || batch.layerCount() == 0 || tint.getAlpha() == 0) return;
        drawMeshBatch(batch, x, y, scale, tint);
    }

    /** Aspect-fits a precompiled SVG asset into a canvas-local destination box. */
    public final void svg(SvgAsset asset, float x, float y, float width, float height, Color tint) {
        if (asset == null) throw new IllegalArgumentException("SVG asset required");
        requireRect(x, y, width, height);
        asset.draw(this, x, y, width, height, requireColor(tint));
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
        if (TextureUsage.isEnabled()) {
            TextureUsage.ui(sourcePath, liveSprite, width * metrics.scaleX(), height * metrics.scaleY(),
                    region.width(), region.height());
        }
        drawSprite(sourcePath, liveSprite, centerX, centerY, width, height,
                angleDegrees, requireColor(tint), region, blend);
    }

    /** Host adapters may draw a textured subcell quad; vertices are normalized, Y-up. */
    public void spritePatch(String path,float cx,float cy,float width,float height,
                            Color tint,CanvasSpriteRegion region,float[] vertices) {
        throw new UnsupportedOperationException("Textured patches require a supporting host adapter");
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

    /**
     * Per-corner colour fill. This default approximates it for targets that cannot blend:
     * thin bands from the {@code 0-1} edge to the {@code 3-2} edge, each in the colour at its
     * middle. Targets that can interpolate override it.
     */
    protected void drawFillQuadGradient(float x0, float y0, float x1, float y1,
                                        float x2, float y2, float x3, float y3,
                                        Color c0, Color c1, Color c2, Color c3) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            float ta = (float) i / bands, tb = (float) (i + 1) / bands, tm = (ta + tb) * .5f;
            Color left = lerp(c0, c3, tm), right = lerp(c1, c2, tm);
            drawFillQuad(x0 + (x3 - x0) * ta, y0 + (y3 - y0) * ta,
                    x1 + (x2 - x1) * ta, y1 + (y2 - y1) * ta,
                    x1 + (x2 - x1) * tb, y1 + (y2 - y1) * tb,
                    x0 + (x3 - x0) * tb, y0 + (y3 - y0) * tb,
                    lerp(left, right, .5f));
        }
    }

    /** Linear blend of two colours, alpha included. */
    protected static Color lerp(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    /** Targets may batch triangles to prevent seams between adjacent fill pieces. */
    protected void drawMesh(CanvasMesh mesh, float x, float y, float scale, Color color) {
        for (int i = 0; i < mesh.vertexCount(); i += 3) {
            float x0 = x + mesh.x(i) * scale;
            float y0 = y + mesh.y(i) * scale;
            float x1 = x + mesh.x(i + 1) * scale;
            float y1 = y + mesh.y(i + 1) * scale;
            float x2 = x + mesh.x(i + 2) * scale;
            float y2 = y + mesh.y(i + 2) * scale;
            drawFillQuad(x0, y0, x1, y1, x2, y2, x2, y2, color);
        }
    }

    /** Raster targets retain union-per-layer fills for antialiasing without mesh seams. */
    protected void drawMeshBatch(CanvasMeshBatch batch, float x, float y, float scale, Color tint) {
        for (int i = 0; i < batch.layerCount(); i++) {
            CanvasMeshBatch.Layer layer = batch.layer(i);
            Color color = layer.color();
            int alpha = color.getAlpha() * tint.getAlpha() / 255;
            if (alpha == 0 || layer.mesh().vertexCount() == 0) continue;
            Color tinted = new Color(color.getRed() * tint.getRed() / 255,
                    color.getGreen() * tint.getGreen() / 255,
                    color.getBlue() * tint.getBlue() / 255, alpha);
            drawMesh(layer.mesh(), x, y, scale, tinted);
        }
    }

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
