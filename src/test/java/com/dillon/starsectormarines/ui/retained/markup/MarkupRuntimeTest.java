package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkupRuntimeTest {

    @Test
    void shippedWorkbenchBuildsTheExpectedOrdinaryElementTree() throws Exception {
        String path = "mod/data/ui/components/dev/ui-workbench.mlx";
        MarkupLoader loader = new MarkupLoader(file -> Files.readString(Path.of(file)), List.of(path));
        loader.reload();

        try (MarkupInstance instance = loader.build("ui-workbench", Map.of())) {
            assertEquals(UiTag.DIV, instance.root().tag());
            assertEquals("workbench-root", instance.root().id());
            assertEquals(3, instance.root().childCount());
            assertEquals(UiTag.BUTTON, instance.requireElement("theme-contrast").tag());
            assertEquals(UiTag.CANVAS, instance.requireElement("transaction-canvas").tag());
            assertEquals(900, instance.requireElement("transaction-canvas").canvasWidth());
            assertTrue(instance.requireElement("state-disabled").disabled());
            assertTrue(instance.requireElement("title").hasClass("mlx-ui-workbench"));
        }
    }

    @Test
    void signalsAndHandlersBindThroughTypedProps() {
        Reactor values = new Reactor();
        MutableSignal<String> label = values.signal("First");
        AtomicInteger clicks = new AtomicInteger();
        String source = """
                <template props="label, act">
                  <button id="action" onclick="{act}">Choice: {label}</button>
                </template>
                """;
        MarkupLoader loader = loader("bound-view.mlx", source);

        try (MarkupInstance instance = loader.build(values, "bound-view",
                Map.of("label", label, "act", (Runnable) clicks::incrementAndGet))) {
            UiElement action = instance.requireElement("action");
            assertEquals("Choice: First", action.text());

            label.set("Second");
            assertEquals(1, instance.flush());
            assertEquals("Choice: Second", action.text());

            UiDocument document = new UiDocument(action);
            document.layout(100f, 40f);
            document.pointerDown(20f, 20f);
            document.pointerUp(20f, 20f);
            assertEquals(1, clicks.get());
        }
    }

    @Test
    void keyedRowsKeepTheirElementIdentityAcrossReorderAndDataReplacement() {
        Reactor values = new Reactor();
        MutableSignal<List<Row>> rows = values.signal(List.of(new Row("a", "Alpha"), new Row("b", "Bravo")));
        MarkupLoader loader = loader("list-view.mlx", """
                <template props="items">
                  <div id="list">
                    <button each="item in items" key="{item.id}" id="{item.id}">{item.label}</button>
                  </div>
                </template>
                """);

        try (MarkupInstance instance = loader.build(values, "list-view", Map.of("items", rows))) {
            UiElement list = instance.requireElement("list");
            UiElement alpha = list.childAt(0);
            UiElement bravo = list.childAt(1);

            rows.set(List.of(new Row("b", "Bravo updated"), new Row("a", "Alpha")));
            instance.flush();

            assertSame(bravo, list.childAt(0));
            assertSame(alpha, list.childAt(1));
            assertEquals("Bravo updated", bravo.text());
        }
    }

    @Test
    void failedReloadLeavesThePreviousTemplateUsable() {
        AtomicReference<String> source = new AtomicReference<>(
                "<template><div id=\"root\">Stable</div></template>");
        MarkupLoader loader = new MarkupLoader(path -> source.get(), List.of("reload-view.mlx"));
        loader.reload();
        source.set("<template><unknown id=\"root\" /></template>");

        assertThrows(UiMarkupException.class, loader::reload);
        try (MarkupInstance instance = loader.build("reload-view", Map.of())) {
            assertEquals("Stable", instance.root().text());
        }
    }

    @Test
    void failedPartialBuildClosesBindingsItAlreadyCreated() {
        Reactor reactor = new Reactor();
        MutableSignal<String> label = reactor.signal("Tracked");
        MarkupLoader loader = loader("broken-view.mlx", """
                <template props="label">
                  <div id="root">
                    <div id="bound">{label}</div>
                    <div id="invalid" disabled="" />
                  </div>
                </template>
                """);

        assertThrows(UiMarkupException.class,
                () -> loader.build(reactor, "broken-view", Map.of("label", label)));
        assertEquals(0, label.subscriberCount());
    }

    @Test
    void buildTimeReloadRefusalKeepsThePreviousRegistry() {
        Reactor reactor = new Reactor();
        AtomicReference<String> source = new AtomicReference<>(
                "<template><div id=\"root\">Stable</div></template>");
        MarkupLoader loader = new MarkupLoader(path -> source.get(), List.of("reload-view.mlx"));
        loader.reload();
        source.set("<template><div id=\"root\" unknown=\"value\">Edited</div></template>");

        assertThrows(UiMarkupException.class,
                () -> loader.reloadAndBuild(reactor, "reload-view", Map.of()));
        try (MarkupInstance instance = loader.build("reload-view", Map.of())) {
            assertEquals("Stable", instance.root().text());
        }
    }

    @Test
    void preparedReloadDoesNotReplaceTemplatesBeforeHostWiringCommits() {
        Reactor reactor = new Reactor();
        AtomicReference<String> source = new AtomicReference<>(
                "<template><div id=\"root\">Stable</div></template>");
        MarkupLoader loader = new MarkupLoader(path -> source.get(), List.of("reload-view.mlx"));
        loader.reload();
        source.set("<template><div id=\"root\">Candidate</div></template>");

        MarkupLoader.PreparedReload prepared = loader.prepareReload(
                reactor, "reload-view", Map.of());
        assertEquals("Candidate", prepared.instance().root().text());
        prepared.instance().close();

        try (MarkupInstance installed = loader.build("reload-view", Map.of())) {
            assertEquals("Stable", installed.root().text());
        }
    }

    private static MarkupLoader loader(String path, String source) {
        MarkupLoader loader = new MarkupLoader(ignored -> source, List.of(path));
        loader.reload();
        return loader;
    }

    private record Row(String id, String label) { }
}
