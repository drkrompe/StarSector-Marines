package com.dillon.starsectormarines.ui.retained;

/**
 * Turns an asset path written in markup into something the painter can draw.
 *
 * <p>A resolver answers {@code null} for anything it cannot load, and the
 * painter then draws nothing at all. A missing icon is a gap in the art, not a
 * reason to fail a screen or to stamp a placeholder box where a player would
 * read it as a real symbol.
 */
@FunctionalInterface
public interface UiImageResolver {

    /** The resolved image, or null when this path cannot be loaded. */
    UiImage resolve(String path);
}
