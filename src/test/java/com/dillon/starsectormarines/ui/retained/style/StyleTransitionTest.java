package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StyleTransitionTest {

    private static final float EPSILON = 0.001f;

    @Test
    void firstResolveDoesNotAnimateAndHoverStartsFromPresentedColor() {
        UiElement button = button();
        UiDocument document = document(button, """
                button {
                    background-color: #000000;
                    transition: background-color 1s linear;
                }
                button:hover { background-color: #ffffff; }
                """);
        document.layout(100f, 100f);

        assertEquals(Color.BLACK, button.background());
        assertEquals(0, document.styles().runningTransitions());

        document.pointerMoved(20f, 20f);
        UiDocument.FrameStats start = document.advance(0f);
        assertEquals(Color.BLACK, button.background());
        assertEquals(1, start.runningTransitions());

        document.advance(0.5f);
        assertEquals(128, button.background().getRed(), 1);
        document.advance(0.5f);
        assertEquals(Color.WHITE, button.background());
        assertEquals(0, document.styles().runningTransitions());
    }

    @Test
    void reversalUsesElapsedDistanceAndLeavesNoTrail() {
        UiElement button = button();
        UiDocument document = document(button, """
                button { background-color: #000000; transition: background-color 1s linear; }
                button:hover { background-color: #ffffff; }
                """);
        document.layout(100f, 100f);
        document.pointerMoved(20f, 20f);
        document.advance(0f);
        document.advance(0.2f);

        document.pointerMoved(150f, 150f);
        document.advance(0f);
        document.advance(0.21f);

        assertEquals(Color.BLACK, button.background());
        assertEquals(0, document.styles().runningTransitions());
    }

    @Test
    void easedReversalDurationTracksPresentedDistance() {
        UiElement button = button();
        UiDocument document = document(button, """
                button { background-color: #000; transition: background-color 1s ease-out; }
                button:hover { background-color: #fff; }
                """);
        document.layout(100f, 100f);
        document.pointerMoved(20f, 20f);
        document.advance(0f);
        document.advance(0.2f);

        document.pointerMoved(150f, 150f);
        document.advance(0f);
        document.advance(0.21f);
        assertEquals(1, document.styles().runningTransitions());

        document.advance(0.11f);
        assertEquals(Color.BLACK, button.background());
        assertEquals(0, document.styles().runningTransitions());
    }

    @Test
    void transparentColorInterpolationIsPremultiplied() {
        UiElement button = button();
        UiDocument document = document(button, """
                button { background-color: transparent; transition: background-color 1s linear; }
                button:hover { background-color: #ffffffff; }
                """);
        document.layout(100f, 100f);
        document.pointerMoved(20f, 20f);
        document.advance(0f);
        document.advance(0.5f);

        Color midpoint = button.background();
        assertEquals(255, midpoint.getRed());
        assertEquals(255, midpoint.getGreen());
        assertEquals(255, midpoint.getBlue());
        assertEquals(128, midpoint.getAlpha(), 1);
    }

    @Test
    void paintOnlyMotionDoesNotRelayoutButWidthMotionDoes() {
        UiElement button = new UiElement("button")
                .tag(UiTag.BUTTON)
                .preferredHeight(40f)
                .align(UiAlign.START, UiAlign.START)
                .addClass("wide");
        UiDocument document = document(button, """
                button {
                    width: 20px;
                    background-color: #000;
                    transition: width 1s linear, background-color 1s linear;
                }
                button.expanded { width: 100px; background-color: #fff; }
                """);
        document.layout(200f, 100f);
        int baseline = document.layoutPasses();

        button.addClass("expanded");
        document.advance(0f);
        assertEquals(baseline, document.layoutPasses());

        UiDocument.FrameStats moving = document.advance(0.5f);
        assertEquals(1, moving.layoutPasses());
        assertEquals(60f, button.box().borderBox().width(), EPSILON);
    }

    @Test
    void delayMovesAndLaysOutNothingAndReversalInsideItCancels() {
        UiElement button = button();
        UiDocument document = document(button, """
                button { background-color: #000; transition: background-color 1s linear 0.5s; }
                button:hover { background-color: #fff; }
                """);
        document.layout(100f, 100f);
        document.pointerMoved(20f, 20f);
        document.advance(0f);

        UiDocument.FrameStats delayed = document.advance(0.25f);
        assertEquals(0, delayed.transitionedValues());
        assertEquals(0, delayed.layoutPasses());
        assertEquals(Color.BLACK, button.background());

        document.pointerMoved(150f, 150f);
        document.advance(0f);
        document.advance(0f);
        assertEquals(0, document.styles().runningTransitions());
        assertEquals(Color.BLACK, button.background());
    }

    @Test
    void negativeOrNanTimeIsRejected() {
        UiDocument document = document(button(), "button { background-color: #000; }");
        document.layout(100f, 100f);
        assertThrows(IllegalArgumentException.class, () -> document.advance(-0.01f));
        assertThrows(IllegalArgumentException.class, () -> document.advance(Float.NaN));
    }

    @Test
    void paintOnlyColorAndOpacityMotionNeverRelayouts() {
        UiElement button = button();
        UiDocument document = document(button, """
                button {
                    background-color: #000;
                    opacity: 1;
                    transition: background-color 1s linear, opacity 1s linear;
                }
                button:hover { background-color: #fff; opacity: 0.5; }
                """);
        document.layout(100f, 100f);
        int baseline = document.layoutPasses();
        document.pointerMoved(20f, 20f);
        document.advance(0f);

        UiDocument.FrameStats midpoint = document.advance(0.5f);

        assertEquals(0, midpoint.layoutPasses());
        assertEquals(baseline, document.layoutPasses());
        assertEquals(0.75f, button.opacity(), EPSILON);
    }

    @Test
    void animatedInheritedColorPropagatesToDescendants() {
        UiElement child = new UiElement("child");
        UiElement root = new UiElement("root").child(child);
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("marine-ops-theme", """
                        :root { color: #000; transition: color 1s linear; }
                        :root.hot { color: #fff; }
                        """), Map.of()));
        document.layout(100f, 100f);

        root.addClass("hot");
        document.advance(0f);
        document.advance(0.5f);
        document.advance(0f);

        assertEquals(128, child.textColor().getRed(), 1);
    }

    @Test
    void crossingAdjacentButtonsReversesTheOldOneWithoutATrail() {
        UiElement left = new UiElement("left").tag(UiTag.BUTTON)
                .preferredWidth(50f).onClick(() -> { });
        UiElement right = new UiElement("right").tag(UiTag.BUTTON)
                .preferredWidth(50f).onClick(() -> { });
        UiElement root = new UiElement("root").layout(UiLayout.ROW).child(left).child(right);
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("marine-ops-theme", """
                        button { background-color: #000; transition: background-color 1s linear; }
                        button:hover { background-color: #fff; }
                        """), Map.of()));
        document.layout(100f, 40f);
        document.pointerMoved(25f, 20f);
        document.advance(0f);
        document.advance(0.2f);

        document.pointerMoved(75f, 20f);
        document.advance(0f);
        document.advance(0.21f);

        assertEquals(Color.BLACK, left.background());
        assertEquals(54, right.background().getRed(), 1);
        assertEquals(1, document.styles().runningTransitions());
        document.advance(0.79f);
        assertEquals(Color.WHITE, right.background());
        assertEquals(0, document.styles().runningTransitions());
    }

    @Test
    void cssTransitionAcceptsTimingBeforeDuration() {
        StyleDeclaration declaration = StyleDeclaration.parse(
                "transition: opacity ease-out 200ms 50ms");
        @SuppressWarnings("unchecked")
        List<TransitionSpec> specs = (List<TransitionSpec>)
                declaration.value(StyleProperty.TRANSITION);

        assertEquals(0.2f, specs.get(0).durationSeconds(), EPSILON);
        assertEquals(0.05f, specs.get(0).delaySeconds(), EPSILON);
        assertEquals(Easing.EASE_OUT, specs.get(0).easing());
    }

    @Test
    void removingAnElementCancelsItsRunningTransitions() {
        UiElement button = button();
        UiElement root = new UiElement("root").child(button);
        UiDocument document = new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("marine-ops-theme", """
                        button { background-color: #000; transition: background-color 1s linear; }
                        button:hover { background-color: #fff; }
                        """), Map.of()));
        document.layout(100f, 100f);
        document.pointerMoved(20f, 20f);
        document.advance(0f);
        assertEquals(1, document.styles().runningTransitions());

        root.remove(button);
        document.advance(0f);

        assertEquals(0, document.styles().runningTransitions());
    }

    private static UiElement button() {
        return new UiElement("button")
                .tag(UiTag.BUTTON)
                .preferredSize(80f, 40f)
                .align(UiAlign.START, UiAlign.START);
    }

    private static UiDocument document(UiElement button, String css) {
        UiElement root = new UiElement("root")
                .layout(UiLayout.STACK)
                .child(button);
        return new UiDocument(root).theme(new UiTheme(
                StyleSheet.parse("marine-ops-theme", css), Map.of()));
    }
}
