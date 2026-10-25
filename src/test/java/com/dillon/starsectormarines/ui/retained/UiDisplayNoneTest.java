package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDisplayNoneTest {

    private static final float EPSILON = 0.001f;

    @Test
    void hiddenChildTakesNoSpaceAndNoGapSlot() {
        UiElement first = new UiElement("first").style("height: 20px");
        UiElement hidden = new UiElement("hidden").style("height: 20px; display: none");
        UiElement last = new UiElement("last").style("height: 20px");
        UiDocument document = new UiDocument(new UiElement("root").style("gap: 10px")
                .child(first).child(hidden).child(last));
        document.layout(100f, 200f);
        assertEquals(30f, last.box().borderBox().y(), EPSILON);
        assertEquals(Rect.EMPTY.width(), hidden.box().borderBox().width(), EPSILON);
        hidden.style("height: 20px");
        document.advance(0f);
        assertEquals(60f, last.box().borderBox().y(), EPSILON);
    }

    @Test
    void hiddenSubtreeIsNotPainted() {
        UiElement painted = new UiElement("painted").style("height: 20px").background(Color.RED);
        UiElement inner = new UiElement("inner").style("height: 10px").background(Color.BLUE);
        UiElement hidden = new UiElement("hidden").style("display: none").background(Color.GREEN).child(inner);
        UiDocument document = new UiDocument(new UiElement("root").child(painted).child(hidden));
        document.layout(100f, 100f);
        List<Color> fills = new ArrayList<>();
        document.render(new FillRecorder(fills), 1f);
        assertEquals(List.of(Color.RED), fills);
    }

    @Test
    void hiddenButtonIsNeitherHitNorTabbed() {
        UiElement visible = new UiElement("visible").tag(UiTag.BUTTON).style("height: 20px");
        UiElement hidden = new UiElement("hidden").tag(UiTag.BUTTON)
                .style("position: absolute; width: 100px; height: 100px; display: none");
        UiElement root = new UiElement("root").child(visible).child(hidden);
        UiDocument document = new UiDocument(root);
        document.layout(100f, 100f);
        assertSame(visible, document.elementAt(50f, 10f));
        assertSame(root, document.elementAt(50f, 50f));
        assertTrue(document.keyPressed(UiKey.TAB, Set.of(), false));
        assertSame(visible, document.focusedElement());
        assertTrue(document.keyPressed(UiKey.TAB, Set.of(), false));
        assertSame(visible, document.focusedElement());
    }

    private record FillRecorder(List<Color> fills) implements UiPaintTarget {
        @Override public void begin() {}
        @Override public void end() {}
        @Override public float devicePixelRatio() { return 1f; }
        @Override public void clip(Rect clip) {}
        @Override public void fill(Rect rect, Color color, float alphaMult) { fills.add(color); }
        @Override public void outline(Rect rect, Color color, float width, float alphaMult) {}
        @Override public void text(BitmapFont font, String text, Rect lineBox, Color color, float alphaMult) {}
        @Override public CanvasContext canvasContext(CanvasMetrics metrics, Rect visibleBounds, float alphaMult) {
            throw new AssertionError("no canvas in this document");
        }
    }
}
