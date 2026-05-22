package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.function.IntConsumer;

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
 * Visual salvage slider for the expanded dossier: a track with a fill bar
 * showing the current negotiated percentage, flanked by −/+ buttons that
 * step in 10% increments. Click anywhere on the track to jump to that
 * percentage (snapped to 10%).
 *
 * <p>Per {@code roadmap/campaign/contracts.md} §"Salvage Layer 2", the
 * trade curve is {@code cashMultiplier = 100 + (baseline − negotiated) × 0.5}.
 * This widget renders the negotiated %, the resulting cash bonus, and the
 * endpoints (0% and the per-contract baseline %) so the player can see at
 * a glance whether they're at min cash or max salvage.
 *
 * <p>Boundary buttons render dimmed when they'd be no-ops (negotiated == 0
 * disables −; negotiated == baseline disables +). Clicks on a dimmed button
 * do nothing. Same visual cue is the disabled state hint.
 *
 * <p>Self-contained — handles its own button + track hit-testing so the
 * panel only wires one {@link IntConsumer} callback. Owner is responsible
 * for translating that absolute value into a new
 * {@link MarineOpsContext#setSelectedMission} replacement.
 */
public class SalvageSliderWidget extends BaseWidget {

    private static final Color TRACK_BG     = new Color(0x18, 0x24, 0x30);
    private static final Color TRACK_FRAME  = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FILL_COLOR   = new Color(0x70, 0xC0, 0xA0);
    private static final Color CASH_COLOR   = new Color(0xE0, 0xB0, 0x70);
    private static final Color LABEL_COLOR  = new Color(0x88, 0xA8, 0xCC);
    private static final Color VALUE_COLOR  = new Color(0xE0, 0xE8, 0xF4);
    private static final Color END_COLOR    = new Color(0x9C, 0xC0, 0xE0);

    private static final Color BTN_BG       = new Color(0x26, 0x38, 0x50);
    private static final Color BTN_BG_HOVER = new Color(0x35, 0x4C, 0x6A);
    private static final Color BTN_BG_DOWN  = new Color(0x4E, 0x6E, 0x95);
    private static final Color BTN_GLYPH    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color DISABLED     = new Color(0x55, 0x66, 0x77);

    private static final float BTN_W       = 22f;
    private static final float STEP        = 10f;
    private static final float SNAP        = 10f;
    private static final float CAPTION_H   = 18f;
    private static final float TRACK_H     = 12f;

    private final int baseline;
    private int negotiated;
    private final IntConsumer onChange;

    private boolean hoverMinus;
    private boolean hoverPlus;
    private boolean hoverTrack;
    private boolean armedMinus;
    private boolean armedPlus;
    private boolean armedTrack;

    public SalvageSliderWidget(float x, float y, float w, float h,
                               int baseline, int negotiated,
                               IntConsumer onChange) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.baseline = baseline;
        this.negotiated = clamp(negotiated, 0, baseline);
        this.onChange = onChange;
    }

    private float minusBtnX() { return x; }
    private float plusBtnX()  { return x + w - BTN_W; }
    private float trackLeft() { return minusBtnX() + BTN_W + 6f; }
    private float trackRight(){ return plusBtnX() - 6f; }
    private float trackY()    { return y; }

    private boolean inMinus(int px, int py) {
        return px >= minusBtnX() && px < minusBtnX() + BTN_W
                && py >= trackY() && py < trackY() + TRACK_H;
    }
    private boolean inPlus(int px, int py) {
        return px >= plusBtnX() && px < plusBtnX() + BTN_W
                && py >= trackY() && py < trackY() + TRACK_H;
    }
    private boolean inTrack(int px, int py) {
        return px >= trackLeft() && px < trackRight()
                && py >= trackY() && py < trackY() + TRACK_H;
    }

    @Override
    public boolean contains(int px, int py) {
        return inMinus(px, py) || inPlus(px, py) || inTrack(px, py);
    }

    @Override
    public void onMouseMove(int px, int py) {
        hoverMinus = inMinus(px, py);
        hoverPlus  = inPlus(px, py);
        hoverTrack = inTrack(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armedMinus = inMinus(px, py);
        armedPlus  = inPlus(px, py);
        armedTrack = inTrack(px, py);
        return armedMinus || armedPlus || armedTrack;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean handled = false;
        if (armedMinus && inMinus(px, py)) {
            if (negotiated > 0) emit(negotiated - (int) STEP);
            handled = true;
        } else if (armedPlus && inPlus(px, py)) {
            if (negotiated < baseline) emit(negotiated + (int) STEP);
            handled = true;
        } else if (armedTrack && inTrack(px, py)) {
            float frac = (px - trackLeft()) / Math.max(1f, trackRight() - trackLeft());
            int raw = Math.round(frac * baseline);
            int snapped = Math.round(raw / SNAP) * (int) SNAP;
            emit(clamp(snapped, 0, baseline));
            handled = true;
        }
        armedMinus = armedPlus = armedTrack = false;
        return handled;
    }

    private void emit(int newValue) {
        int clamped = clamp(newValue, 0, baseline);
        if (clamped == negotiated) return;
        negotiated = clamped;
        if (onChange != null) onChange.accept(clamped);
    }

    @Override
    public void render(float alphaMult) {
        // Caption row above the track — shows current value + resulting cash bonus.
        int cashBonus = (baseline - negotiated) / 2;
        float captionBaselineY = y + TRACK_H + CAPTION_H - 2f;
        Fonts.ORBITRON_20.drawString("Salvage ", x, captionBaselineY, LABEL_COLOR, alphaMult);
        float dx = x + Fonts.ORBITRON_20.measureWidth("Salvage ");
        Fonts.ORBITRON_20.drawString(negotiated + "%", dx, captionBaselineY, FILL_COLOR, alphaMult);
        dx += Fonts.ORBITRON_20.measureWidth(negotiated + "%");
        Fonts.ORBITRON_20.drawString("  (cash +", dx, captionBaselineY, LABEL_COLOR, alphaMult);
        dx += Fonts.ORBITRON_20.measureWidth("  (cash +");
        Fonts.ORBITRON_20.drawString(cashBonus + "%)", dx, captionBaselineY, CASH_COLOR, alphaMult);

        // − button
        boolean minusEnabled = negotiated > 0;
        renderButton(minusBtnX(), trackY(), BTN_W, TRACK_H, "–",
                minusEnabled, hoverMinus, armedMinus, alphaMult);

        // + button
        boolean plusEnabled = negotiated < baseline;
        renderButton(plusBtnX(), trackY(), BTN_W, TRACK_H, "+",
                plusEnabled, hoverPlus, armedPlus, alphaMult);

        // Track + fill
        float tL = trackLeft();
        float tR = trackRight();
        float tW = tR - tL;
        float ty = trackY();
        fillRect(tL, ty, tW, TRACK_H, TRACK_BG, 0.9f * alphaMult);
        float frac = baseline > 0 ? (negotiated / (float) baseline) : 0f;
        if (frac > 0f) {
            Color fill = hoverTrack && !armedTrack ? mix(FILL_COLOR, 1.15f) : FILL_COLOR;
            fillRect(tL, ty, tW * frac, TRACK_H, fill, 0.95f * alphaMult);
        }
        strokeRect(tL, ty, tW, TRACK_H, TRACK_FRAME, 0.9f * alphaMult);

        // Endpoint labels — "0%" left, "<baseline>%" right, sit just below the track.
        String leftEnd  = "0%";
        String rightEnd = baseline + "%";
        float endBaselineY = ty - 4f;
        Fonts.ORBITRON_20.drawString(leftEnd, tL, endBaselineY, END_COLOR, alphaMult);
        float rightW = Fonts.ORBITRON_20.measureWidth(rightEnd);
        Fonts.ORBITRON_20.drawString(rightEnd, tR - rightW, endBaselineY, END_COLOR, alphaMult);
    }

    private static void renderButton(float x, float y, float w, float h, String glyph,
                                     boolean enabled, boolean hovered, boolean armed,
                                     float alphaMult) {
        Color bg = !enabled ? BTN_BG : armed ? BTN_BG_DOWN : hovered ? BTN_BG_HOVER : BTN_BG;
        Color glyphColor = enabled ? BTN_GLYPH : DISABLED;
        fillRect(x, y, w, h, bg, (enabled ? 0.92f : 0.55f) * alphaMult);
        strokeRect(x, y, w, h, enabled ? TRACK_FRAME : DISABLED,
                (enabled ? 0.9f : 0.55f) * alphaMult);
        float gw = Fonts.ORBITRON_20_BOLD.measureWidth(glyph);
        float gx = x + (w - gw) * 0.5f;
        float gy = y + h - 2f;
        Fonts.ORBITRON_20_BOLD.drawString(glyph, gx, gy, glyphColor, alphaMult);
    }

    private static Color mix(Color base, float factor) {
        int r = Math.min(255, (int) (base.getRed() * factor));
        int g = Math.min(255, (int) (base.getGreen() * factor));
        int b = Math.min(255, (int) (base.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static void fillRect(float x, float y, float w, float h, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    private static void strokeRect(float x, float y, float w, float h, Color c, float a) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }
}
