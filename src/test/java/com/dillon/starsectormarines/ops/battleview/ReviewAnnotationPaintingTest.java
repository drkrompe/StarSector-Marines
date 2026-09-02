package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an annotation lands in the image.
 *
 * <p>An annotation is only worth drawing if it sits on the ground it names,
 * so this asks the projection directly rather than looking at a rendered
 * frame: a box a cell out and a box flipped about the map's waist look
 * equally plausible at 2240 pixels wide, and only one of them is drawn on the
 * keep.
 */
class ReviewAnnotationPaintingTest {

    private static final int GRID_W = 100;
    private static final int GRID_H = 50;
    private static final int IMAGE_W = 1000;
    /** 500 of map under the 30px caption band, so one cell is exactly 10px. */
    private static final int IMAGE_H = 530;

    private static BattleCamera camera() {
        return BattleReviewFrameRenderer.cameraFor(IMAGE_W, IMAGE_H, GRID_W, GRID_H);
    }

    @Test
    void projectsCellsToTheSamePixelsTheUnitMarkersUse() {
        BattleCamera camera = camera();

        // The marker code's own expression, which is what an annotation has to
        // agree with cell for cell.
        assertEquals(camera.cellToScreenX(10.5f),
                ReviewAnnotationPainter.screenX(camera, 10.5f), 0.001f);
        assertEquals(IMAGE_H - camera.cellToScreenY(10.5f),
                ReviewAnnotationPainter.screenY(camera, IMAGE_H, 10.5f), 0.001f);

        // And the absolute pixels, so a flip that keeps the two expressions
        // agreeing with each other still fails here.
        assertEquals(105f, ReviewAnnotationPainter.screenX(camera, 10.5f), 0.001f);
        assertEquals(485f, ReviewAnnotationPainter.screenY(camera, IMAGE_H, 4.5f), 0.001f);
        assertEquals(905f, ReviewAnnotationPainter.screenX(camera, 90.5f), 0.001f);
        assertEquals(75f, ReviewAnnotationPainter.screenY(camera, IMAGE_H, 45.5f), 0.001f);
    }

    @Test
    void worldYIsUpSoAHigherCellDrawsHigherInTheImage() {
        BattleCamera camera = camera();
        assertTrue(ReviewAnnotationPainter.screenY(camera, IMAGE_H, 40f)
                < ReviewAnnotationPainter.screenY(camera, IMAGE_H, 10f));
    }

    @Test
    void anArrowRunsBetweenTheTwoCellCentresItNames() {
        BattleCamera camera = camera();
        ReviewAnnotation.Arrow arrow = new ReviewAnnotation.Arrow(
                10.5f, 4.5f, 90.5f, 45.5f, "approach", ReviewStyle.APPROACH);

        assertEquals(105f, ReviewAnnotationPainter.screenX(camera, arrow.fromX()), 0.001f);
        assertEquals(485f, ReviewAnnotationPainter.screenY(camera, IMAGE_H, arrow.fromY()), 0.001f);
        assertEquals(905f, ReviewAnnotationPainter.screenX(camera, arrow.toX()), 0.001f);
        assertEquals(75f, ReviewAnnotationPainter.screenY(camera, IMAGE_H, arrow.toY()), 0.001f);
    }

    @Test
    void aLabelThatWouldLeaveTheFrameIsNudgedBackInside() {
        Rectangle offRight = ReviewAnnotationPainter.clampToFrame(
                new Rectangle(985, 200, 60, 18), IMAGE_W, IMAGE_H);
        assertTrue(offRight.x + offRight.width <= IMAGE_W,
                "label ran off the right edge: " + offRight);
        assertEquals(60, offRight.width, "clamping must not shrink the text");

        Rectangle offLeft = ReviewAnnotationPainter.clampToFrame(
                new Rectangle(-20, 200, 60, 18), IMAGE_W, IMAGE_H);
        assertTrue(offLeft.x >= 0, "label ran off the left edge: " + offLeft);

        Rectangle offBottom = ReviewAnnotationPainter.clampToFrame(
                new Rectangle(100, IMAGE_H - 4, 60, 18), IMAGE_W, IMAGE_H);
        assertTrue(offBottom.y + offBottom.height <= IMAGE_H,
                "label ran off the bottom edge: " + offBottom);
    }

    @Test
    void aLabelNeverHidesUnderTheCaptionBand() {
        Rectangle underBand = ReviewAnnotationPainter.clampToFrame(
                new Rectangle(100, 2, 60, 18), IMAGE_W, IMAGE_H);
        assertTrue(underBand.y >= BattleReviewFrameRenderer.HEADER_HEIGHT,
                "label was placed under the caption band: " + underBand);
    }

    @Test
    void aLabelThatAlreadyFitsIsLeftWhereItWas() {
        Rectangle inside = new Rectangle(400, 300, 60, 18);
        assertEquals(inside, ReviewAnnotationPainter.clampToFrame(inside, IMAGE_W, IMAGE_H));
    }

    @Test
    void typeScalesWithTheFrameButNeverBelowTheFloor() {
        assertEquals(ReviewAnnotationPainter.MIN_LABEL_POINTS,
                ReviewAnnotationPainter.labelPoints(960));
        assertTrue(ReviewAnnotationPainter.labelPoints(2240)
                > ReviewAnnotationPainter.labelPoints(960));
        assertTrue(ReviewAnnotationPainter.labelPoints(8000) <= 18,
                "a huge frame must not get billboard type");
    }

    @Test
    void aBoxSortsItsOwnCornersSoEitherRowOrderIsAccepted() {
        ReviewAnnotation.Box ascending = new ReviewAnnotation.Box(
                16, 26, 24, 34, "KEEP", ReviewStyle.KEEP);
        ReviewAnnotation.Box descending = new ReviewAnnotation.Box(
                24, 34, 16, 26, "KEEP", ReviewStyle.KEEP);
        assertEquals(ascending, descending);
        assertEquals(9, ascending.width());
        assertEquals(9, ascending.height());
    }

    @Test
    void aMarkWithNothingToSayOrNowhereToPointIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAnnotation.Label(1f, 1f, "  ", ReviewStyle.NOTE));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewAnnotation.Arrow(3f, 3f, 3f, 3f, "nowhere", ReviewStyle.APPROACH));
    }
}
