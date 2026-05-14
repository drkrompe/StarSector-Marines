package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data three-column layout: computes left/middle/right {@link ColumnRect}s
 * from the panel's current position. Outer column widths are fixed; the middle
 * column flexes to fill the rest.
 *
 * <p>Pulled out of {@code MarineOpsPanelPlugin} so panel sizing isn't tangled
 * with widget construction or rendering — recompute cheaply per layout pass.
 */
public final class ColumnLayout {

    public static final float PAD        = 12f;
    public static final float GAP        = 12f;
    public static final float LEFT_W     = 260f;
    public static final float RIGHT_W    = 340f;
    public static final float HEADER_H   = 28f;
    public static final float HEADER_PAD = 8f;

    public final ColumnRect left;
    public final ColumnRect middle;
    public final ColumnRect right;

    public ColumnLayout(PositionAPI position) {
        float contentX = position.getX() + PAD;
        float contentY = position.getY() + PAD;
        float contentW = position.getWidth()  - 2 * PAD;
        float contentH = position.getHeight() - 2 * PAD;

        float middleW = contentW - LEFT_W - RIGHT_W - 2 * GAP;
        float leftX   = contentX;
        float middleX = leftX + LEFT_W + GAP;
        float rightX  = middleX + middleW + GAP;

        float bodyTop      = contentY + contentH - HEADER_H - HEADER_PAD;
        float bodyBottom   = contentY;
        float bodyH        = bodyTop - bodyBottom;
        float headerTextY  = contentY + contentH;

        this.left   = new ColumnRect(leftX,   bodyBottom, LEFT_W,  bodyH, headerTextY);
        this.middle = new ColumnRect(middleX, bodyBottom, middleW, bodyH, headerTextY);
        this.right  = new ColumnRect(rightX,  bodyBottom, RIGHT_W, bodyH, headerTextY);
    }
}
