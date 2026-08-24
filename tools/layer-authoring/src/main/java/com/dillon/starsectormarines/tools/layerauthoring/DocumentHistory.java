package com.dillon.starsectormarines.tools.layerauthoring;

import org.json.JSONException;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded document-memento history; one begin/commit pair is one user edit. */
final class DocumentHistory {

    private static final int MAX_ENTRIES = 100;

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private String pending;

    void begin(AuthoringDocument document) throws JSONException {
        if (pending == null) pending = document.snapshot();
    }

    boolean commit(AuthoringDocument document) throws JSONException {
        if (pending == null) return false;
        String before = pending;
        pending = null;
        if (before.equals(document.snapshot())) return false;
        undo.addLast(before);
        while (undo.size() > MAX_ENTRIES) undo.removeFirst();
        redo.clear();
        return true;
    }

    void cancel() {
        pending = null;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    AuthoringDocument undo(AuthoringDocument current) throws JSONException {
        if (!canUndo()) return current;
        pending = null;
        redo.addLast(current.snapshot());
        return AuthoringDocument.parse(current.projectRoot(), undo.removeLast());
    }

    AuthoringDocument redo(AuthoringDocument current) throws JSONException {
        if (!canRedo()) return current;
        pending = null;
        undo.addLast(current.snapshot());
        return AuthoringDocument.parse(current.projectRoot(), redo.removeLast());
    }

    void clear() {
        undo.clear();
        redo.clear();
        pending = null;
    }
}
