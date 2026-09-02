package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a frame's {@link ReviewAnnotations} over the scene, through the same
 * {@link BattleCamera} the unit markers use so a box lands on exactly the
 * ground it names.
 *
 * <p>The projection is separated from the drawing on purpose: where a cell
 * ends up in the image is the part that can be wrong in a way no eye catches
 * on a 2240px frame, and it is asked directly by
 * {@code ReviewAnnotationPaintingTest}.
 */
final class ReviewAnnotationPainter {

    /** Text never shrinks past this, whatever the frame's size argues for. */
    static final int MIN_LABEL_POINTS = 10;
    private static final int MAX_LABEL_POINTS = 18;
    /** Frame pixels per point of label type. */
    private static final float LABEL_POINTS_PER_PIXEL = 1f / 150f;

    /** Clear of the frame edge, so a nudged label is inside rather than flush. */
    private static final int EDGE_MARGIN = 3;
    private static final int PLATE_PAD_X = 4;
    private static final int PLATE_PAD_Y = 2;

    private static final Color PLATE_FILL = new Color(0, 0, 0, 205);
    private static final Color HALO = new Color(0, 0, 0, 200);

    private ReviewAnnotationPainter() { }

    /** Screen x of a world cell coordinate. The marker code's own arithmetic. */
    static float screenX(BattleCamera camera, float cellX) {
        return camera.cellToScreenX(cellX);
    }

    /**
     * Screen y of a world cell coordinate. World Y is up and image Y is down,
     * so the frame's height is the axis this flips around — the same
     * expression every marker in {@code BattleReviewFrameRenderer} uses.
     */
    static float screenY(BattleCamera camera, int imageHeight, float cellY) {
        return imageHeight - camera.cellToScreenY(cellY);
    }

    /** Label type for a frame this wide, floored at {@link #MIN_LABEL_POINTS}. */
    static int labelPoints(int imageWidth) {
        int scaled = Math.round(imageWidth * LABEL_POINTS_PER_PIXEL);
        return Math.max(MIN_LABEL_POINTS, Math.min(MAX_LABEL_POINTS, scaled));
    }

    static Font labelFont(int imageWidth) {
        return new Font(Font.SANS_SERIF, Font.BOLD, labelPoints(imageWidth));
    }

    /**
     * Nudges a text plate back inside the frame. A label that runs off the
     * right edge loses exactly the word it was placed for, and a label under
     * the caption band is painted over by it a moment later.
     */
    static Rectangle clampToFrame(Rectangle plate, int imageWidth, int imageHeight) {
        int left = Math.min(plate.x,
                imageWidth - plate.width - EDGE_MARGIN);
        left = Math.max(EDGE_MARGIN, left);
        int top = Math.min(plate.y,
                imageHeight - plate.height - EDGE_MARGIN);
        top = Math.max(BattleReviewFrameRenderer.HEADER_HEIGHT + EDGE_MARGIN, top);
        return new Rectangle(left, top, plate.width, plate.height);
    }

    /**
     * Every mark, in authoring order, over whatever is already in
     * {@code graphics}.
     *
     * <p>Geometry first and words second, in two passes over the same list.
     * Drawing each mark complete in turn is the obvious shape and is wrong:
     * two arrival berths sit a cell apart, so the outline and interior wash of
     * the second paint straight over the word belonging to the first, and the
     * frame comes out carrying a box labelled with half a letter.
     */
    static void paint(Graphics2D graphics, BattleCamera camera,
                      int imageWidth, int imageHeight,
                      ReviewAnnotations annotations) {
        if (annotations == null || annotations.isEmpty()) return;
        Font font = labelFont(imageWidth);
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        List<PendingLabel> words = new ArrayList<>();
        for (ReviewAnnotation mark : annotations) {
            if (mark instanceof ReviewAnnotation.Box box) {
                paintBox(graphics, camera, imageHeight, metrics, words, box);
            } else if (mark instanceof ReviewAnnotation.Arrow arrow) {
                paintArrow(graphics, camera, imageHeight, metrics, words, arrow);
            } else if (mark instanceof ReviewAnnotation.Label label) {
                paintLabel(graphics, camera, imageHeight, metrics, words, label);
            }
        }
        List<Rectangle> placed = new ArrayList<>();
        for (PendingLabel word : words) {
            drawPlate(graphics, metrics, word.text(), word.style(),
                    place(word.preferred(), placed, imageWidth, imageHeight,
                            word.alternativeTops()));
        }
    }

    /** A word and the positions it would accept, held until every shape is down. */
    private record PendingLabel(String text, ReviewStyle style,
                                Rectangle preferred, int[] alternativeTops) { }

    private static void paintBox(Graphics2D graphics, BattleCamera camera,
                                 int imageHeight, FontMetrics metrics,
                                 List<PendingLabel> words,
                                 ReviewAnnotation.Box box) {
        float left = screenX(camera, box.left());
        float right = screenX(camera, box.right() + 1f);
        float top = screenY(camera, imageHeight, box.top() + 1f);
        float bottom = screenY(camera, imageHeight, box.bottom());
        int x = Math.round(left);
        int y = Math.round(top);
        int width = Math.max(2, Math.round(right - left));
        int height = Math.max(2, Math.round(bottom - top));

        // A wash rather than a fill: the compound underneath is the thing the
        // reader is judging, and a solid tint would hide the state it is in.
        graphics.setColor(box.style().at(38));
        graphics.fillRect(x, y, width, height);
        graphics.setStroke(new BasicStroke(box.style().strokeWidth + 1.6f));
        graphics.setColor(HALO);
        graphics.drawRect(x, y, width, height);
        graphics.setStroke(new BasicStroke(box.style().strokeWidth));
        graphics.setColor(box.style().color);
        graphics.drawRect(x, y, width, height);

        if (box.text().isEmpty()) return;
        int plateHeight = plateHeight(metrics);
        words.add(new PendingLabel(box.text(), box.style(),
                plateFor(metrics, box.text(), x, y - plateHeight - 2),
                new int[]{y + 2, y + height + 2, y - 2 * plateHeight - 4}));
    }

    private static void paintArrow(Graphics2D graphics, BattleCamera camera,
                                   int imageHeight, FontMetrics metrics,
                                   List<PendingLabel> words,
                                   ReviewAnnotation.Arrow arrow) {
        float x0 = screenX(camera, arrow.fromX());
        float y0 = screenY(camera, imageHeight, arrow.fromY());
        float x1 = screenX(camera, arrow.toX());
        float y1 = screenY(camera, imageHeight, arrow.toY());
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1f) return;
        float ux = dx / length;
        float uy = dy / length;
        // Sized off the map's own scale so the head reads on a 560-cell frame,
        // with a pixel floor so it does not vanish on a small one.
        float head = Math.max(14f, Math.min(40f,
                Math.max(camera.cellPxSize() * 3.5f, length * 0.02f)));
        float halfWidth = head * 0.42f;
        float shaftEndX = x1 - ux * head;
        float shaftEndY = y1 - uy * head;

        graphics.setStroke(new BasicStroke(arrow.style().strokeWidth + 2.2f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(HALO);
        graphics.drawLine(Math.round(x0), Math.round(y0),
                Math.round(shaftEndX), Math.round(shaftEndY));
        graphics.setStroke(new BasicStroke(arrow.style().strokeWidth,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(arrow.style().color);
        graphics.drawLine(Math.round(x0), Math.round(y0),
                Math.round(shaftEndX), Math.round(shaftEndY));

        int[] xs = {
                Math.round(x1),
                Math.round(shaftEndX - uy * halfWidth),
                Math.round(shaftEndX + uy * halfWidth)};
        int[] ys = {
                Math.round(y1),
                Math.round(shaftEndY + ux * halfWidth),
                Math.round(shaftEndY - ux * halfWidth)};
        graphics.setColor(arrow.style().color);
        graphics.fillPolygon(xs, ys, 3);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(HALO);
        graphics.drawPolygon(xs, ys, 3);

        if (arrow.text().isEmpty()) return;
        int midX = Math.round((x0 + x1) / 2f);
        int midY = Math.round((y0 + y1) / 2f);
        int plateHeight = plateHeight(metrics);
        words.add(new PendingLabel(arrow.text(), arrow.style(),
                plateFor(metrics, arrow.text(),
                        midX - textWidth(metrics, arrow.text()) / 2,
                        midY - plateHeight - 4),
                new int[]{midY + 4, midY - 2 * plateHeight - 6}));
    }

    private static void paintLabel(Graphics2D graphics, BattleCamera camera,
                                   int imageHeight, FontMetrics metrics,
                                   List<PendingLabel> words,
                                   ReviewAnnotation.Label label) {
        int x = Math.round(screenX(camera, label.cellX() + 0.5f));
        int y = Math.round(screenY(camera, imageHeight, label.cellY() + 0.5f));
        int plateHeight = plateHeight(metrics);
        words.add(new PendingLabel(label.text(), label.style(),
                plateFor(metrics, label.text(),
                        x - textWidth(metrics, label.text()) / 2,
                        y - plateHeight - 3),
                new int[]{y + 3, y - 2 * plateHeight - 6}));
    }

    /**
     * The first candidate position that collides with nothing already drawn,
     * or the preferred one when they all do.
     *
     * <p>Every alternative stays within a plate's height of the mark it
     * belongs to, so a label pushed off a neighbour is still obviously
     * attached to its own box. Two labels on top of each other is one label;
     * a label that has wandered somewhere clear belongs to nothing.
     */
    private static Rectangle place(Rectangle preferred, List<Rectangle> placed,
                                   int imageWidth, int imageHeight,
                                   int[] alternativeTops) {
        Rectangle first = clampToFrame(preferred, imageWidth, imageHeight);
        if (isClear(first, placed)) {
            placed.add(first);
            return first;
        }
        for (int top : alternativeTops) {
            Rectangle candidate = clampToFrame(
                    new Rectangle(preferred.x, top, preferred.width, preferred.height),
                    imageWidth, imageHeight);
            if (isClear(candidate, placed)) {
                placed.add(candidate);
                return candidate;
            }
        }
        placed.add(first);
        return first;
    }

    private static boolean isClear(Rectangle candidate, List<Rectangle> placed) {
        for (Rectangle taken : placed) {
            if (taken.intersects(candidate)) return false;
        }
        return true;
    }

    private static Rectangle plateFor(FontMetrics metrics, String text, int left, int top) {
        return new Rectangle(left - PLATE_PAD_X, top,
                textWidth(metrics, text) + 2 * PLATE_PAD_X, plateHeight(metrics));
    }

    private static int textWidth(FontMetrics metrics, String text) {
        return metrics.stringWidth(text);
    }

    private static int plateHeight(FontMetrics metrics) {
        return metrics.getAscent() + metrics.getDescent() + 2 * PLATE_PAD_Y;
    }

    private static void drawPlate(Graphics2D graphics, FontMetrics metrics,
                                  String text, ReviewStyle style, Rectangle plate) {
        graphics.setColor(PLATE_FILL);
        graphics.fillRoundRect(plate.x, plate.y, plate.width, plate.height, 5, 5);
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(style.at(150));
        graphics.drawRoundRect(plate.x, plate.y, plate.width, plate.height, 5, 5);
        graphics.setColor(style.color);
        graphics.drawString(text, plate.x + PLATE_PAD_X,
                plate.y + PLATE_PAD_Y + metrics.getAscent());
    }
}
