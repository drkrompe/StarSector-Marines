package com.dillon.starsectormarines.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BitmapFontPagesTest {
    private static final String TWO_PAGES = """
            info face="Test" size=12
            common lineHeight=14 base=11 scaleW=64 scaleH=64 pages=2
            page id=0 file="test_0.png"
            page id=1 file="test_zh_1.png"
            chars count=2
            char id=65    x=1  y=2  width=6  height=8  xoffset=0 yoffset=3 xadvance=7  page=0 chnl=15
            char id=20013 x=10 y=20 width=12 height=12 xoffset=1 yoffset=1 xadvance=13 page=1 chnl=15
            """;

    @Test
    void everyPageIsReadAndEachGlyphKeepsItsPage() {
        BitmapFont font = new BitmapFont("graphics/fonts/test.fnt");
        font.installMetrics(TWO_PAGES);

        assertEquals(2, font.pageCount());
        assertEquals("graphics/fonts/test_0.png", font.pagePath());
        assertEquals("graphics/fonts/test_0.png", font.pagePath(0));
        assertEquals("graphics/fonts/test_zh_1.png", font.pagePath(1));
        assertNull(font.pagePath(2));
        assertEquals(0, font.glyph('A').page);
        assertEquals(1, font.glyph('中').page);
        assertEquals(10, font.glyph('中').x);
        assertEquals(20f, font.measureWidth("A中"));
    }

    @Test
    void aSinglePageManifestKeepsItsOnePageAndGlyphsDefaultToIt() {
        BitmapFont font = new BitmapFont("graphics/fonts/one.fnt");
        font.installMetrics("""
                common lineHeight=14 base=11 scaleW=64 scaleH=64
                page id=0 file="one_0.png"
                char id=65 x=1 y=2 width=6 height=8 xoffset=0 yoffset=3 xadvance=7
                """);

        assertEquals(1, font.pageCount());
        assertEquals("graphics/fonts/one_0.png", font.pagePath());
        assertEquals(0, font.glyph('A').page);
    }
}
