package com.dillon.starsectormarines.render;

/**
 * Something the renderer can draw inside the bridge FBO pass. Implementations own their
 * own GL resources (shaders, buffers, textures) and are responsible for lazy init on
 * first {@link #draw} call — there's no GL context until then.
 */
public interface Drawable {
    /**
     * @param viewProjection column-major 4x4: camera projection × view
     * @param model          column-major 4x4: this drawable's world transform
     */
    void draw(float[] viewProjection, float[] model);
}
