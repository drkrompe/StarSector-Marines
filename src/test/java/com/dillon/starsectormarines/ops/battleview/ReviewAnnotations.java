package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An immutable set of marks for one review frame.
 *
 * <p>A wrapper rather than a bare {@code List} so a renderer signature says
 * what the list is for, and so {@link #NONE} is a value a caller can pass
 * rather than a null the renderer has to guard.
 */
public final class ReviewAnnotations implements Iterable<ReviewAnnotation> {

    /** Nothing to draw. What every existing caller renders with. */
    public static final ReviewAnnotations NONE = new ReviewAnnotations(List.of());

    private final List<ReviewAnnotation> marks;

    private ReviewAnnotations(List<ReviewAnnotation> marks) {
        this.marks = List.copyOf(marks);
    }

    public static ReviewAnnotations of(ReviewAnnotation... marks) {
        return marks == null || marks.length == 0 ? NONE
                : new ReviewAnnotations(List.of(marks));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** In authoring order, which is drawing order. */
    public List<ReviewAnnotation> marks() {
        return marks;
    }

    public boolean isEmpty() {
        return marks.isEmpty();
    }

    public int size() {
        return marks.size();
    }

    @Override
    public Iterator<ReviewAnnotation> iterator() {
        return marks.iterator();
    }

    /** Accumulates marks in the order they should be drawn. */
    public static final class Builder {

        private final List<ReviewAnnotation> marks = new ArrayList<>();

        private Builder() { }

        public Builder add(ReviewAnnotation mark) {
            if (mark != null) marks.add(mark);
            return this;
        }

        public Builder label(float cellX, float cellY, String text, ReviewStyle style) {
            return add(new ReviewAnnotation.Label(cellX, cellY, text, style));
        }

        public Builder arrow(float fromX, float fromY, float toX, float toY,
                             String text, ReviewStyle style) {
            return add(new ReviewAnnotation.Arrow(fromX, fromY, toX, toY, text, style));
        }

        /**
         * A run of cells drawn as a connected line. Fewer than two points is
         * not a line and is dropped rather than refused, so a caller may hand
         * over whatever route it was given.
         */
        public Builder polyline(List<ReviewAnnotation.Point> points, String text,
                                ReviewStyle style) {
            if (points == null || points.size() < 2) return this;
            return add(new ReviewAnnotation.Polyline(points, text, style));
        }

        public Builder box(int left, int bottom, int right, int top,
                           String text, ReviewStyle style) {
            return add(new ReviewAnnotation.Box(left, bottom, right, top, text, style));
        }

        public ReviewAnnotations build() {
            return marks.isEmpty() ? NONE : new ReviewAnnotations(marks);
        }
    }
}
