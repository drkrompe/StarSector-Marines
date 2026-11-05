package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShortcutLabelTest {
    @Test void theKeysFirstLetterInTheWordIsPickedOutWhateverItsCase() {
        assertEquals(new ShortcutLabel("Store", 2), ShortcutLabel.of("Store", 'o'));
        assertEquals(new ShortcutLabel("HQ", 0), ShortcutLabel.of("HQ", 'h'));
    }

    @Test void aWordWithoutTheLetterShowsTheKeyAfterIt() {
        ShortcutLabel label = ShortcutLabel.of("商店", 'o');
        assertEquals("商店 [O]", label.text());
        assertEquals('O', label.text().charAt(label.index()));
    }
}
