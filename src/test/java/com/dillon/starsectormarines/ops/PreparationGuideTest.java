package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PreparationGuideTest {
    @Test void escapeCannotHideRequiredLessonAndOnlyRealOrderAdvancesIt() {
        var complete = new AtomicBoolean();
        var exits = new AtomicInteger();
        var blockedClicks = new AtomicInteger();
        var target = button("required-order", 20, () -> complete.set(true));
        var exit = button("page-nav-return", 920, exits::incrementAndGet);
        var blocked = button("unrelated", 720, blockedClicks::incrementAndGet);
        var document = new UiDocument(new UiElement("root").child(target).child(exit).child(blocked)).theme(MarineOpsThemes.standard());
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        var guide = new PreparationGuide(document, () -> complete.get() ? null
                : new PreparationGuide.Lesson(0, "REQUIRED ORDER", "Use the highlighted order to continue.",
                List.of("required-order"), null, null), exits::incrementAndGet, loader);
        document.layout(1920, 1080); guide.update();
        assertTrue(find(document.root(), "preparation-later").hasClass("preparation-hidden"));
        document.keyPressed(UiKey.ESCAPE, Set.of(), false); guide.update();
        assertEquals(0, exits.get());
        click(document, blocked);
        assertEquals(0, blockedClicks.get(), "Escape must not expose unrelated controls");
        click(document, exit);
        assertEquals(1, exits.get(), "Travel/resupply remains accessible without skipping the lesson");
        assertFalse(complete.get());
        click(document, target); guide.update();
        assertTrue(complete.get(), "Real highlighted order remains clickable through the dimming");
        click(document, blocked);
        assertEquals(1, blockedClicks.get(), "Completion releases the spotlight");
        document.keyPressed(UiKey.ESCAPE, Set.of(), false);
        assertEquals(2, exits.get());
    }

    @Test void dialogueActionRemainsClickableAndReleasesTheSpotlight() {
        var complete = new AtomicBoolean();
        var document = PreparationGuide.document(new UiElement("content"));
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        var guide = new PreparationGuide(document, () -> complete.get() ? null
                : new PreparationGuide.Lesson(3, "TWO SQUADS", "Check the real stores first.",
                        List.of(), "UNDERSTOOD", () -> complete.set(true)), () -> {}, loader);
        document.theme(MarineOpsThemes.standard());
        document.layout(1920, 1080); guide.update();
        click(document, find(document.root(), "preparation-continue")); guide.update();
        assertTrue(complete.get(), "The dialogue must accept input independently of its dim backdrop");
    }

    @Test void campaignPopoverLeavesNavigationVisibleAndRestrictsUnrelatedControls() {
        var worldOrders = new AtomicInteger();
        var navigation = new UiElement("marine-ops-page-nav").style("position: absolute; left: 0px; top: 0px; width: 1920px; height: 60px;");
        var target = new UiElement("workspace").style("position: absolute; left: 450px; top: 200px; width: 1000px; height: 800px;");
        var world = button("world-order", 1200, worldOrders::incrementAndGet);
        var document = new UiDocument(new UiElement("root").child(navigation).child(target).child(world)).theme(MarineOpsThemes.standard());
        var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                List.of("data/ui/components/company/preparation-guide.mlx"));
        var guide = new PreparationGuide(document, () -> new PreparationGuide.Lesson(1,
                "FITTING", "Practice on the live controls.", List.of("workspace", "marine-ops-page-nav"), null, null), () -> {}, loader);
        document.layout(1920, 1080); guide.update();
        assertTrue(guide.calloutBounds().y() >= navigation.box().borderBox().bottom());
        click(document, world); assertEquals(0, worldOrders.get());
    }

    @Test void calloutShrinksToShortCopyAndCapsLongCopy() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var lesson = new PreparationGuide.Lesson[1];
            var document = PreparationGuide.document(new UiElement("content")).theme(fixedWidthTheme());
            var guide = new PreparationGuide(document, () -> lesson[0], () -> {},
                    new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                            List.of("data/ui/components/company/preparation-guide.mlx")));
            document.layout(1920, 1080);
            lesson[0] = new PreparationGuide.Lesson(1, "TITLE", "One short line.", List.of(), null, null, 280);
            guide.update();
            float oneLine = guide.calloutBounds().height();
            assertTrue(oneLine < 200, "A one-line lesson leaves no empty card: " + oneLine);
            lesson[0] = new PreparationGuide.Lesson(1, "TITLE", "One short line. ".repeat(8), List.of(), null, null, 280);
            guide.update();
            float wrapped = guide.calloutBounds().height();
            assertTrue(wrapped > oneLine && wrapped < 280, "Wrapped copy grows the card: " + wrapped);
            lesson[0] = new PreparationGuide.Lesson(1, "TITLE", "One short line. ".repeat(200), List.of(), "GO", () -> {}, 280);
            guide.update();
            assertEquals(280f, guide.calloutBounds().height(), .5f);
        }
    }

    @Test void dockedLessonSitsInItsDocksCornerAndOtherwiseSearches() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var dock = new UiElement("quiet-corner").style("position: absolute; left: 600px; top: 200px; width: 700px; height: 600px;");
            var document = new UiDocument(new UiElement("root").child(dock)).theme(fixedWidthTheme());
            var docked = new boolean[]{true};
            var guide = new PreparationGuide(document, () -> new PreparationGuide.Lesson(1, "TITLE", "One short line.",
                    List.of(), null, null, 280, null, null, docked[0] ? "quiet-corner" : null), () -> {},
                    new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                            List.of("data/ui/components/company/preparation-guide.mlx")));
            document.layout(1920, 1080); guide.update();
            assertEquals(612f, guide.calloutBounds().x(), .5f);
            assertEquals(208f, guide.calloutBounds().y(), .5f);
            dock.style("position: absolute; left: 600px; top: 200px; width: 300px; height: 600px;");
            document.advance(0f); guide.update();
            assertNotEquals(612f, guide.calloutBounds().x(), "A dock too small for the callout falls back to the search");
        }
    }

    /** Ten pixels a glyph, twenty a line: the shared theme fonts keep the game's metrics untouched. */
    private static UiTheme fixedWidthTheme() {
        var manifest = new StringBuilder("common lineHeight=20 base=16 scaleW=256 scaleH=256\npage id=0 file=\"test.png\"\n");
        for (int c = 32; c < 127; c++) manifest.append("char id=").append(c).append(" width=10 height=20 xadvance=10\n");
        var theme = MarineOpsThemes.standard();
        Map<String, BitmapFont> fonts = new HashMap<>();
        for (String family : theme.fonts().keySet()) {
            var font = new BitmapFont("test.fnt");
            font.installMetrics(manifest.toString());
            fonts.put(family, font);
        }
        return new UiTheme(theme.sheet(), fonts);
    }

    private static UiElement button(String id, int left, Runnable action) {
        return new UiElement(id).tag(UiTag.BUTTON).onClick(action)
                .style("position: absolute; top: 700px; left: " + left + "px; width: 120px; height: 40px;");
    }
    private static void click(UiDocument document, UiElement element) {
        var bounds = element.box().borderBox();
        document.pointerDown(bounds.x() + 10, bounds.y() + 10);
        document.pointerUp(bounds.x() + 10, bounds.y() + 10);
    }
    private static UiElement find(UiElement element, String id) {
        if (id.equals(element.id())) return element;
        for (UiElement child : element.children()) {
            UiElement found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
