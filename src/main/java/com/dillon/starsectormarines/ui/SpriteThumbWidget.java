package com.dillon.starsectormarines.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a texture (a ship's combat sprite, a planet, anything) scaled to fit
 * its rect, aspect-preserved and centered — the same trick the vanilla
 * pre-battle ship selector uses ({@code ShipHullSpecAPI.getSpriteName()} →
 * {@code getSprite(path)} → scale down). Non-interactive: {@link #contains}
 * returns {@code false} so it sits inside a clickable row without stealing the
 * row's input.
 *
 * <p>Two {@link SpriteAPI} hazards are handled here:
 * <ul>
 *   <li><b>Mutable singleton.</b> {@code getSprite} returns a shared instance
 *       whose {@code setSize}/{@code setTex*}/{@code setAngle} persist. We reset
 *       the tex region + angle + color each render, and we never read
 *       {@code getWidth()} after a {@code setSize} (which would compound-shrink
 *       the image frame over frame) — the natural dimensions are read once and
 *       cached per path in {@link #NATURAL_DIMS}.</li>
 *   <li><b>Lazy load.</b> An unloaded texture reports zero dims; we call
 *       {@code loadTexture} once and paint on the next frame
 *       ([[sprite_lazy_load]]).</li>
 * </ul>
 */
public final class SpriteThumbWidget extends BaseWidget {

    private static final Logger LOG = Global.getLogger(SpriteThumbWidget.class);

    /** path → {naturalWidth, naturalHeight}, captured once before any setSize pollutes getWidth(). */
    private static final Map<String, float[]> NATURAL_DIMS = new HashMap<>();

    private final String spritePath;
    private boolean triedLoad;

    public SpriteThumbWidget(String spritePath, float x, float y, float w, float h) {
        this.spritePath = spritePath;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public boolean contains(int px, int py) {
        return false; // decorative — clicks fall through to the row's toggle
    }

    @Override
    public void render(float alphaMult) {
        if (spritePath == null || spritePath.isEmpty() || w <= 0f || h <= 0f) return;
        try {
            SpriteAPI sprite = Global.getSettings().getSprite(spritePath);
            if (sprite == null) return;

            float[] dims = naturalDims(sprite);
            if (dims == null) {
                if (!triedLoad) {
                    triedLoad = true;
                    try {
                        Global.getSettings().loadTexture(spritePath);
                    } catch (Exception e) {
                        LOG.warn("SpriteThumbWidget: loadTexture failed for " + spritePath, e);
                    }
                }
                return; // dims show up next frame
            }

            float scale = Math.min(w / dims[0], h / dims[1]);
            // Singleton hygiene — reset region/angle/tint another renderer may have left set.
            sprite.setTexX(0f);
            sprite.setTexY(0f);
            sprite.setTexWidth(sprite.getTextureWidth());
            sprite.setTexHeight(sprite.getTextureHeight());
            sprite.setAngle(0f);
            sprite.setColor(Color.WHITE);
            sprite.setSize(dims[0] * scale, dims[1] * scale);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.renderAtCenter(x + w / 2f, y + h / 2f);
        } catch (Exception e) {
            LOG.warn("SpriteThumbWidget: render failed for " + spritePath, e);
        }
    }

    /**
     * Natural (pre-scale) pixel dimensions for the path, cached on first read.
     * Reading {@code getWidth()/getHeight()} after a {@code setSize} returns the
     * scaled size, so we capture once — before this widget (or a sibling sharing
     * the path this frame) mutates the singleton — and reuse forever.
     *
     * @return {w, h}, or {@code null} if the texture isn't loaded yet.
     */
    private float[] naturalDims(SpriteAPI sprite) {
        float[] cached = NATURAL_DIMS.get(spritePath);
        if (cached != null) return cached;
        float w = sprite.getWidth();
        float h = sprite.getHeight();
        if (w <= 0f || h <= 0f) return null;
        float[] dims = { w, h };
        NATURAL_DIMS.put(spritePath, dims);
        return dims;
    }
}
