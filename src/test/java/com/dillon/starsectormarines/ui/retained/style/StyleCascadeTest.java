package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleCascadeTest {

    @Test
    void tagClassIdDescendantAndPseudoSelectorsUseOrderedCascade() {
        UiElement button = new UiElement("confirm")
                .tag(UiTag.BUTTON)
                .addClass("action")
                .preferredSize(80f, 40f);
        UiElement panel = new UiElement("panel")
                .addClass("panel")
                .layout(UiLayout.STACK)
                .child(button);
        UiDocument document = new UiDocument(panel).theme(theme("""
                button { background-color: #101010; }
                #confirm { background-color: #202020; }
                .action { background-color: #303030; }
                .panel button { border-color: #778899; border-width: 1px; }
                button:hover { background-color: #405060; }
                """));

        document.layout(200f, 100f);
        assertEquals(new Color(0x30, 0x30, 0x30), button.background());
        assertEquals(new Color(0x77, 0x88, 0x99), button.borderColor());

        document.pointerMoved(20f, 20f);
        UiDocument.FrameStats frame = document.advance(0f);

        assertEquals(new Color(0x40, 0x50, 0x60), button.background());
        assertEquals(0, frame.layoutPasses());
        assertTrue(frame.resolvedStyles() > 0);
    }

    @Test
    void inheritedColorAndInlineStyleBeatTheFinalTheme() {
        UiElement child = new UiElement("child")
                .style("background-color: #abcdef; color: #fedcba");
        UiElement root = new UiElement("root").child(child);
        UiDocument document = new UiDocument(root).theme(theme("""
                :root { color: #112233; }
                #child { background-color: #010203; }
                """));

        document.layout(100f, 100f);

        assertEquals(new Color(0xAB, 0xCD, 0xEF), child.background());
        assertEquals(new Color(0xFE, 0xDC, 0xBA), child.textColor());
    }

    @Test
    void resettingInlineStyleRemovesPropertiesThatAreNoLongerPresent() {
        UiElement child = new UiElement("child")
                .preferredWidth(42f)
                .style("background-color: #abcdef; color: #fedcba");
        UiDocument document = new UiDocument(child).theme(theme("""
                :root { color: #112233; background-color: #010203; }
                """));
        document.layout(100f, 100f);

        child.style("color: #334455");
        document.advance(0f);

        assertEquals(new Color(0x01, 0x02, 0x03), child.background());
        assertEquals(new Color(0x33, 0x44, 0x55), child.textColor());
        assertEquals(42f, child.preferredWidth());
    }

    @Test
    void componentScopeNarrowsOnlyTheSubjectAndThemeStillWinsLast() {
        UiElement name = new UiElement("name").addClass("name").addClass("mlx-row");
        UiElement panel = new UiElement("panel").addClass("panel").child(name);
        StyleSheet component = StyleSheet.parse("row", """
                .panel .name { background-color: #112233; }
                """).scopedTo("mlx-row");
        UiDocument document = new UiDocument(panel)
                .addStyleSheet(component)
                .theme(theme(".name { background-color: #445566; }"));

        document.layout(100f, 100f);

        assertEquals(new Color(0x44, 0x55, 0x66), name.background());
    }

    @Test
    void replacingTheNamedThemeReskinsTheSameTreeAndIdleFrameDoesNoWork() {
        UiElement button = new UiElement("button").tag(UiTag.BUTTON);
        UiElement root = new UiElement("root").child(button);
        UiDocument document = new UiDocument(root).theme(theme("""
                button { background-color: #102030; }
                """));
        document.layout(100f, 100f);
        int passes = document.layoutPasses();

        UiDocument.FrameStats idle = document.advance(0.25f);
        assertEquals(0, idle.resolvedStyles());
        assertEquals(0, idle.layoutPasses());
        assertEquals(passes, document.layoutPasses());

        document.replaceStyleSheet(StyleSheet.parse("marine-ops-theme", """
                button { background-color: #a0b0c0; }
                """));
        document.advance(0f);

        assertSame(button, root.children().get(0));
        assertEquals(new Color(0xA0, 0xB0, 0xC0), button.background());
    }

    @Test
    void unsupportedCssIsRefusedByName() {
        assertThrows(UiStyleException.class,
                () -> StyleSheet.parse("bad", "div { z-index: 4; }"));
        assertThrows(UiStyleException.class,
                () -> StyleSheet.parse("bad", "div:visited { color: #fff; }"));
        assertThrows(UiStyleException.class,
                () -> StyleSheet.parse("bad", "@media (min-width: 2px) { div { color: #fff; } }"));
        assertThrows(UiStyleException.class,
                () -> StyleDeclaration.parse("color: #fff !important"));
        assertThrows(UiStyleException.class,
                () -> StyleDeclaration.parse("width: 12"));
        assertThrows(UiStyleException.class,
                () -> StyleDeclaration.parse("overflow: auto"));
    }

    @Test
    void cssEightDigitHexUsesRgbaOrder() {
        UiElement root = new UiElement("root").style("background-color: #ffffff14");
        new UiDocument(root).layout(10f, 10f);

        assertEquals(new Color(255, 255, 255, 0x14), root.background());
    }

    @Test
    void standardControlStatesComeFromSemanticStateAndSelectedClass() {
        UiElement focus = stateButton("focus");
        UiElement active = stateButton("active");
        UiElement selected = stateButton("selected").selected(true);
        UiElement disabled = stateButton("disabled").disabled(true);
        UiElement root = new UiElement("root")
                .layout(UiLayout.ROW)
                .child(focus)
                .child(active)
                .child(selected)
                .child(disabled);
        UiDocument document = new UiDocument(root).theme(theme("""
                button { background-color: #000000; opacity: 1; }
                button:focus-visible { background-color: #113355; }
                button:active { background-color: #775511; }
                button.selected { background-color: #117733; }
                button:disabled { opacity: 0.25; }
                """));
        document.layout(160f, 40f);

        document.requestFocus(focus, true);
        document.advance(0f);
        assertEquals(new Color(0x11, 0x33, 0x55), focus.background());
        assertEquals(new Color(0x11, 0x77, 0x33), selected.background());
        assertEquals(0.25f, disabled.opacity(), 0.001f);

        document.pointerDown(60f, 20f);
        document.advance(0f);
        assertEquals(new Color(0x77, 0x55, 0x11), active.background());
    }

    @Test
    void movingInsideTheSameTargetDoesNotRepeatStyleWork() {
        UiElement button = stateButton("button");
        UiElement root = new UiElement("root").layout(UiLayout.STACK).child(button);
        UiDocument document = new UiDocument(root).theme(theme(
                "button:hover { background-color: #fff; }"));
        document.layout(100f, 100f);
        document.pointerMoved(10f, 10f);
        document.advance(0f);

        document.pointerMoved(20f, 20f);
        UiDocument.FrameStats idleMove = document.advance(0f);

        assertEquals(0, idleMove.resolvedStyles());
        assertEquals(0, idleMove.layoutPasses());
    }

    @Test
    void layoutChangeRefreshesHoverForAStationaryPointer() {
        UiElement spacer = new UiElement("spacer").addClass("spacer");
        UiElement button = stateButton("button");
        UiElement root = new UiElement("root")
                .layout(UiLayout.ROW)
                .child(spacer)
                .child(button);
        UiDocument document = new UiDocument(root).theme(theme("""
                .spacer { width: 20px; }
                .spacer.expanded { width: 70px; }
                """));
        document.layout(140f, 40f);
        document.pointerMoved(50f, 20f);
        assertTrue(button.hovered());

        spacer.addClass("expanded");
        document.advance(0f);

        assertTrue(!button.hovered());
    }

    private static UiElement stateButton(String id) {
        return new UiElement(id)
                .tag(UiTag.BUTTON)
                .preferredWidth(40f)
                .onClick(() -> { });
    }

    private static UiTheme theme(String css) {
        return new UiTheme(StyleSheet.parse("marine-ops-theme", css), Map.of());
    }
}
