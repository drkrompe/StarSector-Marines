package com.dillon.starsectormarines.ui.retained;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * The live path's image resolver: one attempt per asset path, remembered.
 *
 * <p>Mod textures have to be loaded before they can be asked for their size,
 * so the load and the lookup travel together here. The outcome — sprite or
 * nothing — is cached because the painter runs every frame and a texture that
 * is missing on the first frame is still missing on the ten-thousandth; a
 * retry loop would spend a frame budget and a log file to learn that again.
 */
public final class UiSpriteCache implements UiImageResolver {

    private static final UiSpriteCache SHARED = new UiSpriteCache();

    private final Map<String, UiImage> attempted = new HashMap<>();

    /** The process-wide cache; Starsector textures outlive any one screen. */
    public static UiSpriteCache shared() {
        return SHARED;
    }

    @Override
    public UiImage resolve(String path) {
        if (path == null || path.isBlank()) return null;
        if (attempted.containsKey(path)) return attempted.get(path);
        UiImage image = load(path);
        attempted.put(path, image);
        return image;
    }

    private static UiImage load(String path) {
        try {
            Global.getSettings().loadTexture(path);
            SpriteAPI sprite = Global.getSettings().getSprite(path);
            // Said once, because the outcome is cached. A caller that draws
            // nothing and says nothing is the failure mode this whole class
            // exists downstream of: a hull backdrop that simply did not appear.
            if (sprite == null) {
                Global.getLogger(UiSpriteCache.class)
                        .warn("Retained UI image not found: " + path);
                return null;
            }
            float width = sprite.getWidth();
            float height = sprite.getHeight();
            if (!(width > 0f) || !(height > 0f)) {
                Global.getLogger(UiSpriteCache.class)
                        .warn("Retained UI image has no extent: " + path);
                return null;
            }
            return new UiImage(sprite, width, height);
        } catch (Exception failure) {
            Global.getLogger(UiSpriteCache.class)
                    .warn("Retained UI image failed to load: " + path);
            return null;
        }
    }
}
