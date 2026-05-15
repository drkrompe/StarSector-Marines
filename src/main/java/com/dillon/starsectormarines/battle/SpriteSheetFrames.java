package com.dillon.starsectormarines.battle;

/**
 * Detected sprite bounding boxes on a sheet, plus the sheet's own pixel
 * dimensions. Produced by {@link SpriteSheetSlicer}; consumed by the
 * battle renderer to compute per-frame UV regions on a SpriteAPI singleton.
 *
 * <p>Frames are returned in left-to-right order, matching the convention
 * the source sprite sheet uses (idle WNES + weapon-up variants).
 */
public final class SpriteSheetFrames {

    public final int sheetWidth;
    public final int sheetHeight;
    public final Frame[] frames;

    public SpriteSheetFrames(int sheetWidth, int sheetHeight, Frame[] frames) {
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frames = frames;
    }

    /** Pixel bounding box of one sprite within the sheet (origin at sheet top-left, Y down). */
    public static final class Frame {
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        public Frame(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
