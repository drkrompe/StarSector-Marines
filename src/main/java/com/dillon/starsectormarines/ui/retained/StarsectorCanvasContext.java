package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Fixed-function Starsector implementation of the generic canvas surface. */
final class StarsectorCanvasContext extends CanvasContext {

    private final UiViewport viewport;

    StarsectorCanvasContext(CanvasMetrics metrics, Rect visibleBounds, UiViewport viewport,
                            float alphaMult) {
        super(metrics, visibleBounds, alphaMult);
        this.viewport = viewport;
    }

    @Override
    protected void drawFillRect(float x, float y, float width, float height, Color color) {
        StarsectorUiPaintTarget.fillGl(
                documentRect(x, y, width, height), viewport, color, alphaMult());
    }

    @Override
    protected void drawFillQuad(float x0, float y0, float x1, float y1,
                                float x2, float y2, float x3, float y3,
                                Color color) {
        CanvasMetrics metrics = metrics();
        StarsectorUiPaintTarget.fillQuadGl(
                metrics.toDocumentX(x0), metrics.toDocumentY(y0),
                metrics.toDocumentX(x1), metrics.toDocumentY(y1),
                metrics.toDocumentX(x2), metrics.toDocumentY(y2),
                metrics.toDocumentX(x3), metrics.toDocumentY(y3),
                viewport, color, alphaMult());
    }

    @Override
    protected void drawLine(float x1, float y1, float x2, float y2,
                            Color color, float strokeWidth) {
        CanvasMetrics metrics = metrics();
        StarsectorUiPaintTarget.lineGl(metrics.toDocumentX(x1), metrics.toDocumentY(y1),
                metrics.toDocumentX(x2), metrics.toDocumentY(y2), viewport,
                color, strokeWidth * strokeScale(metrics, x2 - x1, y2 - y1)
                        * metrics.devicePixelRatio(),
                alphaMult());
    }

    @Override
    protected void drawText(BitmapFont font, String text, float x, float y, Color color) {
        CanvasMetrics metrics = metrics();
        font.drawStringScaled(text, viewport.screenXFor(metrics.toDocumentX(x)),
                viewport.screenTopFor(metrics.toDocumentY(y)),
                metrics.scaleX() * viewport.documentScale(),
                metrics.scaleY() * viewport.documentScale(), color, alphaMult());
    }

    /**
     * {@inheritDoc}
     *
     * <p>A caller may hand over a live handle or only the asset path, and both
     * are legal — the path is the canvas API's authority and the handle is an
     * optimisation for producers that already hold one. This backend used to
     * accept only the handle and threw on the path, which made a producer that
     * drew from a path work perfectly in the snapshot suite and crash the
     * campaign render loop. Resolving the path here is what the two backends
     * agreeing actually requires.
     *
     * <p>An asset that will not resolve is skipped rather than thrown on. The
     * painter runs every frame inside the game's own render call, so a missing
     * texture that throws takes the screen down; one that is absent leaves a
     * hole in a picture. {@link UiSpriteCache} logs the failure once and
     * remembers it, so the hole is explained without costing a frame.
     */
    @Override
    protected void drawSprite(String sourcePath, SpriteAPI liveSprite,
                              float centerX, float centerY,
                              float width, float height, float angleDegrees, Color tint,
                              CanvasSpriteRegion region, CanvasBlend blend) {
        SpriteAPI sprite = liveSprite;
        if (sprite == null) {
            UiImage resolved = UiSpriteCache.shared().resolve(sourcePath);
            if (resolved == null) return;
            sprite = resolved.liveSprite();
            if (sprite == null) return;
        }
        CanvasMetrics metrics = metrics();
        float textureWidth = sprite.getTextureWidth();
        float textureHeight = sprite.getTextureHeight();
        // KNOWN DIVERGENCE, unresolved: this passes region.y() to setTexY
        // unchanged, while the headless backend resolves the same value as a
        // top-down image row — which is the convention CanvasSpriteRegion's
        // own contract states. Every ground-FX caller of setTexY in this repo
        // is written as though the axis runs bottom-up, so one of the two
        // backends mirrors any region that is not vertically symmetric.
        //
        // Nothing observable depends on it today: every live canvas region is
        // FULL except the mech-lab welding sparks, which cycle a whole sheet
        // and so only shift phase under a mirror. Settling it needs a look at
        // the running game, not more reading; until then do not author a
        // vertically asymmetric region for a canvas that draws through both.
        try {
            sprite.setTexX((region.x() + (region.flipX() ? region.width() : 0f))
                    * textureWidth);
            sprite.setTexY((region.y() + (region.flipY() ? region.height() : 0f))
                    * textureHeight);
            sprite.setTexWidth(region.width() * textureWidth * (region.flipX() ? -1f : 1f));
            sprite.setTexHeight(region.height() * textureHeight * (region.flipY() ? -1f : 1f));
            sprite.setSize(width * metrics.scaleX() * viewport.documentScale(),
                    height * metrics.scaleY() * viewport.documentScale());
            sprite.setAngle(angleDegrees);
            sprite.setAlphaMult(alphaMult() * tint.getAlpha() / 255f);
            sprite.setColor(tint.getRed() == 255 && tint.getGreen() == 255
                    && tint.getBlue() == 255
                    ? Color.WHITE
                    : new Color(tint.getRed(), tint.getGreen(), tint.getBlue()));
            if (blend == CanvasBlend.ADDITIVE) {
                sprite.setAdditiveBlend();
            } else {
                sprite.setNormalBlend();
            }
            sprite.renderAtCenter(
                    viewport.screenXFor(metrics.toDocumentX(centerX)),
                    viewport.screenTopFor(metrics.toDocumentY(centerY)));
        } finally {
            sprite.setTexX(0f);
            sprite.setTexY(0f);
            sprite.setTexWidth(textureWidth);
            sprite.setTexHeight(textureHeight);
            sprite.setAngle(0f);
            sprite.setAlphaMult(1f);
            sprite.setColor(Color.WHITE);
            sprite.setNormalBlend();
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    @Override
    public CanvasHostViewport hostViewport() {
        CanvasMetrics metrics = metrics();
        Rect content = metrics.contentBox();
        return new CanvasHostViewport(
                viewport.screenXFor(content.x()),
                viewport.screenBottomFor(content),
                content.width() * viewport.documentScale(),
                content.height() * viewport.documentScale(),
                metrics.surfaceWidth(), metrics.surfaceHeight());
    }

    @Override
    protected boolean drawHostPass(CanvasHostPass pass) {
        CanvasHostViewport hostViewport = hostViewport();
        try {
            pass.draw(hostViewport, alphaMult());
        } finally {
            // The pass owns its local state, but the retained painter still
            // reasserts the fixed-function canvas contract for following ops.
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        return true;
    }

    private Rect documentRect(float x, float y, float width, float height) {
        CanvasMetrics metrics = metrics();
        return new Rect(metrics.toDocumentX(x), metrics.toDocumentY(y),
                width * metrics.scaleX(), height * metrics.scaleY());
    }
}
