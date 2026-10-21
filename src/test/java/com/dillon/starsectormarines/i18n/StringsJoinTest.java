package com.dillon.starsectormarines.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** An unknown key reads as itself, which is enough to see where the separator goes. */
class StringsJoinTest {

    @Test
    void joinPutsTheNamedSeparatorBetweenPartsAndSkipsBlanks() {
        assertEquals("aSEPb", Strings.join("SEP", "a", "", null, "b"));
        assertEquals("a", Strings.join("SEP", "a", " "));
        assertEquals("", Strings.join("SEP"));
    }

    @Test
    void capitalsFollowTheLanguageTheCopyNames() {
        assertEquals(Locale.ENGLISH, Strings.locale());
        assertEquals("TITLE", Strings.upper("title"));
        assertEquals("", Strings.upper(null));
    }
}
