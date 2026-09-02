package com.dillon.starsectormarines.ops.battleview;

/**
 * One mark a review frame carries over the battle it draws — a label, an
 * arrow, or an outlined place.
 *
 * <p>Everything here is in <strong>world cell coordinates</strong>, never
 * pixels: an annotation is authored against the map and projected by
 * {@link ReviewAnnotationPainter} through the same {@code BattleCamera} the
 * unit markers use, so a box lands exactly on the ground it names whatever
 * size the frame is rendered at.
 *
 * <p>Cell Y is up, as it is everywhere else in the world model. A box's
 * {@code top} is therefore its higher cell row and draws nearer the top of the
 * image; the record sorts its own corners, so a caller may hand a rectangle
 * over in either order.
 */
public sealed interface ReviewAnnotation {

    /** What this mark is for, and the colour that says so. */
    ReviewStyle style();

    /** A word placed at one cell. Empty text is not a mark and is refused. */
    record Label(float cellX, float cellY, String text, ReviewStyle style)
            implements ReviewAnnotation {
        public Label {
            text = requireText(text);
            style = requireStyle(style);
        }
    }

    /**
     * A line from one cell to another with a filled head at the far end, and
     * an optional word along it. Zero-length is refused: a head with no
     * direction to point in is a dot, and a reader would take it for a unit.
     */
    record Arrow(float fromX, float fromY, float toX, float toY,
                 String text, ReviewStyle style) implements ReviewAnnotation {
        public Arrow {
            if (fromX == toX && fromY == toY) {
                throw new IllegalArgumentException("arrow has no direction");
            }
            text = text == null ? "" : text.trim();
            style = requireStyle(style);
        }
    }

    /**
     * An inclusive cell rect drawn as an outline with its word at the visual
     * top-left, so a compound footprint or a landing area reads as a place
     * rather than as a point. The corners are normalised on construction.
     */
    record Box(int left, int bottom, int right, int top,
               String text, ReviewStyle style) implements ReviewAnnotation {
        public Box {
            int minX = Math.min(left, right);
            int maxX = Math.max(left, right);
            int minY = Math.min(bottom, top);
            int maxY = Math.max(bottom, top);
            left = minX;
            right = maxX;
            bottom = minY;
            top = maxY;
            text = text == null ? "" : text.trim();
            style = requireStyle(style);
        }

        /** Cells across, inclusive. */
        public int width() { return right - left + 1; }

        /** Cells up, inclusive. */
        public int height() { return top - bottom + 1; }
    }

    private static String requireText(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("a label with no text is not a mark");
        }
        return trimmed;
    }

    private static ReviewStyle requireStyle(ReviewStyle style) {
        return style == null ? ReviewStyle.NOTE : style;
    }
}
