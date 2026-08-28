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

    @Override
    protected void drawSprite(String sourcePath, SpriteAPI sprite, float centerX, float centerY,
                              float width, float height, float angleDegrees, Color tint,
                              CanvasSpriteRegion region, CanvasBlend blend) {
        if (sprite == null) {
            throw new IllegalArgumentException("Starsector canvas requires a live sprite handle");
        }
        CanvasMetrics metrics = metrics();
        float textureWidth = sprite.getTextureWidth();
        float textureHeight = sprite.getTextureHeight();
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
    protected boolean drawHostPass(CanvasHostPass pass) {
        CanvasMetrics metrics = metrics();
        Rect content = metrics.contentBox();
        CanvasHostViewport hostViewport = new CanvasHostViewport(
                viewport.screenXFor(content.x()),
                viewport.screenBottomFor(content),
                content.width() * viewport.documentScale(),
                content.height() * viewport.documentScale(),
                metrics.surfaceWidth(), metrics.surfaceHeight());
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
