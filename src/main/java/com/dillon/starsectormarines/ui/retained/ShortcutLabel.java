package com.dillon.starsectormarines.ui.retained;

/**
 * A label as it shows its keyboard shortcut, as the base game's bottom bar does: the key's letter picked out in
 * the word. A word without that letter, as in a translation, shows the key after it instead: {@code 商店 [T]}.
 *
 * @param text what is drawn
 * @param index where in {@code text} the letter to pick out is
 */
record ShortcutLabel(String text, int index) {

    static ShortcutLabel of(String word, char key) {
        char wanted = Character.toLowerCase(key);
        for (int i = 0; i < word.length(); i++) {
            if (Character.toLowerCase(word.charAt(i)) == wanted) return new ShortcutLabel(word, i);
        }
        return new ShortcutLabel(word + " [" + Character.toUpperCase(key) + "]", word.length() + 2);
    }
}
