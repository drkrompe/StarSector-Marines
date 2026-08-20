package com.dillon.starsectormarines.battle.ui.highlight;

import java.awt.Color;

/**
 * One rectangular cell mark in a {@link HighlightOverlay}. Cell coordinates
 * index the world grid; the default constructor covers one cell, while coarse
 * debug fields can provide a larger width/height footprint. Color alpha scales
 * both the translucent fill and sharper outline.
 *
 * <p>Immutable value type — sources rebuild their highlight lists each frame,
 * so there's no need to mutate individual marks.
 */
public final class CellHighlight {

    public final int cellX;
    public final int cellY;
    public final int width;
    public final int height;
    public final Color color;

    public CellHighlight(int cellX, int cellY, Color color) {
        this(cellX, cellY, 1, 1, color);
    }

    public CellHighlight(int cellX, int cellY, int width, int height, Color color) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.color = color;
    }
}
