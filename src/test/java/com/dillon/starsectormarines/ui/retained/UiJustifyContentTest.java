package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiJustifyContentTest {

    private static final float EPSILON = 0.001f;

    @Test
    void centerAndEndPlaceFixedChildrenInTheFreeMainSpace() {
        UiElement first = new UiElement("first").style("height: 20px");
        UiElement second = new UiElement("second").style("height: 20px");
        UiElement root = new UiElement("root").style("gap: 10px; justify-content: center")
                .child(first).child(second);
        UiDocument document = new UiDocument(root);
        document.layout(100f, 100f);
        assertEquals(25f, first.box().borderBox().y(), EPSILON);
        assertEquals(55f, second.box().borderBox().y(), EPSILON);
        root.style("gap: 10px; justify-content: end");
        document.advance(0f);
        assertEquals(50f, first.box().borderBox().y(), EPSILON);
        assertEquals(80f, second.box().borderBox().y(), EPSILON);
    }

    @Test
    void spaceBetweenSpreadsFreeSpaceIntoTheGapsAndAGrowingChildTakesItInstead() {
        UiElement first = new UiElement("first").style("width: 20px");
        UiElement middle = new UiElement("middle").style("width: 20px");
        UiElement last = new UiElement("last").style("width: 20px");
        UiElement root = new UiElement("root")
                .style("flex-direction: row; gap: 10px; justify-content: space-between")
                .child(first).child(middle).child(last);
        UiDocument document = new UiDocument(root);
        document.layout(200f, 20f);
        assertEquals(0f, first.box().borderBox().x(), EPSILON);
        assertEquals(90f, middle.box().borderBox().x(), EPSILON);
        assertEquals(180f, last.box().borderBox().x(), EPSILON);
        middle.style("width: 20px; flex-grow: 1");
        document.advance(0f);
        assertEquals(30f, middle.box().borderBox().x(), EPSILON);
        assertEquals(180f, last.box().borderBox().x(), EPSILON);
    }
}
