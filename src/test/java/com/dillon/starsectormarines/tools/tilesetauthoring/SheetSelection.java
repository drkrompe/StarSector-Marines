package com.dillon.starsectormarines.tools.tilesetauthoring;

import java.awt.Rectangle;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Which pieces of a sheet are picked, and how clicking changes that.
 *
 * <p>Separate from the canvas that draws it because this is the part with rules.
 * Whether a click replaces the selection or adds to it, what a shift-click
 * measures a run from, and whether a dragged box clears what was there are
 * decisions a person builds a habit around, and a habit that is wrong once in
 * ten uses is worse than no habit. None of it is visible to a test while it
 * lives inside a component's mouse handler.
 *
 * <p>Indices, not entries: the table beside the canvas addresses rows by index
 * and the two have to agree. An entry-keyed selection would also quietly
 * survive a re-slice that replaced the very pieces it referred to.
 */
final class SheetSelection {

    private final SortedSet<Integer> selected = new TreeSet<>();
    /** Where a shift-click measures its run from; -1 when there is nothing to measure from. */
    private int anchor = -1;

    boolean contains(int index) {
        return selected.contains(index);
    }

    boolean isEmpty() {
        return selected.isEmpty();
    }

    int size() {
        return selected.size();
    }

    int[] toArray() {
        int[] rows = new int[selected.size()];
        int at = 0;
        for (int row : selected) rows[at++] = row;
        return rows;
    }

    void clear() {
        selected.clear();
        anchor = -1;
    }

    /**
     * Replace the selection wholesale, as when the table is the one that changed.
     *
     * <p>Leaves the anchor alone deliberately: a selection arriving from
     * elsewhere says nothing about where the next shift-click should measure
     * from, and moving it would make a run jump to somewhere the person never
     * clicked.
     */
    void set(int[] rows) {
        selected.clear();
        for (int row : rows) selected.add(row);
    }

    /**
     * A click landing on {@code hit}, or on nothing when it is negative.
     *
     * @param toggle the modifier that adds or removes one piece
     * @param extend the modifier that runs from the anchor to here
     */
    void click(int hit, boolean toggle, boolean extend) {
        if (hit < 0) {
            // Clicking the backdrop clears, unless a modifier says the gesture
            // was meant to be additive and simply missed.
            if (!toggle && !extend) clear();
            return;
        }
        if (extend && anchor >= 0) {
            for (int i = Math.min(anchor, hit); i <= Math.max(anchor, hit); i++) {
                selected.add(i);
            }
            return;
        }
        if (toggle) {
            if (!selected.remove(hit)) selected.add(hit);
            anchor = hit;
            return;
        }
        selected.clear();
        selected.add(hit);
        anchor = hit;
    }

    /** Everything the dragged box touches. Intersecting, not containing: a box drawn
     * across a row of cells is meant to take them, not only the ones it swallowed whole. */
    void band(List<TilesetExport.Entry> entries, Rectangle area, boolean additive) {
        if (!additive) selected.clear();
        for (int i = 0; i < entries.size(); i++) {
            SheetSlicer.Piece piece = entries.get(i).piece;
            if (area.intersects(piece.x(), piece.y(), piece.width(), piece.height())) {
                selected.add(i);
            }
        }
        if (!selected.isEmpty()) anchor = selected.first();
    }
}
