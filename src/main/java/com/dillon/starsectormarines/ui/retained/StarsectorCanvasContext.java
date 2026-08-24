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
    protected void drawLine(float x1, float y1, float x2, float y2,
                            Color color, float strokeWidth) {
        CanvasMetrics metrics = metrics();
        StarsectorUiPaintTarget.lineGl(metrics.toDocumentX(x1), metrics.toDocumentY(y1),
                metrics.toDocumentX(x2), metrics.toDocumentY(y2), viewport,
                color, strokeWidth * strokeScale(metrics, x2 - x1, y2 - y1),
                alphaMult());
    }

    @Override
    protected void drawText(BitmapFont font, String text, float x, float y, Color color) {
        CanvasMetrics metrics = metrics();
        font.drawStringScaled(text, viewport.screenXFor(metrics.toDocumentX(x)),
                viewport.screenTopFor(metrics.toDocumentY(y)), metrics.scaleX(),
                metrics.scaleY(), color, alphaMult());
    }

    @Override
    protected void drawSprite(String sourcePath, SpriteAPI sprite, float centerX, float centerY,
                              float width, float height, float angleDegrees, Color tint) {
        if (sprite == null) {
            throw new IllegalArgumentException("Starsector canvas requires a live sprite handle");
        }
        CanvasMetrics metrics = metrics();
        try {
            sprite.setSize(width * metrics.scaleX(), height * metrics.scaleY());
            sprite.setAngle(angleDegrees);
            sprite.setAlphaMult(alphaMult() * tint.getAlpha() / 255f);
            sprite.setColor(tint.getRed() == 255 && tint.getGreen() == 255
                    && tint.getBlue() == 255
                    ? Color.WHITE
                    : new Color(tint.getRed(), tint.getGreen(), tint.getBlue()));
            sprite.setNormalBlend();
            sprite.renderAtCenter(
                    viewport.screenXFor(metrics.toDocumentX(centerX)),
                    viewport.screenTopFor(metrics.toDocumentY(centerY)));
        } finally {
            sprite.setAngle(0f);
            sprite.setAlphaMult(1f);
            sprite.setColor(Color.WHITE);
            glUseProgram(0);
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private Rect documentRect(float x, float y, float width, float height) {
        CanvasMetrics metrics = metrics();
        return new Rect(metrics.toDocumentX(x), metrics.toDocumentY(y),
                width * metrics.scaleX(), height * metrics.scaleY());
    }
}
