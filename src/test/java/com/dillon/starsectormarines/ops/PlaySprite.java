package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTranslatef;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * A sprite that really draws, for {@code playBattle}. The headless stand-ins answer sizes and draw nothing, because
 * their frames are drained another way; a played battle's HUD draws sprites straight to the screen.
 *
 * <p>Holds the texture it was made with, whatever later happens to the path, and reports the image's share of a padded
 * texture from {@link #getTextureWidth}, both as the game's sprites do ({@code PlayBattleSettings}).
 */
final class PlaySprite implements SpriteAPI {

    private final int texture;
    private final float textureWidth;
    private final float textureHeight;
    private float width;
    private float height;
    private float texX;
    private float texY;
    private float texWidth;
    private float texHeight;
    /** Negative until set: an unset centre follows the size. */
    private float centerX = -1f;
    private float centerY = -1f;
    private float angle;
    private float alphaMult = 1f;
    private Color color = Color.WHITE;
    private int blendSrc = GL_SRC_ALPHA;
    private int blendDest = GL_ONE_MINUS_SRC_ALPHA;

    /** @param textureWidth the image's share of its padded texture across, 0 to 1 */
    PlaySprite(int texture, float width, float height, float textureWidth, float textureHeight) {
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        texWidth = textureWidth;
        texHeight = textureHeight;
    }

    private void prepare(boolean bind) {
        if (bind) glBindTexture(GL_TEXTURE_2D, texture);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(blendSrc, blendDest);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * alphaMult);
    }

    private void quad(boolean bind, float pivotX, float pivotY, float u0, float v0, float u1, float v1, float w, float h) {
        prepare(bind);
        float cx = getCenterX();
        float cy = getCenterY();
        glPushMatrix();
        glTranslatef(pivotX, pivotY, 0f);
        if (angle != 0f) glRotatef(angle, 0f, 0f, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0);
        glVertex2f(-cx, -cy);
        glTexCoord2f(u1, v0);
        glVertex2f(w - cx, -cy);
        glTexCoord2f(u1, v1);
        glVertex2f(w - cx, h - cy);
        glTexCoord2f(u0, v1);
        glVertex2f(-cx, h - cy);
        glEnd();
        glPopMatrix();
    }

    private void draw(boolean bind, float pivotX, float pivotY) {
        quad(bind, pivotX, pivotY, texX, texY, texX + texWidth, texY + texHeight, width, height);
    }

    @Override public void render(float x, float y) { draw(true, x + getCenterX(), y + getCenterY()); }
    @Override public void renderNoBind(float x, float y) { draw(false, x + getCenterX(), y + getCenterY()); }
    @Override public void renderAtCenter(float x, float y) { draw(true, x, y); }
    @Override public void renderAtCenterNoBind(float x, float y) { draw(false, x, y); }

    @Override public void renderRegion(float x, float y, float tx, float ty, float tw, float th) {
        quad(true, x + getCenterX(), y + getCenterY(), tx * textureWidth, ty * textureHeight,
                (tx + tw) * textureWidth, (ty + th) * textureHeight, width * tw, height * th);
    }

    @Override public void renderRegionAtCenter(float x, float y, float tx, float ty, float tw, float th) {
        quad(true, x, y, tx * textureWidth, ty * textureHeight,
                (tx + tw) * textureWidth, (ty + th) * textureHeight, width * tw, height * th);
    }

    @Override public void renderWithCorners(float blX, float blY, float tlX, float tlY,
                                            float trX, float trY, float brX, float brY) {
        prepare(true);
        glBegin(GL_QUADS);
        glTexCoord2f(texX, texY);
        glVertex2f(blX, blY);
        glTexCoord2f(texX, texY + texHeight);
        glVertex2f(tlX, tlY);
        glTexCoord2f(texX + texWidth, texY + texHeight);
        glVertex2f(trX, trY);
        glTexCoord2f(texX + texWidth, texY);
        glVertex2f(brX, brY);
        glEnd();
    }

    @Override public void bindTexture() { glBindTexture(GL_TEXTURE_2D, texture); }
    @Override public int getTextureId() { return texture; }
    @Override public float getTextureWidth() { return textureWidth; }
    @Override public float getTextureHeight() { return textureHeight; }

    @Override public void setBlendFunc(int src, int dest) { blendSrc = src; blendDest = dest; }
    @Override public void setNormalBlend() { setBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); }
    @Override public void setAdditiveBlend() { setBlendFunc(GL_SRC_ALPHA, GL_ONE); }
    @Override public int getBlendSrc() { return blendSrc; }
    @Override public int getBlendDest() { return blendDest; }

    @Override public void setSize(float width, float height) { this.width = width; this.height = height; }
    @Override public void setWidth(float width) { this.width = width; }
    @Override public void setHeight(float height) { this.height = height; }
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }

    @Override public void setCenter(float x, float y) { centerX = x; centerY = y; }
    @Override public void setCenterX(float cx) { centerX = cx; }
    @Override public void setCenterY(float cy) { centerY = cy; }
    @Override public float getCenterX() { return centerX < 0f ? width / 2f : centerX; }
    @Override public float getCenterY() { return centerY < 0f ? height / 2f : centerY; }

    @Override public float getAngle() { return angle; }
    @Override public void setAngle(float angle) { this.angle = angle; }
    @Override public Color getColor() { return color; }
    @Override public void setColor(Color color) { this.color = color; }
    @Override public float getAlphaMult() { return alphaMult; }
    @Override public void setAlphaMult(float alphaMult) { this.alphaMult = alphaMult; }
    @Override public Color getAverageColor() { return Color.WHITE; }
    @Override public Color getAverageBrightColor() { return Color.WHITE; }

    @Override public void setTexX(float texX) { this.texX = texX; }
    @Override public void setTexY(float texY) { this.texY = texY; }
    @Override public void setTexWidth(float texWidth) { this.texWidth = texWidth; }
    @Override public void setTexHeight(float texHeight) { this.texHeight = texHeight; }
    @Override public float getTexX() { return texX; }
    @Override public float getTexY() { return texY; }
    @Override public float getTexWidth() { return texWidth; }
    @Override public float getTexHeight() { return texHeight; }
}
