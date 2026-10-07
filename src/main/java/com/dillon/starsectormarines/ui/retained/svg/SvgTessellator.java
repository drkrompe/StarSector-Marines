package com.dillon.starsectormarines.ui.retained.svg;

import com.dillon.starsectormarines.ui.retained.CanvasMesh;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Converts normalized fill areas, including holes, into disjoint triangles once at load. */
final class SvgTessellator {

    private SvgTessellator() {
    }

    static CanvasMesh mesh(Shape shape, double flatness, Rectangle2D viewport) {
        Area area = new Area(shape);
        area.intersect(new Area(viewport));
        List<Edge> edges = new ArrayList<>();
        TreeSet<Double> levels = new TreeSet<>();
        PathIterator path = area.getPathIterator(null, flatness);
        double[] point = new double[6];
        double x = 0, y = 0, startX = 0, startY = 0;
        while (!path.isDone()) {
            switch (path.currentSegment(point)) {
                case PathIterator.SEG_MOVETO -> {
                    x = startX = point[0];
                    y = startY = point[1];
                }
                case PathIterator.SEG_LINETO -> {
                    edge(edges, levels, x, y, point[0], point[1]);
                    x = point[0];
                    y = point[1];
                }
                case PathIterator.SEG_CLOSE -> edge(edges, levels, x, y, startX, startY);
                default -> throw new IllegalStateException("SVG flattening left a curve");
            }
            path.next();
        }
        List<Float> triangles = new ArrayList<>();
        List<Double> rows = new ArrayList<>(levels);
        List<Edge> active = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            double top = rows.get(i - 1);
            double bottom = rows.get(i);
            // Area may split a join at adjacent double values. Such a band
            // has no interior double midpoint and collapses to zero height
            // in our float mesh, so it cannot contribute any painted area.
            if ((float) top == (float) bottom) continue;
            double middle = top + (bottom - top) / 2;
            active.clear();
            for (Edge edge : edges) {
                // Every boundary level is in rows, so an edge is active only
                // when it spans this complete band. Membership need not
                // depend on the rounding of a sampled midpoint.
                if (edge.lowY() <= top && edge.highY() >= bottom) active.add(edge);
            }
            active.sort(Comparator.comparingDouble(edge -> edge.x(middle)));
            if (active.size() % 2 != 0) {
                throw new IllegalArgumentException("SVG fill has an unmatched boundary");
            }
            for (int e = 0; e < active.size(); e += 2) {
                Edge left = active.get(e);
                Edge right = active.get(e + 1);
                triangle(triangles, left.x(top), top, right.x(top), top, right.x(bottom), bottom);
                triangle(triangles, left.x(top), top, right.x(bottom), bottom, left.x(bottom), bottom);
            }
        }
        float[] coordinates = new float[triangles.size()];
        for (int i = 0; i < coordinates.length; i++) coordinates[i] = triangles.get(i);
        return new CanvasMesh(coordinates);
    }

    private static void edge(List<Edge> edges, TreeSet<Double> levels,
                             double x0, double y0, double x1, double y1) {
        if (!Double.isFinite(x0) || !Double.isFinite(y0)
                || !Double.isFinite(x1) || !Double.isFinite(y1)) {
            throw new IllegalArgumentException("SVG geometry must be finite");
        }
        if (y0 == y1) return;
        edges.add(new Edge(x0, y0, x1, y1));
        levels.add(y0);
        levels.add(y1);
    }

    private static void triangle(List<Float> result, double x0, double y0,
                                 double x1, double y1, double x2, double y2) {
        if ((x1 - x0) * (y2 - y0) == (x2 - x0) * (y1 - y0)) return;
        result.add((float) x0);
        result.add((float) y0);
        result.add((float) x1);
        result.add((float) y1);
        result.add((float) x2);
        result.add((float) y2);
    }

    private record Edge(double x0, double y0, double x1, double y1) {
        double lowY() { return Math.min(y0, y1); }
        double highY() { return Math.max(y0, y1); }
        double x(double y) { return x0 + (y - y0) * (x1 - x0) / (y1 - y0); }
    }
}
