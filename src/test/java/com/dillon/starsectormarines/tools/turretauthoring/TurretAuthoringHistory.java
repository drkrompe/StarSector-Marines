package com.dillon.starsectormarines.tools.turretauthoring;

import org.json.JSONException;

import java.util.ArrayDeque;
import java.util.Deque;

/** Complete cross-catalog snapshots for undo/redo. */
final class TurretAuthoringHistory {

    private static final int LIMIT = 100;
    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();

    void record(String before, String after) {
        if (before.equals(after)) return;
        undo.addLast(before);
        while (undo.size() > LIMIT) undo.removeFirst();
        redo.clear();
    }

    boolean canUndo() { return !undo.isEmpty(); }

    boolean canRedo() { return !redo.isEmpty(); }

    void clear() {
        undo.clear();
        redo.clear();
    }

    void undo(TurretAuthoringDocument document) throws JSONException {
        String current = document.snapshot();
        String previous = undo.removeLast();
        document.restore(previous);
        redo.addLast(current);
    }

    void redo(TurretAuthoringDocument document) throws JSONException {
        String current = document.snapshot();
        String next = redo.removeLast();
        document.restore(next);
        undo.addLast(current);
    }
}
