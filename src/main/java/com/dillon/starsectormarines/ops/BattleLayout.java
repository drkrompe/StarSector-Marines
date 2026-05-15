package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data layout for the battle screen. Fits the {@code gridCellsW × gridCellsH}
 * cell grid into the dialog rect, centered, with a top control strip (speed
 * buttons) and a bottom-left Back button. {@link #cellSize} is the pixel size
 * of one cell — same for X and Y so cells stay square regardless of dialog
 * aspect ratio.
 */
public final class BattleLayout {

    public static final float PAD          = 12f;
    public static final float CONTROLS_H   = 36f;
    public static final float CONTROLS_GAP = 12f;
    public static final float BACK_W       = 120f;
    public static final float BACK_H       = 32f;

    /** Grid drawing area, in pixel coords (Y-up, bottom-left at gridX/Y). */
    public final float gridX;
    public final float gridY;
    public final float gridW;
    public final float gridH;
    public final float cellSize;

    /** Top control strip (speed buttons sit here). */
    public final float controlsX;
    public final float controlsY;
    public final float controlsW;
    public final float controlsH;

    /** Bottom-left Back button rect. */
    public final float backX;
    public final float backY;

    public BattleLayout(PositionAPI position, int gridCellsW, int gridCellsH) {
        float contentX = position.getX() + PAD;
        float contentY = position.getY() + PAD;
        float contentW = position.getWidth()  - 2 * PAD;
        float contentH = position.getHeight() - 2 * PAD;

        // Reserve the top strip for controls.
        this.controlsX = contentX;
        this.controlsY = contentY + contentH - CONTROLS_H;
        this.controlsW = contentW;
        this.controlsH = CONTROLS_H;

        // Reserve the bottom strip for the back button.
        float backStripH = BACK_H + CONTROLS_GAP;
        this.backX = contentX;
        this.backY = contentY;

        // Grid area sits between the two strips.
        float gridAreaY = contentY + backStripH;
        float gridAreaH = contentH - CONTROLS_H - CONTROLS_GAP - backStripH;
        float cellW = contentW   / gridCellsW;
        float cellH = gridAreaH  / gridCellsH;
        this.cellSize = Math.min(cellW, cellH);

        this.gridW = cellSize * gridCellsW;
        this.gridH = cellSize * gridCellsH;
        this.gridX = contentX  + (contentW  - gridW) / 2f;
        this.gridY = gridAreaY + (gridAreaH - gridH) / 2f;
    }
}
