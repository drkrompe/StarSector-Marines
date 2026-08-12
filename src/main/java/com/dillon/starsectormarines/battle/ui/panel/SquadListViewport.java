package com.dillon.starsectormarines.battle.ui.panel;

/**
 * Pure fixed-row layout shared by the squad overview and compact GOAP lists.
 * Fits a whole number of rows below a fixed header so glyphs never straddle
 * the viewport edge when the list is scrolled without a GL scissor bracket.
 */
final class SquadListViewport {

    final int rowCount;
    final int visibleRows;
    final float rowHeight;
    final float headerHeight;
    final float bottomPadding;
    final float panelHeight;
    final float viewportHeight;
    final float contentHeight;

    private SquadListViewport(int rowCount, int visibleRows, float rowHeight,
                              float headerHeight, float bottomPadding) {
        this.rowCount = rowCount;
        this.visibleRows = visibleRows;
        this.rowHeight = rowHeight;
        this.headerHeight = headerHeight;
        this.bottomPadding = bottomPadding;
        this.panelHeight = headerHeight + visibleRows * rowHeight + bottomPadding;
        this.viewportHeight = visibleRows * rowHeight;
        this.contentHeight = rowCount * rowHeight;
    }

    static SquadListViewport fit(int rowCount, float maxPanelHeight,
                                 float headerHeight, float bottomPadding,
                                 float rowHeight) {
        int safeRowCount = Math.max(0, rowCount);
        float availableForRows = maxPanelHeight - headerHeight - bottomPadding;
        int capacity = Math.max(1, (int) Math.floor(availableForRows / rowHeight));
        return new SquadListViewport(safeRowCount, Math.min(safeRowCount, capacity),
                rowHeight, headerHeight, bottomPadding);
    }

    float rowBottom(float headerBottomY, int rowIndex, float scrollOffset) {
        return headerBottomY - (rowIndex + 1) * rowHeight + scrollOffset;
    }

    boolean rowVisible(float rowBottomY, float bodyBottomY, float headerBottomY) {
        return rowBottomY >= bodyBottomY && rowBottomY + rowHeight <= headerBottomY;
    }

    int rowAt(float py, float bodyBottomY, float headerBottomY, float scrollOffset) {
        if (py < bodyBottomY || py >= headerBottomY) return -1;
        // The bottom edge belongs to the row above it: row bands are
        // (bottom, top], while the panel's own body check remains [bottom, top).
        // ceil(depth)-1 preserves that convention at exact row boundaries.
        int row = (int) Math.ceil((headerBottomY - py + scrollOffset) / rowHeight) - 1;
        return row >= 0 && row < rowCount ? row : -1;
    }
}
