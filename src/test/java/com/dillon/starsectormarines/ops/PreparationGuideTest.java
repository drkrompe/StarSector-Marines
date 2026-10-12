package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiKey;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        document.layout(1200, 800); guide.update();
        assertNull(find(document.root(), "preparation-later"));
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
