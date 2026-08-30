package com.dillon.starsectormarines.render2d;

/**
 * Pan + zoom transform for the battle screen. Bridges sim-space (cells, with
 * the bottom-left of the map at (0, 0)) and screen-space (pixels, with the
 * viewport rect provided by {@code BattleLayout}).
 *
 * <p>Pan is stored as the world-cell coordinate sitting at the <em>center</em>
 * of the viewport. Zoom multiplies the layout's cover-fitted cell size — zoom
 * 1.0 fills the complete battle canvas while preserving square cells, and the
 * narrower world axis may be panned to reveal its cropped edge. Zooming in
 * past 1.0 grows each cell on screen while shrinking the visible slice.
 *
 * <p>{@link #zoomAt} re-anchors the pan so the world point under the cursor
 * stays under the cursor across the zoom step (zoom-to-cursor, which feels
 * much better than zoom-to-center on larger maps).
 *
 * <p>Y-axis convention matches the existing render code: cell Y and screen Y
 * both increase upward.
 */
public final class BattleCamera {

    public static final float MIN_ZOOM = 1.0f;
    /** Tactical close-up while retaining enough cells to read local movement. */
    public static final float MAX_ZOOM = 8.0f;
    /** Multiplier applied per wheel notch. One notch ≈ 20% zoom step. */
    private static final float ZOOM_STEP = 1.20f;

    private final int worldCellsW;
    private final int worldCellsH;

    private float vpX, vpY, vpW, vpH;
    private float baseCellSize;

    private float zoom = 1f;
    private float panCellX;
    private float panCellY;

    public BattleCamera(int worldCellsW, int worldCellsH) {
        this.worldCellsW = worldCellsW;
        this.worldCellsH = worldCellsH;
        this.panCellX = worldCellsW * 0.5f;
        this.panCellY = worldCellsH * 0.5f;
    }

    /**
     * Sets the screen-space viewport rect (in pixels) and the base cell size
     * — {@code baseCellSize} is the cover-fitted pixel size of one cell at
     * zoom 1.0, taken from {@code BattleLayout.cellSize}. Re-clamps pan in case
     * the viewport shrank under the camera.
     */
    public void setViewport(float x, float y, float w, float h, float baseCellSize) {
        this.vpX = x;
        this.vpY = y;
        this.vpW = w;
        this.vpH = h;
        this.baseCellSize = baseCellSize;
        clampPan();
    }

    public float cellPxSize()  { return baseCellSize * zoom; }
    public float zoom()        { return zoom; }
    public float panCellX()    { return panCellX; }
    public float panCellY()    { return panCellY; }
    public float vpX()         { return vpX; }
    public float vpY()         { return vpY; }
    public float vpW()         { return vpW; }
    public float vpH()         { return vpH; }
    public int worldCellsW()   { return worldCellsW; }
    public int worldCellsH()   { return worldCellsH; }

    public float cellToScreenX(float cellX) {
        return vpX + vpW * 0.5f + (cellX - panCellX) * cellPxSize();
    }

    public float cellToScreenY(float cellY) {
        return vpY + vpH * 0.5f + (cellY - panCellY) * cellPxSize();
    }

    public float screenToCellX(float px) {
        return panCellX + (px - (vpX + vpW * 0.5f)) / cellPxSize();
    }

    public float screenToCellY(float py) {
        return panCellY + (py - (vpY + vpH * 0.5f)) / cellPxSize();
    }

    public boolean containsScreen(float px, float py) {
        return px >= vpX && px < vpX + vpW && py >= vpY && py < vpY + vpH;
    }

    /**
     * Inclusive cell AABB covering the current viewport, clamped to this
     * camera's world. Equivalent to {@code visibleCells(0, worldCellsW, worldCellsH)}.
     */
    public VisibleCellRect visibleCells() {
        return visibleCells(0, worldCellsW, worldCellsH);
    }

    /**
     * Inclusive cell AABB covering the current viewport plus {@code margin}
     * cells on each side, clamped to this camera's world.
     */
    public VisibleCellRect visibleCells(int margin) {
        return visibleCells(margin, worldCellsW, worldCellsH);
    }

    /**
     * Inclusive cell AABB covering the current viewport plus {@code margin}
     * cells on each side, clamped to {@code [0, gridW) × [0, gridH)}.
     *
     * <p>A cell at integer {@code (cx, cy)} occupies {@code [cx, cx+1) × [cy, cy+1)}.
     * The viewport edges are mapped through {@link #screenToCellX}/{@link #screenToCellY}
     * and expanded with {@code floor}/{@code ceil} so a partially visible cell is
     * included. Callers that need a halo (fog gradient, parallax edge samples)
     * pass a positive {@code margin}.
     */
    public VisibleCellRect visibleCells(int margin, int gridW, int gridH) {
        if (cellPxSize() <= 0f || vpW <= 0f || vpH <= 0f || gridW <= 0 || gridH <= 0) {
            return VisibleCellRect.EMPTY;
        }
        int pad = Math.max(0, margin);
        int minX = (int) Math.floor(screenToCellX(vpX)) - pad;
        int maxX = (int) Math.ceil(screenToCellX(vpX + vpW)) + pad;
        int minY = (int) Math.floor(screenToCellY(vpY)) - pad;
        int maxY = (int) Math.ceil(screenToCellY(vpY + vpH)) + pad;
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(gridW - 1, maxX);
        maxY = Math.min(gridH - 1, maxY);
        if (minX > maxX || minY > maxY) return VisibleCellRect.EMPTY;
        return new VisibleCellRect(minX, minY, maxX, maxY);
    }

    /** Pans by a pixel delta — drag handlers pass mouse-move deltas straight through. */
    public void panByPixels(float dxPx, float dyPx) {
        float c = cellPxSize();
        if (c <= 0f) return;
        panCellX -= dxPx / c;
        panCellY -= dyPx / c;
        clampPan();
    }

    /** Pans by a cell delta — keyboard handlers (WASD / arrows) accumulate dt-scaled cell steps. */
    public void panByCells(float dxCells, float dyCells) {
        panCellX += dxCells;
        panCellY += dyCells;
        clampPan();
    }

    /** Centers the viewport on one world-cell position, subject to world-edge clamping. */
    public void centerOn(float cellX, float cellY) {
        panCellX = cellX;
        panCellY = cellY;
        clampPan();
    }

    /**
     * Applies one wheel-notch worth of zoom centered on ({@code anchorScreenX},
     * {@code anchorScreenY}). {@code notches} is the signed wheel delta — sign
     * determines direction, magnitude scales the step. The world point under
     * the anchor stays under the anchor across the zoom (zoom-to-cursor).
     */
    public void zoomAt(float notches, float anchorScreenX, float anchorScreenY) {
        if (notches == 0f) return;
        float worldX = screenToCellX(anchorScreenX);
        float worldY = screenToCellY(anchorScreenY);
        float factor = (float) Math.pow(ZOOM_STEP, notches);
        float target = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (target == zoom) return;
        zoom = target;
        // Re-anchor: shift pan so the same world point still projects to the anchor pixel.
        float newWorldX = screenToCellX(anchorScreenX);
        float newWorldY = screenToCellY(anchorScreenY);
        panCellX += worldX - newWorldX;
        panCellY += worldY - newWorldY;
        clampPan();
    }

    /**
     * Clamps pan so the camera never reveals "outside" the world rect. When
     * the world fits inside the viewport on an axis (zoomed out enough that
     * half the viewport in cells exceeds half the world), that axis is locked
     * to the world midpoint so the map stays centered instead of sliding.
     */
    private void clampPan() {
        if (cellPxSize() <= 0f) return;
        float halfVpCellsX = (vpW * 0.5f) / cellPxSize();
        float halfVpCellsY = (vpH * 0.5f) / cellPxSize();
        float halfWorldX = worldCellsW * 0.5f;
        float halfWorldY = worldCellsH * 0.5f;
        if (halfVpCellsX >= halfWorldX) {
            panCellX = halfWorldX;
        } else {
            panCellX = Math.max(halfVpCellsX, Math.min(worldCellsW - halfVpCellsX, panCellX));
        }
        if (halfVpCellsY >= halfWorldY) {
            panCellY = halfWorldY;
        } else {
            panCellY = Math.max(halfVpCellsY, Math.min(worldCellsH - halfVpCellsY, panCellY));
        }
    }
}
