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
        brickHeight = Math.max(1f, brickHeight);
        float brickWidth = brickHeight * BRICK_ASPECT;
        float totalWidth = columns * brickWidth + (columns - 1) * gap;
        float totalHeight = rows * brickHeight + (rows - 1) * gap;
        return new Layout(x + (width - totalWidth) * .5f, y + (height - totalHeight) * .5f,
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
     * Fitted hardware over {@code columns x rows} bricks starting at the given cell: a tinted
     * body, a lit top lip and a full-strength rim in the hardware's colour.
     */
    static void drawFilled(CanvasContext c, Layout layout, int startColumn, int startRow,
                           int columns, int rows, Color tone) {
        for (int row = startRow; row < Math.min(layout.rows(), startRow + rows); row++) {
            for (int column = startColumn; column < Math.min(layout.columns(), startColumn + columns); column++) {
                float bx = layout.brickX(column), by = layout.brickY(row);
                float w = layout.brickWidth(), h = layout.brickHeight();
                brick(c, bx, by, w, h, mix(tone, 0x101311, .84f));
                float lip = Math.max(1f, h * .14f);
                float ch = chamfer(w, h);
                c.fillRect(bx + ch, by + 1f, w - ch * 2f, lip, mix(tone, 0x101311, .62f));
                outline(c, bx, by, w, h, tone, 1.4f);
            }
        }
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
        return Math.max(1f, Math.min(w, h) * .2f);
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
