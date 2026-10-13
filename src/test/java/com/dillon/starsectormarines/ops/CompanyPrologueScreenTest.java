package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CompanyPrologueScreenTest {
    @Test void fallbackAndMissingPortNeverGiveTheJangalaContactOrADisabledExit() {
        var fallback = CompanyPrologueScreen.model(3, "Independent Port", false, 17, 500, 5000, () -> {}, () -> {}, () -> {});
        assertFalse(fallback.get("narrative").toString().contains("Corvus"));
        assertFalse(fallback.get("dialogue").toString().contains("Jangala"));
        assertFalse(fallback.get("dialogue").toString().contains("Nessa"));
        assertEquals("Reach Independent Port", fallback.get("destination"));
        assertTrue(fallback.get("manifest").toString().contains("17 crew"));
        var absent = CompanyPrologueScreen.model(5, null, false, 17, 500, 5000, () -> {}, () -> {}, () -> {});
        assertEquals("Take command", absent.get("nextLabel"));
        assertFalse(absent.get("dialogue").toString().contains("null"));
        assertFalse(absent.get("dialogue").toString().contains("Nessa"));
        var fallbackHandoff = CompanyPrologueScreen.model(5, "Independent Port", false, 17, 500, 5000, () -> {}, () -> {}, () -> {});
        assertFalse(fallbackHandoff.get("dialogue").toString().contains("Nessa"));
    }

    @Test void allPagesKeepNavigationClickableAt1920x1080And100Percent() throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), CompanyPrologueScreen.COMPONENTS);
        loader.reload();
        for (int page = 0; page < 6; page++) {
            var next = new AtomicInteger(); var back = new AtomicInteger(); var skip = new AtomicInteger();
            var props = CompanyPrologueScreen.model(page, "Jangala", true, 20, 500, 5000,
                    next::incrementAndGet, back::incrementAndGet, skip::incrementAndGet);
            try (var instance = loader.build(new Reactor(), CompanyPrologueScreen.ROOT, props)) {
                var document = new UiDocument(instance.root());
                instance.styles().forEach(document::addStyleSheet);
                document.theme(MarineOpsThemes.standard());
                document.layout(1920, 1080);
                click(document, instance, "prologue-next", 1080);
                click(document, instance, "prologue-skip", 1080);
                if (page > 0) click(document, instance, "prologue-back", 1080);
                assertEquals(1, next.get()); assertEquals(1, skip.get());
                assertEquals(page > 0 ? 1 : 0, back.get());
                var reading = instance.requireElement("prologue-reading").box().borderBox();
                var controls = instance.requireElement("prologue-controls").box().borderBox();
                assertTrue(reading.height() > 200);
                assertTrue(reading.bottom() <= controls.y());
            }
        }
    }
    private void click(UiDocument doc, MarkupInstance instance, String id, float height) {
        var box = instance.requireElement(id).box().borderBox();
        assertTrue(box.width() > 0 && box.height() > 0);
        assertTrue(box.y() >= 0 && box.bottom() <= height, id);
        float x = box.x() + box.width() / 2, y = box.y() + box.height() / 2;
        assertTrue(doc.pointerDown(x, y), id);
        assertTrue(doc.pointerUp(x, y), id);
    }
}
