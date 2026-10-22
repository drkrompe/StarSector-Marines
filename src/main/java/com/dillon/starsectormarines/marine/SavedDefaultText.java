package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

/**
 * Built-in default text that a save holds as an English literal (a squad's default
 * name, a generated definition name) and that is translated each time it is shown.
 * When the catalog has no pattern for it, the saved literal is what the reader gets.
 */
final class SavedDefaultText {

    private SavedDefaultText() {}

    static String formatOr(String saved, String key, Object... arguments) {
        String pattern = Strings.get(key);
        return pattern.equals(key) ? saved : Strings.format(key, arguments);
    }
}
