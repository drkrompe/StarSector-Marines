package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.StyleResolver;

/** Shared single-line measurement and placement seam for layout and paint. */
final class UiTextMeasurer {

    private final StyleResolver styles;

    UiTextMeasurer(StyleResolver styles) {
        this.styles = styles;
    }

    Measurement measure(UiElement element) {
        String value = element.text();
        BitmapFont font = styles.fontFor(element);
        if (font == null) return Measurement.EMPTY;
        if (value == null) return new Measurement(font, 0f, 0f);
        float width = font.measureWidth(value);
        return new Measurement(font, width, font.getLineHeight());
    }

    Rect lineBox(UiElement element, Measurement measurement) {
        Rect content = element.box().contentBox();
        float slack = content.width() - measurement.width();
        float x = switch (element.textAlign()) {
            case START -> content.x();
            case CENTER -> content.x() + slack * 0.5f;
            case END -> content.x() + slack;
        };
        float y = content.y();
        if (element.tag() == UiTag.BUTTON) {
            y += (content.height() - measurement.lineHeight()) * 0.5f;
        }
        return new Rect(x, y, measurement.width(), measurement.lineHeight());
    }

    record Measurement(BitmapFont font, float width, float lineHeight) {
        private static final Measurement EMPTY = new Measurement(null, 0f, 0f);
    }
}
