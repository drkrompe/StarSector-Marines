package com.dillon.starsectormarines.ui.retained.markup;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkupParserTest {

    @Test
    void shippedWorkbenchParsesAsOneScopedComponent() throws Exception {
        String path = "mod/data/ui/components/dev/ui-workbench.mlx";
        MarkupTemplate template = MarkupParser.parse(path, Files.readString(Path.of(path)));

        assertEquals("ui-workbench", template.name());
        assertEquals("workbench-root", template.root().attribute("id").value());
        assertEquals(3, template.root().children().size());
        assertTrue(template.style().rules().stream().allMatch(rule -> rule.selectors().stream()
                .allMatch(selector -> selector.toString().contains("mlx-ui-workbench"))));
    }

    @Test
    void unknownElementReportsItsExactSourcePosition() {
        UiMarkupException failure = assertThrows(UiMarkupException.class, () -> MarkupParser.parse(
                "bad-view.mlx", """
                        <template>
                          <div id="root">
                            <span id="mistake" />
                          </div>
                        </template>
                        """));

        assertTrue(failure.getMessage().contains("bad-view.mlx:3:5"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Unknown element <span>"), failure.getMessage());
        assertTrue(failure.getMessage().contains("div, button, input, canvas, and img"),
                failure.getMessage());
    }

    @Test
    void imagesAreSelfClosingBuiltIns() {
        MarkupTemplate template = MarkupParser.parse("icon-view.mlx", """
                <template props="icon">
                  <div id="root">
                    <img id="badge" src="{icon}" />
                  </div>
                </template>
                """);

        assertEquals("img", ((MarkupElement) template.root().children().get(0)).tagName());

        UiMarkupException failure = assertThrows(UiMarkupException.class, () -> MarkupParser.parse(
                "bad-view.mlx", """
                        <template>
                          <div id="root">
                            <img id="badge" src="graphics/icon.png"></img>
                          </div>
                        </template>
                        """));
        assertTrue(failure.getMessage().contains("<img> takes no children"), failure.getMessage());
    }

    @Test
    void expressionsCanOnlyReadDeclaredWholeValuePaths() {
        UiMarkupException undeclared = assertThrows(UiMarkupException.class, () -> MarkupParser.parse(
                "bad-view.mlx", "<template><div id=\"root\">{missing.value}</div></template>"));
        UiMarkupException arithmetic = assertThrows(UiMarkupException.class, () -> MarkupParser.parse(
                "bad-view.mlx", "<template props=\"n\"><div id=\"root\">{n + 1}</div></template>"));

        assertTrue(undeclared.getMessage().contains("not in scope"), undeclared.getMessage());
        assertTrue(arithmetic.getMessage().contains("dotted path"), arithmetic.getMessage());
    }
}
