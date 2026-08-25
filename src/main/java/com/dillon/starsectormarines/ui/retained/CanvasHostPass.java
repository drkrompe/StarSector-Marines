package com.dillon.starsectormarines.ui.retained;

/**
 * Optional live-host drawing pass inside a retained canvas clip.
 *
 * <p>This is the bounded escape hatch for an existing renderer that already
 * owns its complete GL lifecycle. Headless targets decline the pass so the
 * canvas producer can provide deterministic primitive evidence instead.</p>
 */
@FunctionalInterface
public interface CanvasHostPass {
    void draw(CanvasHostViewport viewport, float alphaMult);
}
