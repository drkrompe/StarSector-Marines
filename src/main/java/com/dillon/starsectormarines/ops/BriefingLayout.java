package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data full-canvas layout for the pre-battle loadout screen
 * ({@link BriefingScreen}). Two equal columns under a shared header strip: a
 * left "mission" column (briefing details, salvage negotiation, captain) and a
 * right "detachment" column (what the player's fleet brings vs. what the
 * employer provides, plus Deploy / Back).
 *
 * <p>The old decorative planet-map zone is gone — it provided no gameplay and
 * ate ~70% of the screen via a fixed {@code INFO_W} strip. The action area now
 * uses the full width (roadmap command-powers S8 Slice B). A future battlespace
 * preview may reclaim a corner, but the layout stands on its own without it.
 */
public final class BriefingLayout {

    public static final float PAD        = 12f;
    public static final float GAP        = 16f;
    public static final float HEADER_H   = 28f;
    public static final float HEADER_PAD = 8f;

    public final ColumnRect leftCol;
    public final ColumnRect rightCol;
    /** Top edge of the canvas where the mission-name header is drawn. */
    public final float headerTextY;
    /** Left edge of the header text (= left column x). */
    public final float headerX;

    public BriefingLayout(PositionAPI position) {
        float contentX = position.getX() + PAD;
        float contentY = position.getY() + PAD;
        float contentW = position.getWidth()  - 2 * PAD;
        float contentH = position.getHeight() - 2 * PAD;

        float bodyTop    = contentY + contentH - HEADER_H - HEADER_PAD;
        float bodyBottom = contentY;
        float bodyH      = bodyTop - bodyBottom;
        this.headerTextY = contentY + contentH;
        this.headerX     = contentX;

        float colW = (contentW - GAP) / 2f;
        this.leftCol  = new ColumnRect(contentX,              bodyBottom, colW, bodyH, headerTextY);
        this.rightCol = new ColumnRect(contentX + colW + GAP, bodyBottom, colW, bodyH, headerTextY);
    }
}
