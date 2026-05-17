package com.dillon.starsectormarines.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
 * Single-line editable text field. Click to focus, type to append, backspace
 * to delete the last char. Cursor blinks at 1Hz while focused; no caret
 * positioning by click or arrow keys, no selection, no clipboard — minimum
 * viable input for the in-game tileset catalog. Re-extend when a second use
 * case appears.
 *
 * <p>Keyboard routing is the caller's responsibility — {@link WidgetRoot}
 * only routes mouse events. The owning screen calls {@link #processKey} for
 * each key/char event it wants to deliver to the focused field. This keeps
 * the widget framework's keyboard story explicit instead of broadcasting
 * every keystroke to every widget.
 */
public class TextFieldWidget extends BaseWidget {

    private static final Color BG_IDLE    = new Color(0x10, 0x16, 0x20);
    private static final Color BG_FOCUS   = new Color(0x16, 0x22, 0x30);
    private static final Color BORDER_IDLE  = new Color(0x4A, 0x6B, 0x8C);
    private static final Color BORDER_FOCUS = new Color(0x7AB7FF);
    private static final Color TEXT_COLOR = new Color(0xE6, 0xEE, 0xFA);
    private static final Color PLACEHOLDER = new Color(0x6A, 0x78, 0x8C);
    private static final Color CURSOR     = new Color(0xE0, 0xF0, 0xFF);

    private static final float TEXT_PAD_X = 6f;
    private static final float TEXT_PAD_Y = 4f;

    private final BitmapFont font;
    private final int maxChars;
    private final String placeholder;
    private Consumer<String> onChange;

    private StringBuilder text = new StringBuilder();
    private boolean focused;
    private float blinkPhase; // seconds since last blink toggle
    private boolean cursorOn = true;

    public TextFieldWidget(float x, float y, float w, float h,
                           BitmapFont font, int maxChars, String placeholder) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = font;
        this.maxChars = maxChars;
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    public String getText() { return text.toString(); }

    /** Replaces the current text. Does not fire {@link #onChange}. */
    public void setText(String s) {
        text.setLength(0);
        if (s != null) {
            for (int i = 0; i < s.length() && text.length() < maxChars; i++) {
                text.append(s.charAt(i));
            }
        }
    }

    public boolean isFocused() { return focused; }

    public void setFocused(boolean f) {
        this.focused = f;
        this.blinkPhase = 0f;
        this.cursorOn = true;
    }

    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    @Override
    public boolean onMouseDown(int px, int py) {
        setFocused(true);
        return true;
    }

    @Override
    public boolean onMouseUp(int px, int py) {
        return false;
    }

    @Override
    public void advance(float dt) {
        if (!focused) return;
        blinkPhase += dt;
        if (blinkPhase >= 0.5f) {
            blinkPhase = 0f;
            cursorOn = !cursorOn;
        }
    }

    /**
     * Caller-driven keyboard delivery. Returns {@code true} if the event was
     * consumed by this field (so the caller can stop routing it elsewhere).
     * Unfocused fields ignore everything.
     */
    public boolean processKey(InputEventAPI e) {
        if (!focused) return false;
        if (!e.isKeyDownEvent()) return false;

        int key = e.getEventValue();
        if (key == Keyboard.KEY_BACK) {
            if (text.length() > 0) {
                text.setLength(text.length() - 1);
                fireChange();
            }
            return true;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_ESCAPE) {
            // Enter/Esc both unfocus — confirms entry. Caller can re-focus if needed.
            setFocused(false);
            return true;
        }
        if (key == Keyboard.KEY_TAB) {
            setFocused(false);
            return false; // let the screen route Tab to focus-next if it wants
        }

        char c = e.getEventChar();
        if (isPrintable(c) && text.length() < maxChars) {
            text.append(c);
            fireChange();
            return true;
        }
        // Swallow other keys (arrows, F1, etc.) while focused so they don't fire game shortcuts.
        return true;
    }

    /** Convenience: route a batch of input events, consuming each one this field accepted. */
    public void routeKeys(List<InputEventAPI> events) {
        if (events == null || !focused) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isKeyboardEvent()) continue;
            if (processKey(e)) e.consume();
        }
    }

    private void fireChange() {
        if (onChange != null) onChange.accept(text.toString());
    }

    private static boolean isPrintable(char c) {
        // ASCII printable range. Tab + newline excluded — those are control flow here.
        return c >= 0x20 && c < 0x7F;
    }

    @Override
    public void render(float alphaMult) {
        Color bg = focused ? BG_FOCUS : BG_IDLE;
        Color border = focused ? BORDER_FOCUS : BORDER_IDLE;

        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(bg.getRed() / 255f, bg.getGreen() / 255f, bg.getBlue() / 255f, 0.92f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();

        glColor4f(border.getRed() / 255f, border.getGreen() / 255f, border.getBlue() / 255f, 0.9f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();

        // Text baseline: font top-edge at (x + pad, y + h - pad). drawString takes top-left in GL coords.
        float textX = x + TEXT_PAD_X;
        float textY = y + h - TEXT_PAD_Y;
        boolean empty = text.length() == 0;
        if (empty && !focused && !placeholder.isEmpty()) {
            font.drawString(placeholder, textX, textY, PLACEHOLDER, alphaMult);
        } else {
            font.drawString(text.toString(), textX, textY, TEXT_COLOR, alphaMult);
        }

        if (focused && cursorOn) {
            float cursorX = textX + font.measureWidth(text.toString()) + 1f;
            float cursorY0 = y + TEXT_PAD_Y;
            float cursorY1 = y + h - TEXT_PAD_Y;
            glDisable(GL_TEXTURE_2D);
            glColor4f(CURSOR.getRed() / 255f, CURSOR.getGreen() / 255f, CURSOR.getBlue() / 255f, 0.9f * alphaMult);
            glLineWidth(1.5f);
            glBegin(GL_LINES);
            glVertex2f(cursorX, cursorY0);
            glVertex2f(cursorX, cursorY1);
            glEnd();
        }
    }
}
