package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import java.awt.Color;

/**
 * The Mech Lab's hardware language: a footprint is a stack of chamfered bricks, one per grid
 * cell, each twice as wide as it is tall, so a piece's size reads from its outline. Colour
 * names the hardpoint: energy red-orange, missile green, ballistic yellow, omni grey.
 */
final class HardpointBricks {
    static final Color ENERGY = new Color(0xe0, 0x6a, 0x3c);
    static final Color MISSILE = new Color(0x6c, 0xc4, 0x5e);
    static final Color BALLISTIC = new Color(0xe2, 0xc2, 0x48);
    static final Color OMNI = new Color(0xa4, 0xa8, 0xa0);
    static final Color INTERNAL = new Color(0x7e, 0x94, 0xa6);

    /** Width over height of one brick. */
    static final float BRICK_ASPECT = 2f;

    private HardpointBricks() { }

    static Color tone(HardpointType type) {
        if (type == null) return OMNI;
        return switch (type) {
            case ENERGY -> ENERGY;
            case MISSILE -> MISSILE;
            case BALLISTIC -> BALLISTIC;
        };
    }

    static Color tone(SocketType type) {
        if (type == null) return OMNI;
        return switch (type) {
            case BALLISTIC -> BALLISTIC;
            case MISSILE -> MISSILE;
            case OMNI -> OMNI;
            case CORE, AMMO, UTILITY -> INTERNAL;
        };
    }

    /** The brick-stack box of {@code columns x rows} fitted and centred inside the given area. */
    static Layout fit(float x, float y, float width, float height, int columns, int rows, float gap) {
        columns = Math.max(1, columns);
        rows = Math.max(1, rows);
        float brickHeight = Math.min((height - gap * (rows - 1)) / rows,
                (width - gap * (columns - 1)) / columns / BRICK_ASPECT);
        // Whole pixels, so fused bricks meet on pixel edges and leave no antialiased seam.
        brickHeight = Math.max(1f, (float) Math.floor(brickHeight));
        gap = Math.round(gap);
        float brickWidth = brickHeight * BRICK_ASPECT;
        float totalWidth = columns * brickWidth + (columns - 1) * gap;
        float totalHeight = rows * brickHeight + (rows - 1) * gap;
        return new Layout(Math.round(x + (width - totalWidth) * .5f), Math.round(y + (height - totalHeight) * .5f),
                columns, rows, brickWidth, brickHeight, gap);
    }

    record Layout(float left, float top, int columns, int rows,
                  float brickWidth, float brickHeight, float gap) {
        float width() { return columns * brickWidth + (columns - 1) * gap; }
        float height() { return rows * brickHeight + (rows - 1) * gap; }
        float centerX() { return left + width() * .5f; }
        float centerY() { return top + height() * .5f; }
        float brickX(int column) { return left + column * (brickWidth + gap); }
        float brickY(int row) { return top + row * (brickHeight + gap); }
    }

    /** Empty capacity: dark recessed bricks with a dim rim in the socket's colour. */
    static void drawEmpty(CanvasContext c, Layout layout, Color tone) {
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                float bx = layout.brickX(column), by = layout.brickY(row);
                brick(c, bx, by, layout.brickWidth(), layout.brickHeight(), new Color(0x0b, 0x0e, 0x0d));
                outline(c, bx, by, layout.brickWidth(), layout.brickHeight(), mix(tone, 0x1a1e1b, .55f), 1f);
            }
        }
    }

    /**
     * Fitted hardware over {@code columns x rows} bricks starting at the given cell, drawn as one
     * solid piece: the bricks fuse across their seams, so the body is unbroken and the rim runs
     * only around the outside. Every brick corner on that outside keeps its chamfer, so the
     * outline notches where two bricks meet and the brick count still reads from the edge.
     */
    static void drawFilled(CanvasContext c, Layout layout, int startColumn, int startRow,
                           int columns, int rows, Color tone) {
        int lastColumn = Math.min(layout.columns(), startColumn + columns) - 1;
        int lastRow = Math.min(layout.rows(), startRow + rows) - 1;
        if (lastColumn < startColumn || lastRow < startRow) return;
        float w = layout.brickWidth(), h = layout.brickHeight(), gap = layout.gap();
        float ch = chamfer(w, h);
        Color body = mix(tone, 0x101311, .84f);
        Color lipColor = mix(tone, 0x101311, .62f);
        float lip = Math.max(1f, h * .14f);
        // Fills first, each overlapping the next brick of the piece by a pixel, so the shared
        // edges are covered twice and no antialiased seam survives; outlines go on top after.
        for (int pass = 0; pass < 2; pass++) {
            for (int row = startRow; row <= lastRow; row++) {
                for (int column = startColumn; column <= lastColumn; column++) {
                    boolean top = row == startRow, bottom = row == lastRow;
                    boolean left = column == startColumn, right = column == lastColumn;
                    float x0 = layout.brickX(column), y0 = layout.brickY(row);
                    // Reach across the seam to the next brick of the same piece.
                    float x1 = x0 + w + (right ? 0f : gap), y1 = y0 + h + (bottom ? 0f : gap);
                    float tl = top || left ? ch : 0f, tr = top || right ? ch : 0f;
                    float br = bottom || right ? ch : 0f, bl = bottom || left ? ch : 0f;
                    if (pass == 0) {
                        fillOctagon(c, x0, y0, x1 + (right ? 0f : 1f), y1 + (bottom ? 0f : 1f),
                                tl, tr, br, bl, body);
                        if (top) c.fillRect(x0 + tl, y0 + 1f, x1 - tr - x0 - tl, lip, lipColor);
                        continue;
                    }
                    // Outline only what faces outside the piece.
                    if (top) c.line(x0 + tl, y0, x1 - tr, y0, tone, 1.4f);
                    if (bottom) c.line(x0 + bl, y1, x1 - br, y1, tone, 1.4f);
                    if (left) c.line(x0, y0 + tl, x0, y1 - bl, tone, 1.4f);
                    if (right) c.line(x1, y0 + tr, x1, y1 - br, tone, 1.4f);
                    if (tl > 0f) c.line(x0, y0 + tl, x0 + tl, y0, tone, 1.4f);
                    if (tr > 0f) c.line(x1 - tr, y0, x1, y0 + tr, tone, 1.4f);
                    if (br > 0f) c.line(x1, y1 - br, x1 - br, y1, tone, 1.4f);
                    if (bl > 0f) c.line(x0 + bl, y1, x0, y1 - bl, tone, 1.4f);
                }
            }
        }
    }

    /**
     * A rectangle with each corner cut by its own amount (0 leaves it square), filled as a top
     * band, a body and a bottom band so no interior diagonal seam can show.
     */
    private static void fillOctagon(CanvasContext c, float x0, float y0, float x1, float y1,
                                    float tl, float tr, float br, float bl, Color fill) {
        float topBand = Math.max(tl, tr), bottomBand = Math.max(bl, br);
        if (topBand > 0f) c.fillQuad(x0 + tl, y0, x1 - tr, y0,
                x1 - Math.max(0f, tr - topBand), y0 + topBand, x0 + Math.max(0f, tl - topBand), y0 + topBand, fill);
        // The body overlaps both bands by a pixel for the same reason the bricks overlap.
        float bodyTop = Math.max(y0, y0 + topBand - 1f), bodyBottom = Math.min(y1, y1 - bottomBand + 1f);
        c.fillRect(x0, bodyTop, x1 - x0, bodyBottom - bodyTop, fill);
        if (bottomBand > 0f) c.fillQuad(x0, y1 - bottomBand, x1, y1 - bottomBand,
                x1 - br, y1, x0 + bl, y1, fill);
    }

    /** A chamfered outline around a whole area, for a socket rim. */
    static void rim(CanvasContext c, float x, float y, float w, float h, float cut, Color color, float stroke) {
        c.line(x + cut, y, x + w - cut, y, color, stroke);
        c.line(x + w - cut, y, x + w, y + cut, color, stroke);
        c.line(x + w, y + cut, x + w, y + h - cut, color, stroke);
        c.line(x + w, y + h - cut, x + w - cut, y + h, color, stroke);
        c.line(x + w - cut, y + h, x + cut, y + h, color, stroke);
        c.line(x + cut, y + h, x, y + h - cut, color, stroke);
        c.line(x, y + h - cut, x, y + cut, color, stroke);
        c.line(x, y + cut, x + cut, y, color, stroke);
    }

    static Color mix(Color tone, int base, float towardBase) {
        float keep = 1f - towardBase;
        return new Color(
                Math.round(tone.getRed() * keep + ((base >> 16) & 0xff) * towardBase),
                Math.round(tone.getGreen() * keep + ((base >> 8) & 0xff) * towardBase),
                Math.round(tone.getBlue() * keep + (base & 0xff) * towardBase));
    }

    private static float chamfer(float w, float h) {
        return Math.max(1f, Math.round(Math.min(w, h) * .2f));
    }

    private static void brick(CanvasContext c, float x, float y, float w, float h, Color fill) {
        float ch = chamfer(w, h);
        c.fillRect(x + ch, y, w - ch * 2f, h, fill);
        c.fillQuad(x, y + ch, x + ch, y, x + ch, y + h, x, y + h - ch, fill);
        c.fillQuad(x + w - ch, y, x + w, y + ch, x + w, y + h - ch, x + w - ch, y + h, fill);
    }

    private static void outline(CanvasContext c, float x, float y, float w, float h, Color color, float stroke) {
        rim(c, x, y, w, h, chamfer(w, h), color, stroke);
    }
}
