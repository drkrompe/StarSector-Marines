package com.dillon.starsectormarines.ui.retained.markup;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkupRuntimeTest {

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
    void retainedInputEditsAReactiveStringWithoutHostReflection() {
        Reactor values = new Reactor();
        MutableSignal<String> name = values.signal("Line Issue");
        MarkupLoader loader = loader("input-view.mlx", """
                <template props="name, editName">
                  <input id="name" value="{name}" maxlength="16" oninput="{editName}" />
                </template>
                """);

        try (MarkupInstance instance = loader.build(values, "input-view",
                Map.of("name", name, "editName",
                        (java.util.function.Consumer<String>) name::set))) {
            UiElement input = instance.requireElement("name");
            assertEquals(UiTag.INPUT, input.tag());
            UiDocument document = new UiDocument(input);
            document.layout(200f, 30f);
            document.requestFocus(input, true);

            assertTrue(document.characterTyped('!'));
            assertEquals("Line Issue!", name.peek());
            assertTrue(document.keyPressed(
                    com.dillon.starsectormarines.ui.retained.UiKey.BACKSPACE,
                    java.util.Set.of(), false));
            assertEquals("Line Issue", name.peek());
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
    void dottedPathsRequireAnExplicitPropertySurface() {
        record HiddenRow(String id) { }
        Reactor values = new Reactor();
        MutableSignal<List<HiddenRow>> rows = values.signal(List.of(new HiddenRow("hidden")));
        MarkupLoader loader = loader("list-view.mlx", """
                <template props="items">
                  <div id="list">
                    <div each="item in items" key="{item.id}" id="{item.id}">{item.id}</div>
                  </div>
                </template>
                """);

        UiMarkupException failure = assertThrows(UiMarkupException.class,
                () -> loader.build(values, "list-view", Map.of("items", rows)));
        assertTrue(failure.getMessage().contains("MarkupPropertySource"));
    }

    @Test
    void aComponentFirstUsedAfterTheBuildStillBringsItsStyle() {
        Map<String, String> files = Map.of(
                "stores-room.mlx", "<template props=\"rows\"><div id=\"root\">"
                        + "<row-card each=\"row in rows\" key=\"{row}\" /></div></template>",
                "row-card.mlx", "<template><div id=\"card\" class=\"card\">Row</div></template>"
                        + "<style>.card { width: 10px; }</style>");
        MarkupLoader loader = new MarkupLoader(files::get, List.of("row-card.mlx", "stores-room.mlx"));
        loader.reload();

        try (MarkupInstance instance = loader.build("stores-room", Map.of("rows", List.of()))) {
            assertEquals(2, instance.styles().size(), "the empty list's card style is in the document");
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

    @Test
    void imagesBindTheirSourceAndTreatNothingAsNothing() {
        Reactor values = new Reactor();
        MutableSignal<String> icon = values.signal("graphics/ui/armory/system-missile-pod.png");
        MarkupLoader loader = loader("icon-view.mlx", """
                <template props="icon">
                  <div id="root">
                    <img id="badge" src="{icon}" />
                    <img id="absent" />
                  </div>
                </template>
                """);

        try (MarkupInstance instance = loader.build(values, "icon-view", Map.of("icon", icon))) {
            UiElement badge = instance.requireElement("badge");
            assertEquals(UiTag.IMAGE, badge.tag());
            assertEquals("graphics/ui/armory/system-missile-pod.png", badge.imageSource());
            assertEquals(0, badge.childCount());
            assertNull(badge.text(), "an image is not a text node");
            assertNull(instance.requireElement("absent").imageSource());

            icon.set(null);
            assertEquals(1, instance.flush());
            assertNull(badge.imageSource(), "a pattern that carries nothing shows nothing");
        }
    }

    @Test
    void aKeptRowRunsItsReplacementDataHandler() {
        Reactor values = new Reactor();
        AtomicReference<String> clicked = new AtomicReference<>();
        MutableSignal<List<Action>> rows = values.signal(List.of(new Action("a", () -> clicked.set("first"))));
        MarkupLoader loader = loader("action-list.mlx", """
                <template props="items">
                  <div id="list">
                    <button each="item in items" key="{item.id}" id="{item.id}" onclick="{item.act}">Go</button>
                  </div>
                </template>
                """);

        try (MarkupInstance instance = loader.build(values, "action-list", Map.of("items", rows))) {
            UiElement row = instance.requireElement("a");
            rows.set(List.of(new Action("a", () -> clicked.set("second"))));
            instance.flush();

            assertSame(row, instance.requireElement("a"));
            UiDocument document = new UiDocument(row);
            document.layout(100f, 40f);
            document.pointerDown(20f, 20f);
            document.pointerUp(20f, 20f);
            assertEquals("second", clicked.get());
        }
    }

    private record Action(String id, Runnable act) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "act" -> act;
                default -> throw new IllegalArgumentException("Unknown action property");
            };
        }
    }

    private static MarkupLoader loader(String path, String source) {
        MarkupLoader loader = new MarkupLoader(ignored -> source, List.of(path));
        loader.reload();
        return loader;
    }

    private record Row(String id, String label) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String name) {
            return switch (name) {
                case "id" -> id;
                case "label" -> label;
                default -> throw new IllegalArgumentException("Unknown row property");
            };
        }
    }
}
