package com.dillon.starsectormarines.ui.retained.headless;

import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;

/** Optional raster backend for retained canvas passes normally owned by the game host. */
@FunctionalInterface
public interface HeadlessHostPassRenderer {
    boolean draw(CanvasHostPass pass, CanvasContext context,
                 CanvasHostViewport viewport, float alphaMult);
}
