package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.render2d.texture.ModTextures;
import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The live path's image resolver: one attempt per asset path, remembered.
 *
 * <p>Mod textures have to be loaded before they can be asked for their size,
 * so the load and the lookup travel together here. The outcome — sprite or
 * nothing — is cached because the painter runs every frame and a texture that
 * is missing on the first frame is still missing on the ten-thousandth; a
 * retry loop would spend a frame budget and a log file to learn that again.
 *
 * <p>While a screen that manages its textures is open — the operations dialog — what is first
 * loaded goes under that screen's {@linkplain #enter lease}, and {@link #leave} gives it back
 * and forgets those entries, so a later paint loads them afresh instead of drawing a texture
 * that is gone ({@code battle-textures.md}). Anything else stays on the session lease.
 */
public final class UiSpriteCache implements UiImageResolver {

    private static final UiSpriteCache SHARED = new UiSpriteCache();

    private final Map<String, UiImage> attempted = new HashMap<>();
    /** The lease each attempted path was loaded under. */
    private final Map<String, TextureLease> loadedUnder = new HashMap<>();
    /** Where a first load goes now; null for the session lease. */
    private TextureLease scope;

    /** The process-wide cache; Starsector textures outlive any one screen. */
    public static UiSpriteCache shared() {
        return SHARED;
    }

    @Override
    public UiImage resolve(String path) {
        if (path == null || path.isBlank()) return null;
        if (attempted.containsKey(path)) return attempted.get(path);
        TextureLease lease = scope != null ? scope : TextureLedger.shared().session();
        UiImage image = load(path, lease);
        attempted.put(path, image);
        loadedUnder.put(path, lease);
        return image;
    }

    /** Loads first-seen images under {@code lease} until {@link #leave} is called with it. */
    public void enter(TextureLease lease) {
        scope = lease;
    }

    /**
     * Releases {@code lease} and forgets every image it loaded. Images loaded under another lease
     * are kept, and a texture another lease also holds stays on the card.
     */
    public void leave(TextureLease lease) {
        if (lease == null) return;
        if (scope == lease) scope = null;
        loadedUnder.entrySet().removeIf(entry -> {
            if (!Objects.equals(entry.getValue(), lease)) return false;
            attempted.remove(entry.getKey());
            return true;
        });
        lease.release();
    }

    private static UiImage load(String path, TextureLease lease) {
        try {
            ModTextures.loadTexture(path, lease);
            SpriteAPI sprite = ModTextures.sprite(path);
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
