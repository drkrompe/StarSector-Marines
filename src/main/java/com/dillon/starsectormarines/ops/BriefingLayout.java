package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data two-zone layout for the briefing screen: a wide left "map zone"
 * (cropped terrain inset with target reticle, mission name as header) and a
 * narrow right "info zone" (mission details + Accept/Back buttons, "Briefing"
 * header).
 *
 * <p>Outer padding, gap, and header strip mirror {@link ColumnLayout} so the
 * two screens read as the same UI family.
 */
public final class BriefingLayout {

    public static final float PAD        = 12f;
    public static final float GAP        = 12f;
    public static final float INFO_W     = 380f;
    public static final float HEADER_H   = 28f;
    public static final float HEADER_PAD = 8f;

    public final ColumnRect mapZone;
    public final ColumnRect infoZone;

    public BriefingLayout(PositionAPI position) {
        float contentX = position.getX() + PAD;
        float contentY = position.getY() + PAD;
        float contentW = position.getWidth()  - 2 * PAD;
        float contentH = position.getHeight() - 2 * PAD;

        float mapW  = contentW - INFO_W - GAP;
        float infoX = contentX + mapW + GAP;

        float bodyTop     = contentY + contentH - HEADER_H - HEADER_PAD;
        float bodyBottom  = contentY;
        float bodyH       = bodyTop - bodyBottom;
        float headerTextY = contentY + contentH;

        this.mapZone  = new ColumnRect(contentX, bodyBottom, mapW,   bodyH, headerTextY);
        this.infoZone = new ColumnRect(infoX,    bodyBottom, INFO_W, bodyH, headerTextY);
    }
}
