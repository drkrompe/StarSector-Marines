package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

/** Discrete DEBUG control for the mission's five-step operation-scale axis. */
final class OperationTierSliderWidget extends BaseWidget {

    static final float DEFAULT_HEIGHT = 48f;

    private static final Color TRACK_BG = new Color(0x18, 0x24, 0x30);
    private static final Color TRACK_FRAME = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FILL_COLOR = new Color(0x70, 0xA8, 0xD8);
    private static final Color THUMB_COLOR = new Color(0xE8, 0xF4, 0xFF);
    private static final Color LABEL_COLOR = new Color(0x88, 0xA8, 0xCC);
    private static final Color VALUE_COLOR = new Color(0xC8, 0xE0, 0xFF);
    private static final Color BTN_BG = new Color(0x26, 0x38, 0x50);
    private static final Color BTN_BG_HOVER = new Color(0x35, 0x4C, 0x6A);
    private static final Color BTN_BG_DOWN = new Color(0x4E, 0x6E, 0x95);
    private static final Color DISABLED_BG = new Color(0x1A, 0x22, 0x2C);
    private static final Color DISABLED_FG = new Color(0x55, 0x66, 0x77);

    private static final float BTN_SIZE = 24f;
    private static final float TRACK_H = 8f;
    private static final float THUMB_W = 8f;
    private static final float THUMB_H = 22f;
    private static final float LABEL_GAP = 6f;
    private static final float CAPTION_GAP = 6f;

    private final List<OperationTier> tiers;
    private final Consumer<OperationTier> onChange;
    private int selectedIndex;

    private boolean hoverMinus;
    private boolean hoverPlus;
    private boolean hoverTrack;
    private boolean armedMinus;
    private boolean armedPlus;
    private boolean armedTrack;

    OperationTierSliderWidget(float x, float y, float w, float h,
                              MissionType missionType, OperationTier selected,
                              Consumer<OperationTier> onChange) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.tiers = allowedTiers(missionType);
        OperationTier clamped = OperationTier.clampTo(selected,
                missionType != null ? missionType.tierFloor : null);
        this.selectedIndex = Math.max(0, tiers.indexOf(clamped));
        this.onChange = onChange;
    }

    private static List<OperationTier> allowedTiers(MissionType missionType) {
        OperationTier floor = missionType != null
                ? missionType.tierFloor : OperationTier.FIRST_CONTRACT;
        List<OperationTier> allowed = new ArrayList<>();
        for (OperationTier tier : OperationTier.values()) {
            if (tier.atLeast(floor)) allowed.add(tier);
        }
        return List.copyOf(allowed);
    }

    private float rowBottom() {
        return y;
    }

    private float rowTop() {
        return y + BTN_SIZE;
    }

    private float minusBtnX() {
        return x;
    }

    private float plusBtnX() {
        return x + w - BTN_SIZE;
    }

    private float leftLabelW() {
        return Fonts.ORBITRON_20.measureWidth(tiers.get(0).displayName);
    }

    private float rightLabelW() {
        return Fonts.ORBITRON_20.measureWidth(tiers.get(tiers.size() - 1).displayName);
    }

    private float trackLeft() {
        return minusBtnX() + BTN_SIZE + LABEL_GAP + leftLabelW() + LABEL_GAP;
    }

    private float trackRight() {
        return plusBtnX() - LABEL_GAP - rightLabelW() - LABEL_GAP;
    }

    private float trackY() {
        return rowBottom() + (BTN_SIZE - TRACK_H) * 0.5f;
    }

    private boolean inMinus(int px, int py) {
        return px >= minusBtnX() && px < minusBtnX() + BTN_SIZE
                && py >= rowBottom() && py < rowTop();
    }

    private boolean inPlus(int px, int py) {
        return px >= plusBtnX() && px < plusBtnX() + BTN_SIZE
                && py >= rowBottom() && py < rowTop();
    }

    private boolean inTrack(int px, int py) {
        float left = minusBtnX() + BTN_SIZE + LABEL_GAP;
        float right = plusBtnX() - LABEL_GAP;
        return px >= left && px < right && py >= rowBottom() && py < rowTop();
    }

    @Override
    public boolean contains(int px, int py) {
        return inMinus(px, py) || inPlus(px, py) || inTrack(px, py);
    }

    @Override
    public void onMouseMove(int px, int py) {
        hoverMinus = inMinus(px, py);
        hoverPlus = inPlus(px, py);
        hoverTrack = inTrack(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armedMinus = inMinus(px, py);
        armedPlus = inPlus(px, py);
        armedTrack = inTrack(px, py);
        return armedMinus || armedPlus || armedTrack;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean handled = false;
        if (armedMinus && inMinus(px, py)) {
            emit(selectedIndex - 1);
            handled = true;
        } else if (armedPlus && inPlus(px, py)) {
            emit(selectedIndex + 1);
            handled = true;
        } else if (armedTrack && inTrack(px, py)) {
            float fraction = (px - trackLeft())
                    / Math.max(1f, trackRight() - trackLeft());
            emit(Math.round(fraction * (tiers.size() - 1)));
            handled = true;
        }
        armedMinus = false;
        armedPlus = false;
        armedTrack = false;
        return handled;
    }

    private void emit(int index) {
        int clamped = Math.max(0, Math.min(tiers.size() - 1, index));
        if (clamped == selectedIndex) return;
        selectedIndex = clamped;
        if (onChange != null) onChange.accept(tiers.get(selectedIndex));
    }

    OperationTier selectedTier() {
        return tiers.get(selectedIndex);
    }

    @Override
    public void render(float alphaMult) {
        float captionY = y + h - CAPTION_GAP;
        Fonts.ORBITRON_20.drawString("Operation Tier", x, captionY,
                LABEL_COLOR, alphaMult);
        Fonts.ORBITRON_20_BOLD.drawString(selectedTier().displayName,
                x + Fonts.ORBITRON_20.measureWidth("Operation Tier  "),
                captionY, VALUE_COLOR, alphaMult);

        boolean minusEnabled = selectedIndex > 0;
        boolean plusEnabled = selectedIndex < tiers.size() - 1;
        renderButton(minusBtnX(), rowBottom(), "-", minusEnabled,
                hoverMinus, armedMinus, alphaMult);
        renderButton(plusBtnX(), rowBottom(), "+", plusEnabled,
                hoverPlus, armedPlus, alphaMult);

        float labelY = rowBottom() + BTN_SIZE - 6f;
        OperationTier first = tiers.get(0);
        OperationTier last = tiers.get(tiers.size() - 1);
        Fonts.ORBITRON_20.drawString(first.displayName,
                minusBtnX() + BTN_SIZE + LABEL_GAP, labelY,
                LABEL_COLOR, alphaMult);
        Fonts.ORBITRON_20.drawString(last.displayName,
                plusBtnX() - LABEL_GAP - rightLabelW(), labelY,
                LABEL_COLOR, alphaMult);

        float left = trackLeft();
        float right = trackRight();
        float width = Math.max(1f, right - left);
        float trackY = trackY();
        float fraction = tiers.size() == 1
                ? 0f : selectedIndex / (float) (tiers.size() - 1);
        fillRect(left, trackY, width, TRACK_H, TRACK_BG, 0.9f * alphaMult);
        fillRect(left, trackY, width * fraction, TRACK_H,
                FILL_COLOR, 0.95f * alphaMult);
        strokeRect(left, trackY, width, TRACK_H, TRACK_FRAME, 0.9f * alphaMult);

        for (int i = 0; i < tiers.size(); i++) {
            float tickFraction = tiers.size() == 1
                    ? 0f : i / (float) (tiers.size() - 1);
            float tickX = left + width * tickFraction - 1f;
            fillRect(tickX, trackY - 2f, 2f, TRACK_H + 4f,
                    TRACK_FRAME, 0.9f * alphaMult);
        }

        float thumbX = left + width * fraction - THUMB_W * 0.5f;
        float thumbY = rowBottom() + (BTN_SIZE - THUMB_H) * 0.5f;
        fillRect(thumbX, thumbY, THUMB_W, THUMB_H,
                THUMB_COLOR, (hoverTrack || armedTrack ? 1f : 0.92f) * alphaMult);
        strokeRect(thumbX, thumbY, THUMB_W, THUMB_H,
                TRACK_FRAME, 0.95f * alphaMult);
    }

    private static void renderButton(float x, float y, String glyph,
                                     boolean enabled, boolean hovered,
                                     boolean armed, float alphaMult) {
        Color background;
        if (!enabled) background = DISABLED_BG;
        else if (armed) background = BTN_BG_DOWN;
        else if (hovered) background = BTN_BG_HOVER;
        else background = BTN_BG;
        Color foreground = enabled ? THUMB_COLOR : DISABLED_FG;
        fillRect(x, y, BTN_SIZE, BTN_SIZE, background,
                (enabled ? 0.92f : 0.55f) * alphaMult);
        strokeRect(x, y, BTN_SIZE, BTN_SIZE,
                enabled ? TRACK_FRAME : DISABLED_FG,
                (enabled ? 0.9f : 0.55f) * alphaMult);
        float glyphX = x + (BTN_SIZE - Fonts.ORBITRON_20_BOLD.measureWidth(glyph)) * 0.5f;
        Fonts.ORBITRON_20_BOLD.drawString(glyph, glyphX,
                y + BTN_SIZE - 6f, foreground, alphaMult);
    }

    private static void fillRect(float x, float y, float w, float h,
                                 Color color, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private static void strokeRect(float x, float y, float w, float h,
                                   Color color, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }
}
