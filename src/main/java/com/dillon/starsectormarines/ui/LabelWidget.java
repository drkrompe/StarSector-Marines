package com.dillon.starsectormarines.ui;

import java.awt.Color;

/**
 * Static text widget — no input, draws a single line via the given font.
 * Position is the top-left of the text bounding box; width/height are derived
 * from the font but kept on {@link BaseWidget#w}/{@link BaseWidget#h} so any
 * future hit-testable container can lay it out without measuring twice.
 */
public class LabelWidget extends BaseWidget {

    public BitmapFont font;
    public String text;
    public Color color;

    public LabelWidget(BitmapFont font, String text, float x, float y, Color color) {
        this.font = font;
        this.text = text;
        this.color = color;
        this.x = x;
        this.y = y;
        if (font.ensureLoaded()) {
            this.w = font.measureWidth(text);
            this.h = font.getLineHeight();
        }
    }

    @Override
    public void render(float alphaMult) {
        font.drawString(text, x, y, color, alphaMult);
    }
}
