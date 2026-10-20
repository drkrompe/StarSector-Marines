package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.start.CompanyPrologue;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CompanyPrologueScreenTest {
    @Test void eachBeatHasDistinctBoundedShippedArtwork() throws Exception {
        var images = new HashSet<String>();
        long bytes = 0;
        for (int page = 0; page < CompanyPrologue.PAGE_COUNT; page++) {
            var props = CompanyPrologueScreen.model(page, "Jangala", 20, 473, 4862, () -> {}, () -> {}, () -> {});
            String path = (String) props.get("image");
            assertTrue(images.add(path), "Each story beat needs its own illustration");
            var file = Path.of("mod", path);
            var image = ImageIO.read(file.toFile());
            assertEquals(1200, image.getWidth());
            assertEquals(800, image.getHeight());
            assertTrue(Files.size(file) <= 400 * 1024);
            bytes += Files.size(file);
            assertFalse(props.get("caption").toString().isBlank());
        }
        for (var stage : CompanyYardReview.Stage.values()) {
            var props = CompanyYardScreen.model(stage, "Second Wind", 473, 4862, null, null, true,
                    () -> {}, () -> {}, () -> {});
            String path = (String) props.get("image");
            if (!images.add(path)) continue;
            var file = Path.of("mod", path);
            var image = ImageIO.read(file.toFile());
            assertEquals(1200, image.getWidth());
            assertEquals(800, image.getHeight());
            assertTrue(Files.size(file) <= 400 * 1024);
            bytes += Files.size(file);
        }
        assertTrue(bytes <= 3 * 1024 * 1024, "The opening and yard art have a bounded distribution cost");
    }
    @Test void anotherPortTellsJangalasStoryButNamesItsOwnCourse() {
        var elsewhere = CompanyPrologueScreen.model(5, "Independent Port", false, 17, 473, 4862, () -> {}, () -> {}, () -> {});
        assertTrue(elsewhere.get("narrative").toString().contains("Nessa Venn, Jangala"));
        assertTrue(elsewhere.get("dialogue").toString().contains("Nessa"));
        assertEquals("Reach Independent Port", elsewhere.get("destination"));
        assertTrue(elsewhere.get("manifest").toString().contains("17 crew"));
        var handoff = CompanyPrologueScreen.model(7, "Independent Port", false, 17, 473, 4862, () -> {}, () -> {}, () -> {});
        assertTrue(handoff.get("dialogue").toString().contains("Nessa"));
        assertEquals("Set course for Independent Port", handoff.get("nextLabel"));
    }

    @Test void noPortAtAllNeverGivesADisabledExit() {
        var absent = CompanyPrologueScreen.model(7, null, false, 17, 473, 4862, () -> {}, () -> {}, () -> {});
        assertEquals("Take command", absent.get("nextLabel"));
        assertFalse(absent.get("dialogue").toString().contains("null"));
        assertFalse(absent.get("dialogue").toString().contains("Nessa"));
    }

    @Test void jangalaPageSixPromisesTheMemoryOnlyWhileTheArrivalIsAhead() {
        var promised = CompanyPrologueScreen.model(5, "Jangala", true, 20, 473, 4862, () -> {}, () -> {}, () -> {});
        assertTrue(promised.get("dialogue").toString().contains("tell you on the way down"));
        assertFalse(promised.get("dialogue").toString().contains("ran ore together"));
        var withheld = CompanyPrologueScreen.model(5, "Jangala", false, 20, 473, 4862, () -> {}, () -> {}, () -> {});
        assertTrue(withheld.get("dialogue").toString().contains("ran ore together"));
    }

    @Test void allPagesKeepNavigationClickableAt1920x1080And100Percent() throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), CompanyPrologueScreen.COMPONENTS);
        loader.reload();
        for (int page = 0; page < CompanyPrologue.PAGE_COUNT; page++) {
            var next = new AtomicInteger(); var back = new AtomicInteger(); var skip = new AtomicInteger();
            var props = CompanyPrologueScreen.model(page, "Jangala", 20, 473, 4862,
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
    @Test void yardIsAStraightClickThroughWithBackOffUntilPastThePaidPage() throws Exception {
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), CompanyPrologueScreen.COMPONENTS);
        loader.reload();
        for (var stage : CompanyYardReview.Stage.values()) {
            for (boolean refused : new boolean[]{false, true}) {
                var next = new AtomicInteger(); var back = new AtomicInteger(); var leave = new AtomicInteger();
                var props = CompanyYardScreen.model(stage, "Second Wind", 473, 4862,
                        refused ? "The yard is unavailable." : null, null, true,
                        next::incrementAndGet, back::incrementAndGet, leave::incrementAndGet);
                try (var instance = loader.build(new Reactor(), CompanyPrologueScreen.ROOT, props)) {
                    var document = new UiDocument(instance.root());
                    instance.styles().forEach(document::addStyleSheet);
                    document.theme(MarineOpsThemes.standard());
                    document.layout(1920, 1080);
                    var box = instance.requireElement("prologue-next").box().borderBox();
                    assertTrue(box.y() >= 0 && box.bottom() <= 1080);
                    float x = box.x() + box.width() / 2, y = box.y() + box.height() / 2;
                    document.pointerDown(x, y); document.pointerUp(x, y);
                    assertEquals(refused && stage == CompanyYardReview.Stage.COST ? 0 : 1, next.get());
                    var skip = instance.requireElement("prologue-skip").box().borderBox();
                    assertTrue(skip.width() == 0 || skip.height() == 0, "The yard has no way out but through");
                    if (CompanyYardReview.backAllowed(stage)) {
                        click(document, instance, "prologue-back", 1080);
                        assertEquals(1, back.get());
                    } else {
                        var b = instance.requireElement("prologue-back").box().borderBox();
                        document.pointerDown(b.x() + b.width() / 2, b.y() + b.height() / 2);
                        document.pointerUp(b.x() + b.width() / 2, b.y() + b.height() / 2);
                        assertEquals(0, back.get());
                    }
                    assertEquals(0, leave.get());
                }
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
