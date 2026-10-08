package com.dillon.starsectormarines.ui.retained.svg;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SvgAssetLoadingTest {
    private static final String SVG = """
            <svg viewBox="0 0 10 10"><rect width="10" height="10"/></svg>
            """;

    @Test
    void imageBindingCompilesSvgOnceAndClearsItWhenSourceChanges() {
        AtomicInteger reads = new AtomicInteger();
        SvgAsset.Source previous = SvgAsset.setSource(path -> {
            reads.incrementAndGet();
            return SVG;
        });
        try {
            UiElement image = new UiElement("hardware").tag(UiTag.IMAGE).imageSource("plate.svg");
            assertNotNull(image.svgImage());
            image.imageSource("plate.svg");
            assertEquals(1, reads.get());
            image.imageSource("portrait.png");
            assertNull(image.svgImage());
            image.imageSource(null);
            assertNull(image.imageSource());
            assertEquals(1, reads.get());
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void cachesSuccessfulLoadsButInvalidatesWhenTheHostChanges() {
        AtomicInteger reads = new AtomicInteger();
        SvgAsset.Source previous = SvgAsset.setSource(path -> {
            reads.incrementAndGet();
            assertEquals("symbol.svg", path);
            return SVG;
        });
        try {
            SvgAsset first = SvgAsset.load("symbol.svg");
            assertSame(first, SvgAsset.load("symbol.svg"));
            assertEquals(1, reads.get());
            SvgAsset.setSource(path -> SVG);
            assertNotSame(first, SvgAsset.load("symbol.svg"));
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void readerFailureNamesTheAssetAndDoesNotPoisonTheCache() {
        AtomicInteger reads = new AtomicInteger();
        Exception missing = new Exception("missing resource");
        SvgAsset.Source previous = SvgAsset.setSource(path -> {
            if (reads.incrementAndGet() == 1) throw missing;
            return SVG;
        });
        try {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> SvgAsset.load("missing.svg"));
            assertTrue(failure.getMessage().contains("missing.svg"));
            assertSame(missing, failure.getCause());
            assertNotNull(SvgAsset.load("missing.svg"));
            assertEquals(2, reads.get());
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    @Test
    void defaultReaderUsesStarsectorResourceAccess() {
        SettingsAPI previousSettings = Global.getSettings();
        SvgAsset.Source previousSource = SvgAsset.setSource(null);
        AtomicInteger reads = new AtomicInteger();
        SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(
                SettingsAPI.class.getClassLoader(), new Class<?>[] {SettingsAPI.class},
                (proxy, method, args) -> {
                    assertEquals("loadText", method.getName());
                    assertEquals("graphics/ui/example.svg", args[0]);
                    reads.incrementAndGet();
                    return SVG;
                });
        try {
            Global.setSettings(settings);
            assertNotNull(SvgAsset.load("graphics/ui/example.svg"));
            assertEquals(1, reads.get());
        } finally {
            Global.setSettings(previousSettings);
            SvgAsset.setSource(previousSource);
        }
    }
}
