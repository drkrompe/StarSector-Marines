package com.dillon.starsectormarines.battle.ui.highlight;

import java.awt.Color;

/**
 * One cell mark in a {@link HighlightOverlay}. Cell coordinates index the
 * world grid; color is applied as a semi-transparent fill + a sharper outline
 * so a marked cell reads against any underlying terrain.
 *
 * <p>Immutable value type — sources rebuild their highlight lists each frame,
 * so there's no need to mutate individual marks.
 */
public final class CellHighlight {

    public final int cellX;
    public final int cellY;
    public final Color color;

    public CellHighlight(int cellX, int cellY, Color color) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.color = color;
    }
}
