package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.util.List;

/**
 * World-layer producer for the cell-highlight overlay ({@code RenderLayer.HIGHLIGHTS}).
 * Reads the shared {@link HighlightOverlay}'s source lists and emits commands:
 * a translucent {@code SOLID_RECT} fill per cell, then a four-{@code LINE} outline
 * per cell, so the marks read as crisp cells rather than vague tints.
 *
 * <p><strong>Two ordered sub-passes</strong> — all fills first (across every
 * source, in insertion order), then all outlines — so outlines always sit on top
 * of fills. Submission order is paint order under the strict-painter drain;
 * {@code SOLID_RECT}s coalesce into one solid-batch flush and {@code LINE}s into
 * one line-batch flush (uniform width). Mirrors the former
 * {@code HighlightOverlay.render} immediate-mode pass exactly, now drained.
 *
 * <p>The overlay carries both a production source (selected-squad cells, fed by
 * {@code SelectionHighlightPublisher}) and debug sources (GOAP action cells,
 * captain badge — fed by the {@code @DebugOnly} squad-plan panel). This renderer
 * is source-agnostic; in a prod build only the production source is ever
 * populated.
 */
public final class HighlightRenderer {

    /** Fill alpha on top of the source color (palette colors are opaque). 64/255 reads against terrain without obscuring the tile. */
    private static final float FILL_ALPHA = 0x40 / 255f;
    private static final float OUTLINE_ALPHA = 0xE0 / 255f;
    private static final float OUTLINE_WIDTH = 1.5f;

    public void collect(HighlightOverlay overlay, BattleCamera camera, DrawList out, float alphaMult) {
        if (overlay == null || camera == null || overlay.isEmpty()) return;
        float cell = camera.cellPxSize();
        if (cell <= 0f) return;

        // Pass 1 — translucent fills.
        for (List<CellHighlight> list : overlay.sourceLists()) {
            for (CellHighlight h : list) {
                float x0 = camera.cellToScreenX(h.cellX);
                float y0 = camera.cellToScreenY(h.cellY);
                out.addSolidRect(RenderLayer.HIGHLIGHTS, x0, y0, x0 + cell, y0 + cell,
                        h.color.getRed() / 255f, h.color.getGreen() / 255f, h.color.getBlue() / 255f,
                        FILL_ALPHA * alphaMult);
            }
        }

        // Pass 2 — sharper outlines, four LINEs per cell (the former GL_LINE_LOOP).
        for (List<CellHighlight> list : overlay.sourceLists()) {
            for (CellHighlight h : list) {
                float x0 = camera.cellToScreenX(h.cellX);
                float y0 = camera.cellToScreenY(h.cellY);
                float x1 = x0 + cell;
                float y1 = y0 + cell;
                float r = h.color.getRed() / 255f, g = h.color.getGreen() / 255f, b = h.color.getBlue() / 255f;
                float a = OUTLINE_ALPHA * alphaMult;
                out.addLine(RenderLayer.HIGHLIGHTS, x0, y0, x1, y0, OUTLINE_WIDTH, r, g, b, a);
                out.addLine(RenderLayer.HIGHLIGHTS, x1, y0, x1, y1, OUTLINE_WIDTH, r, g, b, a);
                out.addLine(RenderLayer.HIGHLIGHTS, x1, y1, x0, y1, OUTLINE_WIDTH, r, g, b, a);
                out.addLine(RenderLayer.HIGHLIGHTS, x0, y1, x0, y0, OUTLINE_WIDTH, r, g, b, a);
            }
        }
    }
}
