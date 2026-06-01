package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.BattleLayout;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
 * Top-of-screen debug panel — collapsible list of boolean toggles and
 * one-shot action rows. Replaces the prior {@code DebugTogglesWidget}
 * (which was a screen-level {@code BaseWidget} registered on the
 * {@code WidgetRoot}; it now joins the rest of the HUD via
 * {@link HudPanel}'s lifecycle, which makes input + render ordering
 * consistent with {@code TickProfileDebugPanel} et al).
 *
 * <p>Row kinds:
 * <ul>
 *   <li><b>Toggle</b> ({@link #addToggle}) — has a green / dark checkbox
 *       driven by the supplied {@link BooleanSupplier}. Click runs the
 *       supplied {@link Runnable} (typically a one-line lambda that flips
 *       a static field).</li>
 *   <li><b>Action</b> ({@link #addAction}) — no checkbox; the row reads
 *       as a clickable button. A trailing glyph signals it's an action,
 *       not state. Used for "force reinforcement"-style on-demand
 *       triggers.</li>
 * </ul>
 *
 * <p>Anchor is top-center, just below the top controls strip. Visibility
 * follows the panel itself rather than a config flag — the header bar
 * is small and always-on; expand only renders the body.
 */
@DebugOnly
public final class DebugTogglesPanel implements HudPanel {

    /** One row in the panel. {@code checkboxState == null} marks an action row (no checkbox, action glyph). */
    private static final class Row {
        final String label;
        final BooleanSupplier checkboxState;
        final Runnable onClick;

        Row(String label, BooleanSupplier checkboxState, Runnable onClick) {
            this.label = label;
            this.checkboxState = checkboxState;
            this.onClick = onClick;
        }
    }

    private static final float PANEL_W   = 220f;
    private static final float HEADER_H  = 24f;
    private static final float ROW_H     = 22f;
    private static final float CHECK_W   = 14f;
    private static final float PADDING   = 8f;

    private static final Color HEADER_TEXT = new Color(230, 230, 230);
    private static final Color ROW_TEXT    = new Color(200, 210, 220);
    private static final Color ACTION_TEXT = new Color(220, 200, 150);

    private final BattleUiContext ctx;
    private final BitmapFont font;
    private final List<Row> rows = new ArrayList<>();

    private boolean expanded;
    /** -1 = header hovered; ≥0 = row index hovered; -2 = nothing hovered. */
    private int hoverIdx = -2;

    /** Cached this-frame button hotspots — set by {@link #render} so the same-frame input pass reads fresh geometry. */
    private float curX, curY;

    public DebugTogglesPanel(BattleUiContext ctx) {
        this.ctx = ctx;
        this.font = Fonts.ORBITRON_20;
    }

    /** Register a boolean toggle row. Returns {@code this} for chained registration. */
    public DebugTogglesPanel addToggle(String label, BooleanSupplier getter, Runnable toggle) {
        rows.add(new Row(label, getter, toggle));
        return this;
    }

    /** Register a one-shot action row — runs {@code action} on click. No checkbox; rendered as a button-style row. */
    public DebugTogglesPanel addAction(String label, Runnable action) {
        rows.add(new Row(label, null, action));
        return this;
    }

    @Override
    public boolean isVisible() {
        return ctx.getLayout() != null;
    }

    @Override
    public void update(float dt) { /* no per-frame state */ }

    @Override
    public void render(float alphaMult) {
        font.ensureLoaded();
        float x = panelX();
        float y = panelY();
        float totalH = computeHeight();
        curX = x;
        curY = y;

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Body fill (only meaningful when expanded — render() draws the
        // header band over it either way so we get consistent borders).
        glColor4f(0.08f, 0.10f, 0.14f, 0.85f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,           y);
        glVertex2f(x + PANEL_W, y);
        glVertex2f(x + PANEL_W, y + totalH);
        glVertex2f(x,           y + totalH);
        glEnd();

        // Header strip — slightly brighter; hover bumps it.
        float hr = 0.16f, hg = 0.22f, hb = 0.30f;
        if (hoverIdx == -1) { hr = 0.30f; hg = 0.42f; hb = 0.58f; }
        glColor4f(hr, hg, hb, 0.95f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,           y + totalH - HEADER_H);
        glVertex2f(x + PANEL_W, y + totalH - HEADER_H);
        glVertex2f(x + PANEL_W, y + totalH);
        glVertex2f(x,           y + totalH);
        glEnd();

        // Row backgrounds + checkboxes (only when expanded).
        if (expanded) {
            for (int i = 0; i < rows.size(); i++) {
                float ry = y + totalH - HEADER_H - (i + 1) * ROW_H;

                if (i == hoverIdx) {
                    glColor4f(0.20f, 0.28f, 0.40f, 0.7f * alphaMult);
                    glBegin(GL_QUADS);
                    glVertex2f(x,           ry);
                    glVertex2f(x + PANEL_W, ry);
                    glVertex2f(x + PANEL_W, ry + ROW_H);
                    glVertex2f(x,           ry + ROW_H);
                    glEnd();
                }

                Row r = rows.get(i);
                if (r.checkboxState != null) {
                    drawCheckbox(x + PANEL_W - CHECK_W - PADDING,
                            ry + (ROW_H - CHECK_W) / 2f,
                            r.checkboxState.getAsBoolean(), alphaMult);
                }
            }
        }

        // Outer border.
        glColor4f(0.55f, 0.65f, 0.80f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,           y);
        glVertex2f(x + PANEL_W, y);
        glVertex2f(x + PANEL_W, y + totalH);
        glVertex2f(x,           y + totalH);
        glEnd();

        // Header separator when expanded.
        if (expanded) {
            glBegin(GL_LINES);
            glVertex2f(x,           y + totalH - HEADER_H);
            glVertex2f(x + PANEL_W, y + totalH - HEADER_H);
            glEnd();
        }

        // Text labels.
        String headerText = "DEBUG " + (expanded ? "-" : "+");
        font.drawString(headerText, x + PADDING, y + totalH - 4f, HEADER_TEXT, alphaMult);
        if (expanded) {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                float ry = y + totalH - HEADER_H - (i + 1) * ROW_H;
                Color textColor = (r.checkboxState != null) ? ROW_TEXT : ACTION_TEXT;
                font.drawString(r.label, x + PADDING, ry + ROW_H - 4f, textColor, alphaMult);
                if (r.checkboxState == null) {
                    // Trailing chevron for action rows — distinguishes them
                    // from toggle rows at a glance and stands in for the
                    // missing checkbox.
                    font.drawString(">>", x + PANEL_W - CHECK_W - PADDING + 1f,
                            ry + ROW_H - 4f, textColor, alphaMult);
                }
            }
        }
    }

    private void drawCheckbox(float cx, float cy, boolean on, float alphaMult) {
        if (on) glColor4f(0.30f, 0.85f, 0.40f, 0.95f * alphaMult);
        else    glColor4f(0.12f, 0.15f, 0.20f, 0.95f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(cx,            cy);
        glVertex2f(cx + CHECK_W,  cy);
        glVertex2f(cx + CHECK_W,  cy + CHECK_W);
        glVertex2f(cx,            cy + CHECK_W);
        glEnd();
        glColor4f(0.55f, 0.65f, 0.80f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(cx,            cy);
        glVertex2f(cx + CHECK_W,  cy);
        glVertex2f(cx + CHECK_W,  cy + CHECK_W);
        glVertex2f(cx,            cy + CHECK_W);
        glEnd();
    }

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        if (!isVisible()) return;

        float totalH = computeHeight();
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            int px = e.getX();
            int py = e.getY();
            boolean inside = px >= curX && px < curX + PANEL_W
                    && py >= curY && py < curY + totalH;

            if (e.isMouseMoveEvent()) {
                if (!inside) {
                    hoverIdx = -2;
                    continue;
                }
                int rowIdx = rowAt(py, totalH);
                hoverIdx = rowIdx;
                continue;
            }

            if (!e.isLMBDownEvent()) continue;
            if (!inside) continue;
            int rowIdx = rowAt(py, totalH);
            if (rowIdx == -1) {
                expanded = !expanded;
                e.consume();
                continue;
            }
            if (rowIdx >= 0 && expanded) {
                rows.get(rowIdx).onClick.run();
                e.consume();
            }
        }
    }

    /** {@code -1} = header band; {@code [0, rows.size())} = expanded row index; {@code -2} = body but no row (impossible when expanded). */
    private int rowAt(int py, float totalH) {
        float headerTop = curY + totalH;
        float headerBottom = headerTop - HEADER_H;
        if (py >= headerBottom && py < headerTop) return -1;
        if (!expanded) return -2;
        float rowsTop = headerBottom;
        if (py >= rowsTop || py < curY) return -2;
        int idx = (int) ((rowsTop - py) / ROW_H);
        if (idx < 0 || idx >= rows.size()) return -2;
        return idx;
    }

    private float computeHeight() {
        return HEADER_H + (expanded ? rows.size() * ROW_H : 0f);
    }

    /** Top-center under the controls strip — mirror of TickProfileDebugPanel's top-left anchor, just horizontally centered on the grid. */
    private float panelX() {
        BattleLayout l = ctx.getLayout();
        return l.gridX + (l.gridW - PANEL_W) / 2f;
    }

    /** Y of the panel's BOTTOM edge — render() builds upward from this. */
    private float panelY() {
        BattleLayout l = ctx.getLayout();
        return l.controlsY - BattleLayout.CONTROLS_GAP - computeHeight();
    }
}
