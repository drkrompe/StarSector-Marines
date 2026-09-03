package com.dillon.starsectormarines.render2d;

/**
 * What one {@link DrawListRenderer#drain} actually did: how many commands it
 * replayed, of which kinds, and how many GL draws and texture binds those
 * coalesced into.
 *
 * <p>It exists because the two numbers that decide how a frame is made faster
 * are not the same number. A layer emitting forty thousand sheet quads that
 * coalesce into six draws is bound by <em>collection</em>, and no amount of
 * batching helps it; a layer emitting four hundred sprites that cannot coalesce
 * at all is bound by <em>submission</em>, and merging is the whole win. From
 * outside the drain the two look identical — a slow frame — so the census
 * counts them separately.
 *
 * <p>A {@code CUSTOM} command owns its own GL lifecycle, so its draws and binds
 * are not visible here and are deliberately not guessed at; the count of custom
 * passes is reported instead, and a reader has to attribute their cost to the
 * pass rather than to this tally.
 *
 * <p>Mutable and not thread-safe, like everything else on the render path. A
 * caller with no interest in the tally passes {@code null} and pays one null
 * check per flush.
 */
public final class DrawCensus {

    private int commands;
    private int sheetQuads;
    private int solidRects;
    private int polygons;
    private int lines;
    private int ribbons;
    private int sprites;
    private int customs;
    private int drawCalls;
    private int textureBinds;

    /** Commands replayed, whatever their kind. */
    public int commands() { return commands; }
    public int sheetQuads() { return sheetQuads; }
    public int solidRects() { return solidRects; }
    public int polygons() { return polygons; }
    public int lines() { return lines; }
    public int ribbons() { return ribbons; }
    /** Whole-sprite draws, each of which is its own bind and its own draw. */
    public int sprites() { return sprites; }
    /** Passes that owned their GL; their draws and binds are not counted here. */
    public int customs() { return customs; }
    /** {@code glDrawArrays} calls plus whole-sprite draws. */
    public int drawCalls() { return drawCalls; }
    public int textureBinds() { return textureBinds; }

    void recordCommand(DrawCommand.Kind kind) {
        commands++;
        switch (kind) {
            case SHEET_QUAD -> sheetQuads++;
            case SOLID_RECT -> solidRects++;
            case POLY -> polygons++;
            case LINE -> lines++;
            case RIBBON -> ribbons++;
            case SPRITE -> sprites++;
            case CUSTOM -> customs++;
        }
    }

    /** One batched draw that bound a texture first. */
    void recordTexturedDraw() {
        drawCalls++;
        textureBinds++;
    }

    /** One batched draw of untextured geometry. */
    void recordUntexturedDraw() {
        drawCalls++;
    }

    /** Folds another layer's tally into this one, for a whole-frame total. */
    public void add(DrawCensus other) {
        if (other == null) return;
        commands += other.commands;
        sheetQuads += other.sheetQuads;
        solidRects += other.solidRects;
        polygons += other.polygons;
        lines += other.lines;
        ribbons += other.ribbons;
        sprites += other.sprites;
        customs += other.customs;
        drawCalls += other.drawCalls;
        textureBinds += other.textureBinds;
    }

    public void reset() {
        commands = 0;
        sheetQuads = 0;
        solidRects = 0;
        polygons = 0;
        lines = 0;
        ribbons = 0;
        sprites = 0;
        customs = 0;
        drawCalls = 0;
        textureBinds = 0;
    }
}
