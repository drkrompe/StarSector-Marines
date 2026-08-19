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
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

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
 * one-shot action rows, and live numeric dials. Replaces the prior {@code DebugTogglesWidget}
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
 *   <li><b>Dial</b> ({@link #addDial}) — draggable horizontal track with a
 *       live numeric value. Changes apply on every drag event.</li>
 * </ul>
 *
 * <p>Anchor is top-center, just below the top controls strip. Visibility
 * follows the panel itself rather than a config flag — the header bar
 * is small and always-on; expand only renders the body.
 */
@DebugOnly
public final class DebugTogglesPanel implements HudPanel {

    /** One row in the panel. Exactly one of checkbox, action, or dial is populated. */
    private static final class Row {
        final String label;
        final BooleanSupplier checkboxState;
        final Runnable onClick;
        final DoubleSupplier dialValue;
        final DoubleConsumer dialSetter;
        final double dialMin;
        final double dialMax;

        Row(String label, BooleanSupplier checkboxState, Runnable onClick) {
            this.label = label;
            this.checkboxState = checkboxState;
            this.onClick = onClick;
            this.dialValue = null;
            this.dialSetter = null;
            this.dialMin = 0;
            this.dialMax = 0;
        }

        Row(String label, DoubleSupplier dialValue, DoubleConsumer dialSetter,
            double dialMin, double dialMax) {
            this.label = label;
            this.checkboxState = null;
            this.onClick = null;
            this.dialValue = dialValue;
            this.dialSetter = dialSetter;
            this.dialMin = dialMin;
            this.dialMax = dialMax;
        }

        boolean isDial() { return dialValue != null; }
        boolean isAction() { return checkboxState == null && !isDial(); }
    }

    private static final float PANEL_W   = 280f;
    private static final float HEADER_H  = 24f;
    private static final float ROW_H     = 22f;
    private static final float CHECK_W   = 14f;
    private static final float PADDING   = 8f;
    private static final float DIAL_TRACK_FROM_RIGHT = 118f;
    private static final float DIAL_TRACK_TO_RIGHT   = 62f;

    private static final Color HEADER_TEXT = new Color(230, 230, 230);
    private static final Color ROW_TEXT    = new Color(200, 210, 220);
    private static final Color ACTION_TEXT = new Color(220, 200, 150);

    private final BattleUiContext ctx;
    private final BitmapFont font;
    private final List<Row> rows = new ArrayList<>();

    private boolean expanded;
    /** -1 = header hovered; ≥0 = row index hovered; -2 = nothing hovered. */
    private int hoverIdx = -2;
    /** Row being live-dragged, or -1. */
    private int draggingDial = -1;

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

    /** Register a draggable live numeric value. */
    public DebugTogglesPanel addDial(String label, DoubleSupplier getter, DoubleConsumer setter,
                                     double min, double max) {
        if (!(max > min)) throw new IllegalArgumentException("dial max must be greater than min");
        rows.add(new Row(label, getter, setter, min, max));
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
                } else if (r.isDial()) {
                    drawDial(r, x, ry, alphaMult);
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
                Color textColor = r.isAction() ? ACTION_TEXT : ROW_TEXT;
                font.drawString(r.label, x + PADDING, ry + ROW_H - 4f, textColor, alphaMult);
                if (r.isAction()) {
                    // Trailing chevron for action rows — distinguishes them
                    // from toggle rows at a glance and stands in for the
                    // missing checkbox.
                    font.drawString(">>", x + PANEL_W - CHECK_W - PADDING + 1f,
                            ry + ROW_H - 4f, textColor, alphaMult);
                } else if (r.isDial()) {
                    String value = String.format(Locale.ROOT, "%.4f", r.dialValue.getAsDouble());
                    font.drawString(value, x + PANEL_W - 56f,
                            ry + ROW_H - 4f, ROW_TEXT, alphaMult);
                }
            }
        }
    }

    private void drawDial(Row row, float x, float rowY, float alphaMult) {
        float x0 = dialTrackX0(x);
        float x1 = dialTrackX1(x);
        float cy = rowY + ROW_H * 0.5f;
        double value = clamp(row.dialValue.getAsDouble(), row.dialMin, row.dialMax);
        float t = (float) ((value - row.dialMin) / (row.dialMax - row.dialMin));
        float knobX = x0 + (x1 - x0) * t;

        glLineWidth(3f);
        glColor4f(0.12f, 0.15f, 0.20f, 0.95f * alphaMult);
        glBegin(GL_LINES);
        glVertex2f(x0, cy); glVertex2f(x1, cy);
        glEnd();
        glColor4f(0.30f, 0.72f, 0.95f, 0.95f * alphaMult);
        glBegin(GL_LINES);
        glVertex2f(x0, cy); glVertex2f(knobX, cy);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(knobX - 3f, cy - 6f);
        glVertex2f(knobX + 3f, cy - 6f);
        glVertex2f(knobX + 3f, cy + 6f);
        glVertex2f(knobX - 3f, cy + 6f);
        glEnd();
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

            if (e.isLMBUpEvent() && draggingDial >= 0) {
                setDialFromPointer(rows.get(draggingDial), px);
                draggingDial = -1;
                e.consume();
                continue;
            }

            if (e.isMouseMoveEvent()) {
                if (draggingDial >= 0) {
                    setDialFromPointer(rows.get(draggingDial), px);
                    e.consume();
                    continue;
                }
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
                Row row = rows.get(rowIdx);
                if (row.isDial()) {
                    draggingDial = rowIdx;
                    setDialFromPointer(row, px);
                } else {
                    row.onClick.run();
                }
                e.consume();
            }
        }
    }

    private void setDialFromPointer(Row row, float pointerX) {
        row.dialSetter.accept(dialValueAt(pointerX, dialTrackX0(curX), dialTrackX1(curX),
                row.dialMin, row.dialMax));
    }

    static double dialValueAt(float pointerX, float trackX0, float trackX1, double min, double max) {
        float t = trackX1 <= trackX0 ? 0f : (pointerX - trackX0) / (trackX1 - trackX0);
        t = Math.max(0f, Math.min(1f, t));
        return min + (max - min) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float dialTrackX0(float panelX) { return panelX + PANEL_W - DIAL_TRACK_FROM_RIGHT; }
    private static float dialTrackX1(float panelX) { return panelX + PANEL_W - DIAL_TRACK_TO_RIGHT; }

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
