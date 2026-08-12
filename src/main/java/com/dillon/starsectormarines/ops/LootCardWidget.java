package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.loot.LootSelection;
import com.dillon.starsectormarines.ops.loot.LootStack;
import com.dillon.starsectormarines.ui.BaseWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.SpriteThumbWidget;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;

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

/** One selectable recovered stack in the post-battle loot grid. */
public final class LootCardWidget extends BaseWidget {

    private static final Color BG = new Color(0x10, 0x18, 0x22);
    private static final Color BG_HOVER = new Color(0x18, 0x28, 0x38);
    private static final Color BG_SELECTED = new Color(0x1C, 0x38, 0x34);
    private static final Color BG_BLOCKED = new Color(0x16, 0x16, 0x1A);
    private static final Color FRAME = new Color(0x4A, 0x6B, 0x8C);
    private static final Color FRAME_HOVER = new Color(0x9C, 0xC0, 0xE0);
    private static final Color FRAME_SELECTED = new Color(0x70, 0xC0, 0xA0);
    private static final Color TITLE = new Color(0xE0, 0xE8, 0xF4);
    private static final Color META = new Color(0x9C, 0xB0, 0xC8);
    private static final Color SELECTED = new Color(0x80, 0xE0, 0xA0);
    private static final Color BLOCKED = new Color(0xB0, 0x78, 0x78);

    private static final float PAD = 10f;
    private static final float ICON = 58f;

    private final int index;
    private final LootStack stack;
    private final LootSelection selection;
    private final Runnable onChanged;
    private final SpriteThumbWidget thumbnail;

    private boolean hovered;
    private boolean armed;

    public LootCardWidget(int index, LootStack stack, LootSelection selection,
                          float x, float y, float w, float h, Runnable onChanged) {
        this.index = index;
        this.stack = stack;
        this.selection = selection;
        this.onChanged = onChanged;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.thumbnail = new SpriteThumbWidget(stack.iconPath,
                x + PAD, y + (h - ICON) / 2f, ICON, ICON);
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
            if (selection.toggle(index) && onChanged != null) onChanged.run();
            return true;
        }
        return false;
    }

    @Override
    public void render(float alphaMult) {
        boolean selected = selection.isSelected(index);
        boolean canSelect = selection.canSelect(index);
        Color bg = selected ? BG_SELECTED : !canSelect ? BG_BLOCKED : hovered ? BG_HOVER : BG;
        Color frame = selected ? FRAME_SELECTED : hovered && canSelect ? FRAME_HOVER : FRAME;
        fillRect(x, y, w, h, bg, (armed ? 1f : 0.92f) * alphaMult);
        strokeRect(x, y, w, h, frame, alphaMult);

        thumbnail.render((canSelect ? 1f : 0.45f) * alphaMult);

        float textX = x + PAD + ICON + 10f;
        float textW = Math.max(20f, w - (textX - x) - PAD);
        float top = y + h - PAD;
        String title = truncate(stack.displayName, textW);
        Fonts.ORBITRON_20_BOLD.drawString(title, textX, top, TITLE,
                (canSelect ? 1f : 0.55f) * alphaMult);

        String quantity = MessageFormat.format(Strings.get("lootQuantityFmt"), stack.quantity);
        Fonts.ORBITRON_20.drawString(quantity, textX, top - 24f, META, alphaMult);
        String value = MessageFormat.format(Strings.get("lootValueFmt"),
                NumberFormat.getIntegerInstance().format(stack.totalValue()));
        Fonts.ORBITRON_20.drawString(value, textX, top - 46f, META, alphaMult);

        if (selected) {
            Fonts.ORBITRON_20_BOLD.drawString(Strings.get("lootSelected"),
                    textX, y + 24f, SELECTED, alphaMult);
        } else if (!canSelect) {
            Fonts.ORBITRON_20.drawString(Strings.get("lootOverBudget"),
                    textX, y + 24f, BLOCKED, alphaMult);
        }
    }

    private static String truncate(String text, float maxWidth) {
        if (text == null) return "";
        if (Fonts.ORBITRON_20_BOLD.measureWidth(text) <= maxWidth) return text;
        String suffix = "...";
        int end = text.length();
        while (end > 0 && Fonts.ORBITRON_20_BOLD.measureWidth(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    private static void fillRect(float x, float y, float w, float h, Color color, float alpha) {
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

    private static void strokeRect(float x, float y, float w, float h, Color color, float alpha) {
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
