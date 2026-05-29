package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Shared cell-highlight state for debug overlays on the battle map. Multiple
 * "sources" (the GOAP debug panel publishing per-step action cells, the unit
 * selector showing the picked squad, a future captain badge) write into their
 * own slot keyed by string id and are rendered together. Sources don't stomp
 * each other — clearing one source leaves the others intact.
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

    /** Fill alpha applied on top of the source's color (which is typically opaque in the palette). 64/255 reads against most terrain without obscuring the underlying tile. */
    private static final int FILL_ALPHA   = 0x40;
    private static final int OUTLINE_ALPHA = 0xE0;
    private static final float OUTLINE_WIDTH = 1.5f;

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
     * Draws all highlights as a single pass. Two sub-passes per source — fills
     * (translucent) then outlines (more opaque) — so the cells read as crisp
     * marks rather than vague tints. Source insertion order = paint order.
     *
     * <p>Caller is responsible for the surrounding scissor / matrix state; this
     * method only sets GL state it owns (texture off, blend on, blend func).
     */
    public void render(BattleCamera camera, float alphaMult) {
        if (camera == null || isEmpty()) return;
        float cell = camera.cellPxSize();
        if (cell <= 0f) return;

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Pass 1 — fills.
        glBegin(GL_QUADS);
        for (List<CellHighlight> list : sources.values()) {
            for (CellHighlight h : list) {
                float x0 = camera.cellToScreenX(h.cellX);
                float y0 = camera.cellToScreenY(h.cellY);
                float x1 = x0 + cell;
                float y1 = y0 + cell;
                glColor4f(h.color.getRed() / 255f, h.color.getGreen() / 255f,
                        h.color.getBlue() / 255f, FILL_ALPHA / 255f * alphaMult);
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
            }
        }
        glEnd();

        // Pass 2 — outlines. One LINE_LOOP per cell so the per-quad color can
        // change. Cheaper alternatives (textured 9-slice border) aren't worth
        // the complexity for a debug overlay capped at a few hundred cells.
        glLineWidth(OUTLINE_WIDTH);
        for (List<CellHighlight> list : sources.values()) {
            for (CellHighlight h : list) {
                float x0 = camera.cellToScreenX(h.cellX);
                float y0 = camera.cellToScreenY(h.cellY);
                float x1 = x0 + cell;
                float y1 = y0 + cell;
                glColor4f(h.color.getRed() / 255f, h.color.getGreen() / 255f,
                        h.color.getBlue() / 255f, OUTLINE_ALPHA / 255f * alphaMult);
                glBegin(GL_LINE_LOOP);
                glVertex2f(x0, y0);
                glVertex2f(x1, y0);
                glVertex2f(x1, y1);
                glVertex2f(x0, y1);
                glEnd();
            }
        }
    }
}
