package com.dillon.starsectormarines.battle.derelict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The claim lattice (`fallen-hull-nouns.md`, Vocabulary): points on a regular grid round the hull, laid by the ground
 * and never moved by play. A point links to the hull when it lies within {@link #linkCells} of one of its sections,
 * and to another point within {@code linkCells} of it (Chebyshev). The forge plants the nearest open point to the hull
 * that links back to it: to the hull itself, or to an established stake whose chain holds (an <em>anchor</em>).
 */
public final class ClaimLattice {

    /** The hull as a stake's parent: the root of every relay chain. */
    public static final int HULL = -1;

    /** One lattice point. */
    public record Point(int id, int x, int y) { }

    /** Where the forge plants next, and what the new stake's chain runs through first. */
    public record Plan(Point point, int parent) { }

    private final List<FallenHullSite.Rect> sections;
    private final List<Point> points;
    private final int linkCells;

    public ClaimLattice(FallenHullSite.Rect hull, List<Point> points, int linkCells) {
        this(List.of(hull), points, linkCells);
    }

    /** A lattice round a hull lying in several sections: a point links to the hull through the nearest. */
    public ClaimLattice(List<FallenHullSite.Rect> sections, List<Point> points, int linkCells) {
        if (linkCells < 1) throw new IllegalArgumentException("Lattice points link across at least a cell");
        if (sections.isEmpty()) throw new IllegalArgumentException("A hull lies in at least one section");
        this.sections = List.copyOf(sections);
        this.points = List.copyOf(points);
        this.linkCells = linkCells;
        for (int i = 0; i < this.points.size(); i++) {
            for (int j = i + 1; j < this.points.size(); j++) {
                if (this.points.get(i).id() == this.points.get(j).id()) throw new IllegalArgumentException("Duplicate point");
            }
        }
    }

    public List<Point> points() {
        return points;
    }

    public int linkCells() {
        return linkCells;
    }

    public Point point(int id) {
        for (Point p : points) if (p.id() == id) return p;
        return null;
    }

    /** Whether a point links straight to the hull. */
    public boolean linksToHull(Point p) {
        return FallenHullSite.Rect.nearestOf(sections, p.x(), p.y()).chebyshev(p.x(), p.y()) <= linkCells;
    }

    /**
     * How near a point lies to the hull, the order the forge plants in: the squared distance to the nearest cell of
     * any section, then (for points as near the plates) the squared distance to that section's middle.
     */
    public long hullDistance(Point p) {
        long best = Long.MAX_VALUE;
        for (FallenHullSite.Rect r : sections) {
            int[] at = r.nearest(p.x(), p.y());
            long ex = p.x() - at[0], ey = p.y() - at[1];
            long cx = p.x() - r.centreX(), cy = p.y() - r.centreY();
            long d = (ex * ex + ey * ey) * (1L << 32) + Math.min(cx * cx + cy * cy, (1L << 32) - 1);
            best = Math.min(best, d);
        }
        return best;
    }

    /** Whether two points link. */
    public boolean linked(Point a, Point b) {
        return a.id() != b.id() && Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())) <= linkCells;
    }

    /** The points a point links to, in id order. */
    public List<Point> neighbours(Point p) {
        List<Point> out = new ArrayList<>();
        for (Point q : points) if (linked(p, q)) out.add(q);
        out.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return Collections.unmodifiableList(out);
    }

    /**
     * The point the forge plants next.
     *
     * @param open    the points with no stake on them and no train bound for them
     * @param anchors the points whose stake is established and whose chain holds
     * @return the open point nearest the hull ({@link #hullDistance}) that links to the hull or an anchor (lower id on a tie), with
     *         its parent: the hull where it links to it, else the nearest anchor; null when none is plantable
     */
    public Plan nextPlantable(Set<Integer> open, Set<Integer> anchors) {
        Plan best = null;
        long bestD = Long.MAX_VALUE;
        for (Point p : points) {
            if (!open.contains(p.id())) continue;
            int parent = parentOf(p, anchors);
            if (parent == Integer.MIN_VALUE) continue;
            long d = hullDistance(p);
            if (d < bestD || (d == bestD && p.id() < best.point().id())) {
                best = new Plan(p, parent);
                bestD = d;
            }
        }
        return best;
    }

    /** The hull where {@code p} links to it, else the nearest anchor it links to; MIN_VALUE when neither. */
    private int parentOf(Point p, Set<Integer> anchors) {
        if (linksToHull(p)) return HULL;
        Point best = null;
        int bestD = Integer.MAX_VALUE;
        for (Point q : points) {
            if (!anchors.contains(q.id()) || !linked(p, q)) continue;
            int d = Math.max(Math.abs(p.x() - q.x()), Math.abs(p.y() - q.y()));
            if (d < bestD || (d == bestD && q.id() < best.id())) {
                best = q;
                bestD = d;
            }
        }
        return best == null ? Integer.MIN_VALUE : best.id();
    }
}
