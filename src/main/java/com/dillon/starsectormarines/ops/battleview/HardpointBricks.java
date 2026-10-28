package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.marine.MechInternal;
import com.dillon.starsectormarines.ops.fitting.FittingBricks;
import com.dillon.starsectormarines.ui.retained.CanvasContext;

import com.dillon.starsectormarines.ui.retained.CanvasMetrics;

import java.awt.Color;

/**
 * The Mech Lab's hardware language: a footprint is a stack of chamfered bricks, one per grid
 * cell, each narrower than it is tall, so a piece's size reads from its outline. Colour
 * names the hardpoint: energy red-orange, missile green, ballistic yellow, omni grey; the
 * frame's own parts are engine blue, flux violet and utility teal, and the auxiliary
 * compartment that holds the last two is steel.
 */
final class HardpointBricks {
    static final Color ENERGY = new Color(0xe0, 0x6a, 0x3c);
    static final Color MISSILE = new Color(0x6c, 0xc4, 0x5e);
    static final Color BALLISTIC = new Color(0xe2, 0xc2, 0x48);
    static final Color OMNI = new Color(0xa4, 0xa8, 0xa0);
    /** The frame's own mounts, kept clear of the weapon colours: engine, flux, utility. */
    static final Color ENGINE = new Color(0x5e, 0x96, 0xd2);
    static final Color FLUX = new Color(0xa0, 0x7e, 0xdc);
    static final Color UTILITY = new Color(0x4c, 0xb4, 0xa6);
    /** The auxiliary compartment's rim: a neutral steel, since its parts bring their own colours. */
    static final Color AUXILIARY = new Color(0x8a, 0xa4, 0xb8);

    /** Width over height of one brick: narrower than tall, so even a square footprint stands upright. */
    static final float BRICK_ASPECT = FittingBricks.ASPECT;
    static final float BRICK_GAP = FittingBricks.GAP;

    private HardpointBricks() { }

    /**
     * One brick's height in document pixels, everywhere a brick is drawn: the boards, the
     * stores pictures, the selected-mount card and a part being dragged.
     *
     * @see FittingBricks#height()
     */
    static float brickHeight() {
        return FittingBricks.height();
    }

    static Color tone(HardpointType type) {
        if (type == null) return OMNI;
        return switch (type) {
            case ENERGY -> ENERGY;
            case MISSILE -> MISSILE;
            case BALLISTIC -> BALLISTIC;
        };
    }

    /** A marine's piece of kit, in the colour of the hardware it is drawn as. */
    static Color tone(KitBrick.Kind kind) {
        return switch (kind) {
            case BALLISTIC -> BALLISTIC;
            case ENERGY -> ENERGY;
            case MISSILE -> MISSILE;
            case UTILITY -> UTILITY;
            case ARMOR -> OMNI;
            case SYSTEM -> FLUX;
        };
    }

    static Color tone(SocketType type) {
        if (type == null) return OMNI;
        return switch (type) {
            case BALLISTIC -> BALLISTIC;
            case ENERGY -> ENERGY;
            case MISSILE -> MISSILE;
            case OMNI -> OMNI;
            case ENGINE -> ENGINE;
            case AUXILIARY -> AUXILIARY;
            case PLATING -> OMNI;
        };
    }

    static Color tone(MechInternal.Role role) {
        return switch (role) {
            case ENGINE -> ENGINE;
            case CAPACITOR_BANK -> FLUX;
            case VENT_ARRAY, LOADER -> UTILITY;
            case PLATING -> OMNI;
        };
    }

    /**
     * A brick-stack of {@code columns x rows} at the one brick size, centred in the given area
     * of a canvas whose surface {@code metrics} maps to the document: a stores picture or a
     * mount card draws its part at the size the board does, however its surface is scaled.
     */
    static Layout centred(float x, float y, float width, float height, int columns, int rows,
                          CanvasMetrics metrics) {
        float scaleX = metrics != null && metrics.scaleX() > 0f ? metrics.scaleX() : 1f;
        float scaleY = metrics != null && metrics.scaleY() > 0f ? metrics.scaleY() : 1f;
        columns = Math.max(1, columns);
        rows = Math.max(1, rows);
        float brickHeight = brickHeight() / scaleY;
        float brickWidth = Math.round(brickHeight() * BRICK_ASPECT) / scaleX;
        float gap = BRICK_GAP / scaleY;
        float totalWidth = columns * brickWidth + (columns - 1) * gap;
        float totalHeight = rows * brickHeight + (rows - 1) * gap;
        return new Layout(x + (width - totalWidth) * .5f, y + (height - totalHeight) * .5f,
                columns, rows, brickWidth, brickHeight, gap);
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

    /**
     * A brick-stack of {@code columns x rows} at a fixed brick size, its top-left at the given
     * point. The doll draws every socket this way, so a 2x3 piece is physically larger than a 2x2.
     */
    static Layout at(float x, float y, int columns, int rows, float brickHeight, float gap) {
        brickHeight = Math.max(1f, (float) Math.floor(brickHeight));
        return new Layout(Math.round(x), Math.round(y), Math.max(1, columns), Math.max(1, rows),
                Math.round(brickHeight * BRICK_ASPECT), brickHeight, Math.round(gap));
    }

    record Layout(float left, float top, int columns, int rows,
                  float brickWidth, float brickHeight, float gap) {
        float width() { return columns * brickWidth + (columns - 1) * gap; }
        float height() { return rows * brickHeight + (rows - 1) * gap; }
        float centerX() { return left + width() * .5f; }
        float centerY() { return top + height() * .5f; }
        float brickX(int column) { return left + column * (brickWidth + gap); }
        float brickY(int row) { return top + row * (brickHeight + gap); }
        /** Width of a run of {@code count} bricks, seams included. */
        float span(int count, float brick) { return count * brick + Math.max(0, count - 1) * gap; }
    }

    /**
     * Empty capacity: dark bricks shaded as recesses, darkest under their top lip and lifting
     * toward the floor, with a dim rim in the socket's colour.
     */
    static void drawEmpty(CanvasContext c, Layout layout, Color tone) {
        Color shadowed = new Color(0x05, 0x07, 0x06), floor = new Color(0x18, 0x1d, 0x1a);
        for (int row = 0; row < layout.rows(); row++) {
            for (int column = 0; column < layout.columns(); column++) {
                float bx = layout.brickX(column), by = layout.brickY(row);
                float w = layout.brickWidth(), h = layout.brickHeight(), ch = chamfer(w, h);
                shadeOctagon(c, bx, by, bx + w, by + h, ch, ch, ch, ch, by, by + h, shadowed, floor);
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
        // Raised plate: lit from above, so the face falls from a bright top to a deep bottom
        // across the whole piece, with a lit top-and-left bevel and a shaded bottom-and-right one.
        Color lit = mix(tone, 0x101311, .22f);
        Color deep = mix(tone, 0x101311, .74f);
        Color bevelLight = mix(tone, 0xffffff, .38f);
        Color bevelDark = mix(tone, 0x050605, .82f);
        float pieceTop = layout.brickY(startRow);
        float pieceBottom = layout.brickY(lastRow) + h;
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
                        shadeOctagon(c, x0, y0, x1 + (right ? 0f : 1f), y1 + (bottom ? 0f : 1f),
                                tl, tr, br, bl, pieceTop, pieceBottom, lit, deep);
                        continue;
                    }
                    // Bevel just inside the outline, on the faces that look outward.
                    float in = 2f;
                    if (top) c.line(x0 + tl + in * .4f, y0 + in, x1 - tr - in * .4f, y0 + in, bevelLight, 1.6f);
                    if (left) c.line(x0 + in, y0 + tl + in * .4f, x0 + in, y1 - bl - in * .4f, bevelLight, 1.2f);
                    if (bottom) c.line(x0 + bl + in * .4f, y1 - in, x1 - br - in * .4f, y1 - in, bevelDark, 1.6f);
                    if (right) c.line(x1 - in, y0 + tr + in * .4f, x1 - in, y1 - br - in * .4f, bevelDark, 1.2f);
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
     * A cut-corner rectangle filled with a vertical gradient from {@code top} at {@code shadeTop}
     * to {@code bottom} at {@code shadeBottom}: three gradient quads (the cut top band, the
     * body, the cut bottom band), each opaque and overlapping the next by a pixel so the
     * shared edges leave no seam.
     */
    private static void shadeOctagon(CanvasContext c, float x0, float y0, float x1, float y1,
                                     float tl, float tr, float br, float bl,
                                     float shadeTop, float shadeBottom, Color top, Color bottom) {
        float span = Math.max(1f, shadeBottom - shadeTop);
        float topBand = Math.max(tl, tr), bottomBand = Math.max(bl, br);
        float bodyTop = Math.max(y0, y0 + topBand - 1f), bodyBottom = Math.min(y1, y1 - bottomBand + 1f);
        if (topBand > 0f) {
            float yb = y0 + topBand;
            Color a = at(top, bottom, shadeTop, span, y0), b = at(top, bottom, shadeTop, span, yb);
            c.fillQuadGradient(left(x0, y0, y1, tl, bl, y0), y0, right(x1, y0, y1, tr, br, y0), y0,
                    right(x1, y0, y1, tr, br, yb), yb, left(x0, y0, y1, tl, bl, yb), yb, a, a, b, b);
        }
        Color a = at(top, bottom, shadeTop, span, bodyTop), b = at(top, bottom, shadeTop, span, bodyBottom);
        c.fillQuadGradient(x0, bodyTop, x1, bodyTop, x1, bodyBottom, x0, bodyBottom, a, a, b, b);
        if (bottomBand > 0f) {
            float ya = y1 - bottomBand;
            Color d = at(top, bottom, shadeTop, span, ya), e = at(top, bottom, shadeTop, span, y1);
            c.fillQuadGradient(left(x0, y0, y1, tl, bl, ya), ya, right(x1, y0, y1, tr, br, ya), ya,
                    right(x1, y0, y1, tr, br, y1), y1, left(x0, y0, y1, tl, bl, y1), y1, d, d, e, e);
        }
    }

    private static Color at(Color top, Color bottom, float shadeTop, float span, float y) {
        return lerp(top, bottom, Math.max(0f, Math.min(1f, (y - shadeTop) / span)));
    }

    private static float left(float x0, float y0, float y1, float tl, float bl, float y) {
        return x0 + Math.max(0f, tl - (y - y0)) + Math.max(0f, bl - (y1 - y));
    }

    private static float right(float x1, float y0, float y1, float tr, float br, float y) {
        return x1 - Math.max(0f, tr - (y - y0)) - Math.max(0f, br - (y1 - y));
    }

    private static Color lerp(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
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

    private static void outline(CanvasContext c, float x, float y, float w, float h, Color color, float stroke) {
        rim(c, x, y, w, h, chamfer(w, h), color, stroke);
    }
}
