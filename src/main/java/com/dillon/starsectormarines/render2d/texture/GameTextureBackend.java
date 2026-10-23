package com.dillon.starsectormarines.render2d.texture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.io.IOException;

import static org.lwjgl.opengl.GL11.glDeleteTextures;

/**
 * {@link TextureLedger.Backend} over the game's texture store.
 *
 * <p>The game pads every texture up to power-of-two sides, and a sprite reports the image's
 * share of that as {@code getTextureWidth}; the padded size is what the card holds, so that is
 * what {@link #bytes} counts.
 */
final class GameTextureBackend implements TextureLedger.Backend {

    @Override
    public int residentId(String path) {
        SpriteAPI sprite = settings().getSprite(path);
        return sprite == null ? 0 : sprite.getTextureId();
    }

    @Override
    public int load(String path) throws IOException {
        settings().loadTexture(path);
        return residentId(path);
    }

    @Override
    public long bytes(String path) {
        SpriteAPI sprite = settings().getSprite(path);
        if (sprite == null || sprite.getTextureId() == 0) return 0L;
        return paddedBytes(sprite.getWidth(), sprite.getHeight(),
                sprite.getTextureWidth(), sprite.getTextureHeight());
    }

    /** Four bytes a texel over the padded texture an image of this size and share occupies. */
    static long paddedBytes(float width, float height, float shareW, float shareH) {
        long texW = shareW > 0f ? Math.round(width / shareW) : Math.round(width);
        long texH = shareH > 0f ? Math.round(height / shareH) : Math.round(height);
        return texW * texH * 4L;
    }

    @Override
    public void unload(String path, int textureId) {
        // unloadTexture forgets the path and never deletes the texture; the GL side is ours.
        if (textureId != 0) glDeleteTextures(textureId);
        settings().unloadTexture(path);
    }

    private static SettingsAPI settings() {
        return Global.getSettings();
    }
}
