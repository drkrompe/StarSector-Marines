package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data layout for the battle screen. Fits the {@code gridCellsW × gridCellsH}
 * cell grid behind a full-window tactical camera, with inset anchors for the
 * retained HUD. {@link #cellSize} is the pixel size of one cell — same for X
 * and Y so cells stay square regardless of dialog aspect ratio. At zoom 1 the
 * map covers the complete viewport; the camera may crop and pan the excess on
 * the narrower axis instead of letterboxing the battlefield.
 */
public final class BattleLayout {

    public static final float PAD          = 12f;
    public static final float CONTROLS_H   = 54f;
    public static final float CONTROLS_GAP = 12f;
    /** Tallest top-right time/objective rail, including content-box chrome. */
    public static final float COMMAND_RAIL_H = 192f;
    public static final float BACK_W       = 120f;
    public static final float BACK_H       = 52f;

    /** Grid drawing area, in pixel coords (Y-up, bottom-left at gridX/Y). */
    public final float gridX;
    public final float gridY;
    public final float gridW;
    public final float gridH;
    public final float cellSize;

    /** Top control strip (the MLX time control sits here). */
    public final float controlsX;
    public final float controlsY;
    public final float controlsW;
    public final float controlsH;

    /** Bottom-left battle-action origin retained for compatible layout consumers. */
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

        this.backX = contentX;
        this.backY = contentY;

        // The world is full-bleed. HUD strips are overlays, not holes cut out
        // of the camera. Cover-fit keeps square cells and removes the black
        // bars produced by fitting the complete map inside a different aspect.
        this.gridX = position.getX();
        this.gridY = position.getY();
        this.gridW = position.getWidth();
        this.gridH = position.getHeight();
        float cellW = gridW / gridCellsW;
        float cellH = gridH / gridCellsH;
        this.cellSize = Math.max(cellW, cellH);
    }
}
