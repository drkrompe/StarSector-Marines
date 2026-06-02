package com.dillon.starsectormarines.battle.ui.highlight;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared cell-highlight state for overlays on the battle map. Multiple "sources"
 * write into their own slot keyed by string id and are rendered together:
 * the selected squad (a production selection cue, fed by
 * {@code SelectionHighlightPublisher}), plus debug sources (the GOAP panel's
 * per-step action cells, a captain badge). Sources don't stomp each other —
 * clearing one source leaves the others intact.
 *
 * <p>Pure state holder: the GL was hoisted out to {@code HighlightRenderer}
 * ({@code ops.battleview}), which drains {@link #sourceLists()} into
 * {@code SOLID_RECT} + {@code LINE} commands. Keeping the draw out of here avoids
 * an {@code ops.battleview} package cycle and leaves this in the UI tier where
 * the publishers live.
 *
 * <p>Iteration order is insertion order ({@link LinkedHashMap}) — sources
 * pushed later paint on top. Callers that want explicit layering should push
 * in the order they want drawn back-to-front (selected unit > captain > action
 * cells is a sensible default).
 *
 * <p>Lifecycle: panels rebuild their source's list every frame in update()
 * and consume in render() — same per-frame snapshot pattern the rest of the
 * HUD uses. {@link #clear(String)} drops a single source; {@link #clearAll()}
 * resets everything (e.g. on battle teardown).
 */
public final class HighlightOverlay {

    /** Source ids the in-tree panels publish under. New consumers should add their own constants here. */
    public static final String SRC_ACTION_CELLS    = "action-cells";
    public static final String SRC_SELECTED_SQUAD  = "selected-squad";
    public static final String SRC_CAPTAIN         = "captain";

    /** Suggested palette so unrelated sources don't visually collide. */
    public static final Color COLOR_ACTION_CELLS   = new Color(0x40, 0xE0, 0xFF, 0xFF);  // cyan
    public static final Color COLOR_SELECTED_UNIT  = new Color(0x80, 0xFF, 0x80, 0xFF);  // green
    public static final Color COLOR_CAPTAIN        = new Color(0xFF, 0xD0, 0x40, 0xFF);  // gold

    private final Map<String, List<CellHighlight>> sources = new LinkedHashMap<>();

    /**
     * Replaces the highlight list under {@code sourceId}. Pass an empty list
     * (or call {@link #clear(String)}) to drop the source's marks.
     */
    public void put(String sourceId, List<CellHighlight> cells) {
        if (cells == null || cells.isEmpty()) {
            sources.remove(sourceId);
            return;
        }
        sources.put(sourceId, new ArrayList<>(cells));
    }

    public void clear(String sourceId) {
        sources.remove(sourceId);
    }

    public void clearAll() {
        sources.clear();
    }

    public boolean hasSource(String sourceId) {
        return sources.containsKey(sourceId);
    }

    /** True iff at least one source has highlights to draw. Renderer can skip the GL state setup when false. */
    public boolean isEmpty() {
        for (List<CellHighlight> v : sources.values()) {
            if (!v.isEmpty()) return false;
        }
        return true;
    }

    /**
     * The per-source highlight lists in insertion (= paint) order — the read
     * side {@code HighlightRenderer} drains into draw commands. Live view over
     * the backing map; the renderer only iterates it within a frame.
     */
    public Collection<List<CellHighlight>> sourceLists() {
        return sources.values();
    }
}
