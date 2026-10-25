package com.dillon.starsectormarines.ops.fitting;

/**
 * The one brick size of the fitting rooms. A part is drawn at this size everywhere it is
 * shown — the Mech Lab's and the Boat Deck's boards, their stores pictures, the selected
 * mount card and a part on the end of the pointer — so a player sees at a glance which
 * slot a part drops into.
 *
 * <p>It follows the screen rather than standing at a fixed pixel count: the room's document
 * grows past its reference size on a larger panel, and a fixed brick would shrink inside it.
 * A room sets it from its document height before it lays out ({@link #fitTo}); every
 * drawing and every card box reads {@link #height()}.
 */
public final class FittingBricks {

    /** Width over height of one brick: narrower than tall, so even a square footprint stands upright. */
    public static final float ASPECT = 0.75f;
    public static final float GAP = 2f;
    /** A brick's height as a share of the document's height: 40px on a 1080-line document. */
    static final float SHARE = 0.037f;
    static final float MIN_HEIGHT = 28f;
    static final float MAX_HEIGHT = 56f;
    /** The tallest part or socket a stores picture or mount card must hold, in bricks. */
    static final int TALLEST_COLUMNS = 2;
    static final int TALLEST_ROWS = 3;
    /** The picture box's room around its bricks: a border and a little air. */
    private static final float PICTURE_PAD_X = 28f;
    private static final float PICTURE_PAD_Y = 4f;

    private static volatile float height = heightFor(1080f);

    private FittingBricks() { }

    /** The brick height for a document of this height: whole pixels, within the floor and ceiling. */
    public static float heightFor(float documentHeight) {
        float wanted = Math.round(documentHeight * SHARE);
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, wanted));
    }

    /** Sizes every brick for a room laid out on a document of this height. */
    public static void fitTo(float documentHeight) {
        if (Float.isFinite(documentHeight) && documentHeight > 0f) height = heightFor(documentHeight);
    }

    public static float height() {
        return height;
    }

    public static float width() {
        return Math.round(height * ASPECT);
    }

    /** A run of {@code count} bricks across, seams included. */
    public static float across(int count) {
        return count * width() + Math.max(0, count - 1) * GAP;
    }

    /** A run of {@code count} bricks down, seams included. */
    public static float down(int count) {
        return count * height + Math.max(0, count - 1) * GAP;
    }

    /** The picture box a stores card or mount card gives its part: room for the tallest. */
    public static int pictureWidth() {
        return Math.round(across(TALLEST_COLUMNS) + PICTURE_PAD_X);
    }

    public static int pictureHeight() {
        return Math.round(down(TALLEST_ROWS) + PICTURE_PAD_Y);
    }

    public static String pictureStyle() {
        return "width: " + pictureWidth() + "px; height: " + pictureHeight() + "px;";
    }
}
