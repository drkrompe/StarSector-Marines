package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.ui.PositionAPI;

/**
 * Pure-data two-column layout for the mission-select briefing room: a fixed-
 * width clients column on the left and a wide console region on the right
 * (officer header + thumbnail map + dossier stack live inside the console).
 *
 * <p>The console region flexes to fill the rest. Previously a three-column
 * layout (clients / tactical-map / planet-intel); the right two were merged
 * into the comms console per the list-centric briefing-room design — see
 * {@code [[project_comms_officer_narrator]]} memory.
 */
public final class ColumnLayout {

    public static final float PAD        = 12f;
    public static final float GAP        = 12f;
    public static final float LEFT_W     = 260f;
    public static final float HEADER_H   = 28f;
    public static final float HEADER_PAD = 8f;

    public final ColumnRect left;
    public final ColumnRect console;

    public ColumnLayout(PositionAPI position) {
        float contentX = position.getX() + PAD;
        float contentY = position.getY() + PAD;
        float contentW = position.getWidth()  - 2 * PAD;
        float contentH = position.getHeight() - 2 * PAD;

        float consoleW = contentW - LEFT_W - GAP;
        float leftX    = contentX;
        float consoleX = leftX + LEFT_W + GAP;

        float bodyTop      = contentY + contentH - HEADER_H - HEADER_PAD;
        float bodyBottom   = contentY;
        float bodyH        = bodyTop - bodyBottom;
        float headerTextY  = contentY + contentH;

        this.left    = new ColumnRect(leftX,    bodyBottom, LEFT_W,   bodyH, headerTextY);
        this.console = new ColumnRect(consoleX, bodyBottom, consoleW, bodyH, headerTextY);
    }
}
