package com.dillon.starsectormarines.ui.retained;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * One resolved image asset: its intrinsic pixel size plus the optional live
 * Starsector handle that draws it.
 *
 * <p>The intrinsic size is the authority for aspect-correct layout and is
 * required on every backend. The live sprite is the host rendering handle and
 * is deliberately absent on tooling backends, which draw from the asset path
 * instead.
 */
public record UiImage(SpriteAPI liveSprite, float width, float height) {

    public UiImage {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("Image intrinsic size must be finite and positive");
        }
    }
}
