package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.BitmapFont;

import java.util.Map;

/** A final cascade sheet plus the font-family assets its declarations may name. */
public record UiTheme(StyleSheet sheet, Map<String, BitmapFont> fonts) {

    public UiTheme {
        fonts = Map.copyOf(fonts);
    }

    public BitmapFont font(String family) {
        BitmapFont font = fonts.get(family);
        if (font == null) {
            throw new UiStyleException("Theme \"" + sheet.name()
                    + "\" does not provide font-family \"" + family + "\".");
        }
        return font;
    }
}
