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
 * Visual salvage slider for the expanded dossier. Self-contained widget with:
 * <ul>
 *   <li>Caption row at the top — "Salvage X% (cash +Y%)"</li>
 *   <li>Slider row at the bottom — square −/+ buttons flanking a track with
 *       endpoint labels (0% / baseline%) and a visible thumb at the fill
 *       boundary</li>
 * </ul>
 *
 * <p>All rendering stays within the declared rect — caller can size {@code h}
 * to allocate the row. {@link #DEFAULT_HEIGHT} is the natural height; smaller
 * values squeeze the rows.
 *
 * <p>−/+ buttons dim and become click-inert at the boundaries (negotiated == 0
 * disables −; negotiated == baseline disables +). Track click jumps to that
 * percentage (snapped to {@link #SNAP}).
 *
 * <p>Per {@code roadmap/campaign/contracts.md} §"Salvage Layer 2": cash bonus
 * curve = {@code (baseline − negotiated) / 2}.
 */
public class SalvageSliderWidget extends BaseWidget {

    /** Recommended height — fits caption + slider row with no clipping. */
    public static final float DEFAULT_HEIGHT = 48f;

    private static final Color TRACK_BG     = new Color(0x18, 0x24, 0x30);
    private static final Color TRACK_FRAME  = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FILL_COLOR   = new Color(0x70, 0xC0, 0xA0);
    private static final Color THUMB_COLOR  = new Color(0xE8, 0xF4, 0xFF);
    private static final Color THUMB_HOVER  = new Color(0xFF, 0xFF, 0xFF);
    private static final Color CASH_COLOR   = new Color(0xE0, 0xB0, 0x70);
    private static final Color LABEL_COLOR  = new Color(0x88, 0xA8, 0xCC);
    private static final Color END_COLOR    = new Color(0x9C, 0xC0, 0xE0);

    private static final Color BTN_BG       = new Color(0x26, 0x38, 0x50);
    private static final Color BTN_BG_HOVER = new Color(0x35, 0x4C, 0x6A);
    private static final Color BTN_BG_DOWN  = new Color(0x4E, 0x6E, 0x95);
    private static final Color BTN_GLYPH    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color DISABLED_BG  = new Color(0x1A, 0x22, 0x2C);
    private static final Color DISABLED_FG  = new Color(0x55, 0x66, 0x77);

    private static final float STEP        = 10f;
    private static final float SNAP        = 10f;
    private static final float BTN_SIZE    = 24f;
    private static final float TRACK_H     = 10f;
    private static final float THUMB_W     = 8f;
    private static final float THUMB_H     = 22f;
    private static final float LABEL_GAP   = 6f;
    private static final float ROW_GAP     = 6f;

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

    // -- Row geometry (slider row sits at the bottom; caption at the top) ----

    /** Y at the bottom of the slider row (= widget's bottom). */
    private float rowBottom() { return y; }
    /** Y at the top of the slider row. */
    private float rowTop()    { return y + BTN_SIZE; }

    private float minusBtnX() { return x; }
    private float plusBtnX()  { return x + w - BTN_SIZE; }

    /** Cached endpoint label widths — used to position the track between them. */
    private float leftLabelW()  { return Fonts.ORBITRON_20.measureWidth("0%"); }
    private float rightLabelW() { return Fonts.ORBITRON_20.measureWidth(baseline + "%"); }

    private float trackLeft()  {
        return minusBtnX() + BTN_SIZE + LABEL_GAP + leftLabelW() + LABEL_GAP;
    }
    private float trackRight() {
        return plusBtnX() - LABEL_GAP - rightLabelW() - LABEL_GAP;
    }
    private float trackY()     { return rowBottom() + (BTN_SIZE - TRACK_H) * 0.5f; }

    private boolean inMinus(int px, int py) {
        return px >= minusBtnX() && px < minusBtnX() + BTN_SIZE
                && py >= rowBottom() && py < rowTop();
    }
    private boolean inPlus(int px, int py) {
        return px >= plusBtnX() && px < plusBtnX() + BTN_SIZE
                && py >= rowBottom() && py < rowTop();
    }
    private boolean inTrack(int px, int py) {
        // Hit area is the full slider row between the buttons + label gaps so
        // small mouse misses near the track still register. Easier to land than
        // a 10px-tall strip.
        float l = minusBtnX() + BTN_SIZE + LABEL_GAP;
        float r = plusBtnX() - LABEL_GAP;
        return px >= l && px < r && py >= rowBottom() && py < rowTop();
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
            // Translate cursor x → snapped percentage. Use the visual track
            // bounds (between the endpoint labels) for the math, not the
            // larger hit-area, so the click lands where the user pointed.
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
        // Caption row — top of the widget. Composed inline so the % numbers
        // can be colored without splitting into multiple LabelWidgets.
        int cashBonus = (baseline - negotiated) / 2;
        float captionBaseY = y + h - ROW_GAP;
        float dx = x;
        Fonts.ORBITRON_20.drawString("Salvage ", dx, captionBaseY, LABEL_COLOR, alphaMult);
        dx += Fonts.ORBITRON_20.measureWidth("Salvage ");
        String valStr = negotiated + "%";
        Fonts.ORBITRON_20.drawString(valStr, dx, captionBaseY, FILL_COLOR, alphaMult);
        dx += Fonts.ORBITRON_20.measureWidth(valStr);
        Fonts.ORBITRON_20.drawString("   (cash bonus +", dx, captionBaseY, LABEL_COLOR, alphaMult);
        dx += Fonts.ORBITRON_20.measureWidth("   (cash bonus +");
        String bonusStr = cashBonus + "%)";
        Fonts.ORBITRON_20.drawString(bonusStr, dx, captionBaseY, CASH_COLOR, alphaMult);

        // Slider row geometry.
        float btnY  = rowBottom();
        float tL    = trackLeft();
        float tR    = trackRight();
        float tW    = Math.max(1f, tR - tL);
        float ty    = trackY();

        // − button
        boolean minusEnabled = negotiated > 0;
        renderButton(minusBtnX(), btnY, BTN_SIZE, BTN_SIZE, "–",
                minusEnabled, hoverMinus, armedMinus, alphaMult);

        // + button
        boolean plusEnabled = negotiated < baseline;
        renderButton(plusBtnX(), btnY, BTN_SIZE, BTN_SIZE, "+",
                plusEnabled, hoverPlus, armedPlus, alphaMult);

        // Endpoint labels — drawn inline at the slider-row baseline so they
        // align visually with the buttons + thumb.
        float labelBaseY = btnY + BTN_SIZE - 6f;
        Fonts.ORBITRON_20.drawString("0%",
                minusBtnX() + BTN_SIZE + LABEL_GAP,
                labelBaseY, END_COLOR, alphaMult);
        Fonts.ORBITRON_20.drawString(baseline + "%",
                plusBtnX() - LABEL_GAP - rightLabelW(),
                labelBaseY, END_COLOR, alphaMult);

        // Track + fill
        fillRect(tL, ty, tW, TRACK_H, TRACK_BG, 0.9f * alphaMult);
        float frac = baseline > 0 ? (negotiated / (float) baseline) : 0f;
        if (frac > 0f) fillRect(tL, ty, tW * frac, TRACK_H, FILL_COLOR, 0.95f * alphaMult);
        strokeRect(tL, ty, tW, TRACK_H, TRACK_FRAME, 0.9f * alphaMult);

        // Thumb — visible "nub" at the fill boundary so the player can see
        // exactly where the current value is and where a click on the track
        // would land relative to it.
        float thumbCx = tL + tW * frac;
        float thumbX = thumbCx - THUMB_W * 0.5f;
        float thumbY = btnY + (BTN_SIZE - THUMB_H) * 0.5f;
        Color thumbFill = (hoverTrack || armedTrack) ? THUMB_HOVER : THUMB_COLOR;
        fillRect(thumbX, thumbY, THUMB_W, THUMB_H, thumbFill, 0.95f * alphaMult);
        strokeRect(thumbX, thumbY, THUMB_W, THUMB_H, TRACK_FRAME, 0.95f * alphaMult);
    }

    private static void renderButton(float x, float y, float w, float h, String glyph,
                                     boolean enabled, boolean hovered, boolean armed,
                                     float alphaMult) {
        Color bg;
        if (!enabled)        bg = DISABLED_BG;
        else if (armed)      bg = BTN_BG_DOWN;
        else if (hovered)    bg = BTN_BG_HOVER;
        else                 bg = BTN_BG;
        Color glyphColor = enabled ? BTN_GLYPH : DISABLED_FG;
        fillRect(x, y, w, h, bg, (enabled ? 0.92f : 0.55f) * alphaMult);
        strokeRect(x, y, w, h, enabled ? TRACK_FRAME : DISABLED_FG,
                (enabled ? 0.9f : 0.55f) * alphaMult);
        float gw = Fonts.ORBITRON_20_BOLD.measureWidth(glyph);
        float gx = x + (w - gw) * 0.5f;
        float gy = y + h - 6f;
        Fonts.ORBITRON_20_BOLD.drawString(glyph, gx, gy, glyphColor, alphaMult);
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
