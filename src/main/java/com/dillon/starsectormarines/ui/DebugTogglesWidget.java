package com.dillon.starsectormarines.ui;

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
 * Collapsible top-of-screen widget hosting a vertical list of boolean
 * debug toggles. Each entry is a ({@link String label}, {@link BooleanSupplier
 * getter}, {@link Runnable toggle}) triple — the widget reads {@code getter}
 * each frame to draw the checkbox state and calls {@code toggle} on click,
 * so callers wiring up to a static field need a one-line lambda each.
 *
 * <p>UI shape: a thin "DEBUG +" header bar. Clicking the header expands /
 * collapses the list. Each row shows the label on the left and a green
 * (on) / dark (off) checkbox on the right. Hovering a row tints its
 * background. Widget is laid out top-center of the battle screen by
 * {@link com.dillon.starsectormarines.ops.BattleScreen}.
 *
 * <p>Designed as the home for future debug instrumentation toggles —
 * pathfinding overlays, AI decision traces, tactical-graph visualizations
 * — so adding a new toggle is a single {@link #add} call at the registration
 * site, not a new widget.
 */
public class DebugTogglesWidget extends BaseWidget {

    public static final class Entry {
        public final String label;
        public final BooleanSupplier getter;
        public final Runnable toggle;

        public Entry(String label, BooleanSupplier getter, Runnable toggle) {
            this.label = label;
            this.getter = getter;
            this.toggle = toggle;
        }
    }

    private static final float HEADER_H = 24f;
    private static final float ROW_H = 22f;
    private static final float CHECK_SIZE = 14f;
    private static final float PADDING = 8f;

    private static final Color HEADER_TEXT = new Color(230, 230, 230);
    private static final Color ROW_TEXT = new Color(200, 210, 220);

    private final List<Entry> entries = new ArrayList<>();
    private final BitmapFont font;
    private boolean expanded;
    /** -1 = header hovered, ≥0 = entry index hovered, -2 = nothing hovered. */
    private int hoverIdx = -2;
    private boolean armed;

    public DebugTogglesWidget(float x, float y, float w, BitmapFont font) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = HEADER_H;
    }

    /** Register a new toggle entry. Returns {@code this} for chained registration. */
    public DebugTogglesWidget add(String label, BooleanSupplier getter, Runnable toggle) {
        entries.add(new Entry(label, getter, toggle));
        return this;
    }

    private float computeHeight() {
        return HEADER_H + (expanded ? entries.size() * ROW_H : 0f);
    }

    @Override
    public boolean contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + computeHeight();
    }

    @Override
    public void onMouseMove(int px, int py) {
        if (!contains(px, py)) {
            hoverIdx = -2;
            return;
        }
        float rely = py - y;
        if (rely < HEADER_H) {
            hoverIdx = -1;
        } else if (expanded) {
            int idx = (int) ((rely - HEADER_H) / ROW_H);
            hoverIdx = (idx >= 0 && idx < entries.size()) ? idx : -2;
        } else {
            hoverIdx = -2;
        }
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armed = contains(px, py);
        return armed;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean wasArmed = armed;
        armed = false;
        if (!wasArmed || !contains(px, py)) return false;
        float rely = py - y;
        if (rely < HEADER_H) {
            expanded = !expanded;
            this.h = computeHeight();
            return true;
        }
        if (expanded) {
            int idx = (int) ((rely - HEADER_H) / ROW_H);
            if (idx >= 0 && idx < entries.size()) {
                entries.get(idx).toggle.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        font.ensureLoaded();
        float totalH = computeHeight();

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Background fill (body of expanded panel).
        glColor4f(0.08f, 0.10f, 0.14f, 0.85f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + totalH);
        glVertex2f(x,     y + totalH);
        glEnd();

        // Header strip — slightly brighter; hover bumps it.
        float hr = 0.16f, hg = 0.22f, hb = 0.30f;
        if (hoverIdx == -1) { hr = 0.30f; hg = 0.42f; hb = 0.58f; }
        glColor4f(hr, hg, hb, 0.95f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + HEADER_H);
        glVertex2f(x,     y + HEADER_H);
        glEnd();

        // Row backgrounds (hover) + checkboxes.
        if (expanded) {
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                float ry = y + HEADER_H + i * ROW_H;

                if (i == hoverIdx) {
                    glColor4f(0.20f, 0.28f, 0.40f, 0.7f * alphaMult);
                    glBegin(GL_QUADS);
                    glVertex2f(x,     ry);
                    glVertex2f(x + w, ry);
                    glVertex2f(x + w, ry + ROW_H);
                    glVertex2f(x,     ry + ROW_H);
                    glEnd();
                }

                float chkX = x + w - CHECK_SIZE - PADDING;
                float chkY = ry + (ROW_H - CHECK_SIZE) / 2f;
                boolean on = e.getter.getAsBoolean();
                if (on) glColor4f(0.30f, 0.85f, 0.40f, 0.95f * alphaMult);
                else    glColor4f(0.12f, 0.15f, 0.20f, 0.95f * alphaMult);
                glBegin(GL_QUADS);
                glVertex2f(chkX,              chkY);
                glVertex2f(chkX + CHECK_SIZE, chkY);
                glVertex2f(chkX + CHECK_SIZE, chkY + CHECK_SIZE);
                glVertex2f(chkX,              chkY + CHECK_SIZE);
                glEnd();
                glColor4f(0.55f, 0.65f, 0.80f, 0.9f * alphaMult);
                glLineWidth(1f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(chkX,              chkY);
                glVertex2f(chkX + CHECK_SIZE, chkY);
                glVertex2f(chkX + CHECK_SIZE, chkY + CHECK_SIZE);
                glVertex2f(chkX,              chkY + CHECK_SIZE);
                glEnd();
            }
        }

        // Outer border.
        glColor4f(0.55f, 0.65f, 0.80f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + totalH);
        glVertex2f(x,     y + totalH);
        glEnd();

        // Header separator when expanded.
        if (expanded) {
            glBegin(GL_LINES);
            glVertex2f(x,     y + HEADER_H);
            glVertex2f(x + w, y + HEADER_H);
            glEnd();
        }

        // Text labels — same baseline pattern as the speed buttons (drawString
        // takes baseline-ish y; row_h - 4 puts text near the row's bottom).
        String headerText = "DEBUG " + (expanded ? "-" : "+");
        font.drawString(headerText, x + PADDING, y + HEADER_H - 4f, HEADER_TEXT, alphaMult);
        if (expanded) {
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                float ry = y + HEADER_H + i * ROW_H;
                font.drawString(e.label, x + PADDING, ry + ROW_H - 4f, ROW_TEXT, alphaMult);
            }
        }
    }
}
