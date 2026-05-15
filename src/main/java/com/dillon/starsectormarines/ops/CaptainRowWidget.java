package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.GL_BLEND;
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
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Compact captain-picker row for the briefing screen's squad section.
 *
 * <p>Shows the captain's name on the left and rank display on the right; click
 * selects the captain. Hover and selected states are background tints; selected
 * also draws an amber accent on the left edge. Pattern mirrors
 * {@link ClientRowWidget} but slimmer — no faction crest, no rep status, no
 * lock state (only ACTIVE captains are passed in).
 */
public class CaptainRowWidget extends BaseWidget {

    private static final Color BG_SELECTED  = new Color(0x30, 0x50, 0x80);
    private static final Color BG_HOVER     = new Color(0x25, 0x35, 0x50);
    private static final Color BG_ARMED     = new Color(0x40, 0x60, 0x90);
    private static final Color NAME_COLOR   = new Color(0xE0, 0xE8, 0xFF);
    private static final Color RANK_COLOR   = new Color(0x9C, 0xCC, 0xE0);
    private static final Color ACCENT_COLOR = new Color(0xFF, 0xB8, 0x00);

    public final MarineCaptain captain;
    private final Supplier<String> getSelectedId;
    private final Consumer<String> onSelect;

    private boolean hovered;
    private boolean armed;

    public CaptainRowWidget(MarineCaptain captain,
                            float x, float y, float w, float h,
                            Supplier<String> getSelectedId,
                            Consumer<String> onSelect) {
        this.captain = captain;
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.getSelectedId = getSelectedId;
        this.onSelect = onSelect;
    }

    @Override
    public void onMouseMove(int px, int py) {
        hovered = contains(px, py);
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        armed = true;
        return true;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        boolean wasArmed = armed;
        armed = false;
        if (wasArmed && contains(px, py)) {
            onSelect.accept(captain.id());
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        boolean selected = captain.id().equals(getSelectedId.get());

        Color bg = null;
        if (armed)         bg = BG_ARMED;
        else if (selected) bg = BG_SELECTED;
        else if (hovered)  bg = BG_HOVER;
        if (bg != null) {
            fillRect(x, y, w, h, bg, 0.55f * alphaMult);
        }

        if (selected) {
            fillRect(x, y, 3f, h, ACCENT_COLOR, 0.9f * alphaMult);
        }

        float textY = y + h - 6f;
        Fonts.ORBITRON_20.drawString(captain.name(), x + 10f, textY, NAME_COLOR, alphaMult);

        String rankText = captain.rank().displayName();
        float rankWidth = Fonts.ORBITRON_20.measureWidth(rankText);
        Fonts.ORBITRON_20.drawString(rankText, x + w - rankWidth - 10f, textY, RANK_COLOR, alphaMult);
    }

    private static void fillRect(float rx, float ry, float rw, float rh, Color c, float alpha) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        glBegin(GL_QUADS);
        glVertex2f(rx,        ry);
        glVertex2f(rx + rw,   ry);
        glVertex2f(rx + rw,   ry + rh);
        glVertex2f(rx,        ry + rh);
        glEnd();
    }
}
