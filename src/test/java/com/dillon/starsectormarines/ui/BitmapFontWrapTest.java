package com.dillon.starsectormarines.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitmapFontWrapTest {
    /** Every glyph 10 wide, including the space. */
    private static BitmapFont font(String chars) {
        StringBuilder manifest = new StringBuilder("common lineHeight=12 base=10 scaleW=64 scaleH=64\npage id=0 file=\"f.png\"\n");
        chars.codePoints().forEach(cp -> manifest.append("char id=").append(cp)
                .append(" x=0 y=0 width=8 height=8 xoffset=0 yoffset=0 xadvance=10\n"));
        BitmapFont font = new BitmapFont("graphics/fonts/test.fnt");
        font.installMetrics(manifest.toString());
        return font;
    }

    @Test
    void unspacedCjkWrapsByCharacterAndKeepsClosingMarksWithTheirCharacter() {
        BitmapFont font = font("日本語の文章です。ab ");
        assertEquals(List.of("日本語", "の文章", "です。"), font.wrapLines("日本語の文章です。", 35f));
    }

    @Test
    void spacedWordsStillWrapAtSpacesAndLatinRunsStayWhole() {
        BitmapFont font = font("ab cd日");
        assertEquals(List.of("ab cd", "日ab"), font.wrapLines("ab cd日ab", 55f));
        assertEquals(List.of("ab", "cd"), font.wrapLines("ab cd", 25f));
    }

    @Test
    void surrogatePairsMeasureAsOneGlyph() {
        assertEquals(10f, font("\uD842\uDFB7").measureWidth("\uD842\uDFB7"));
    }
}
