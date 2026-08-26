package com.dillon.starsectormarines.ui.retained;

/**
 * Optional live-host drawing pass inside a retained canvas clip.
 *
 * <p>This is the bounded escape hatch for an existing renderer that owns a
 * native draw lifecycle. A headless backend may recognize a typed pass and
 * provide an equivalent drain; unknown passes are declined so the canvas
 * producer can still provide deterministic primitive evidence.</p>
 */
@FunctionalInterface
public interface CanvasHostPass {
    void draw(CanvasHostViewport viewport, float alphaMult);
}
