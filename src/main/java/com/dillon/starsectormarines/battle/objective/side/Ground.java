package com.dillon.starsectormarines.battle.objective.side;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A place a side objective reads, in map cells: a yard to reach, a junction to hold, a drop to carry to. It also
 * names the cell its beacon and Locate point at and the outline the map draws round it.
 */
public interface Ground {
    boolean contains(int x, int y);

    /** The member cell the beacon stands on. */
    Cell anchor();

    /** The member cells with a four-neighbour outside: the outline a beacon draws. */
    List<Cell> outline();

    /** A ground of these cells; never empty. Its anchor is the member cell nearest the centroid. */
    static Ground of(Collection<Cell> cells) {
        if (cells == null || cells.isEmpty()) throw new IllegalArgumentException("a ground needs at least one cell");
        Set<Cell> set = Set.copyOf(cells);
        double sx = 0, sy = 0;
        for (Cell c : set) { sx += c.x(); sy += c.y(); }
        double cx = sx / set.size(), cy = sy / set.size();
        Cell best = null;
        double bestD = Double.MAX_VALUE;
        for (Cell c : set) {
            double d = (c.x() - cx) * (c.x() - cx) + (c.y() - cy) * (c.y() - cy);
            if (d < bestD || d == bestD && (c.y() < best.y() || c.y() == best.y() && c.x() < best.x())) { bestD = d; best = c; }
        }
        List<Cell> outline = new ArrayList<>();
        for (Cell c : set)
            if (!set.contains(new Cell(c.x() + 1, c.y())) || !set.contains(new Cell(c.x() - 1, c.y()))
                    || !set.contains(new Cell(c.x(), c.y() + 1)) || !set.contains(new Cell(c.x(), c.y() - 1))) outline.add(c);
        outline.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y()) : Integer.compare(a.x(), b.x()));
        Cell anchor = best;
        List<Cell> edge = List.copyOf(outline);
        return new Ground() {
            @Override public boolean contains(int x, int y) { return set.contains(new Cell(x, y)); }
            @Override public Cell anchor() { return anchor; }
            @Override public List<Cell> outline() { return edge; }
        };
    }
}
