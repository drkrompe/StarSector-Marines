package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.StyleResolver;

import java.util.List;

/** Shared single-line and wrapped-block measurement seam for layout and paint. */
final class UiTextMeasurer {

    private final StyleResolver styles;

    UiTextMeasurer(StyleResolver styles) {
        this.styles = styles;
    }

    Measurement measure(UiElement element) {
        return measure(element, 0f);
    }

    Measurement measure(UiElement element, float availableWidth) {
        String value = element.text();
        BitmapFont font = styles.fontFor(element);
        if (font == null) return Measurement.EMPTY;
        if (value == null) return new Measurement(font, 0f, 0f, List.of());
        List<String> lines = element.whiteSpace() == UiWhiteSpace.NORMAL && availableWidth > 0f
                ? font.wrapLines(value, availableWidth) : List.of(value);
        float width = 0f;
        for (String line : lines) width = Math.max(width, font.measureWidth(line));
        return new Measurement(font, width, font.getLineHeight() * lines.size(), lines);
    }

    List<TextLine> lineBoxes(UiElement element, Measurement measurement) {
        Rect content = element.box().contentBox();
        float y = content.y();
        if (element.tag() == UiTag.BUTTON || element.tag() == UiTag.INPUT) {
            y += (content.height() - measurement.height()) * 0.5f;
        }
        java.util.ArrayList<TextLine> result = new java.util.ArrayList<>();
        for (int index = 0; index < measurement.lines().size(); index++) {
            String value = measurement.lines().get(index);
            float width = measurement.font().measureWidth(value);
            float slack = content.width() - width;
            float x = switch (element.textAlign()) {
                case START -> content.x();
                case CENTER -> content.x() + slack * 0.5f;
                case END -> content.x() + slack;
            };
            result.add(new TextLine(value, new Rect(x,
                    y + index * measurement.lineHeight(), width, measurement.lineHeight())));
        }
        return List.copyOf(result);
    }

    /** Single-line compatibility seam used by intrinsic placement tests. */
    Rect lineBox(UiElement element, Measurement measurement) {
        List<TextLine> lines = lineBoxes(element, measurement);
        return lines.isEmpty() ? Rect.EMPTY : lines.get(0).box();
    }

    record Measurement(BitmapFont font, float width, float height, List<String> lines) {
        private static final Measurement EMPTY = new Measurement(null, 0f, 0f, List.of());

        float lineHeight() {
            return font != null ? font.getLineHeight() : 0f;
        }
    }

    record TextLine(String value, Rect box) {}
}
