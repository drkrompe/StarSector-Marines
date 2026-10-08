package com.dillon.starsectormarines.ui.retained;

import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiIntrinsicTextTest {

    private static final float EPSILON = 0.001f;
    private static final FixedFont FONT = new FixedFont(10f, 20);

    @Test
    void centeredRowUsesIntrinsicLegendHeightWithoutPaddingOffsets() {
        UiElement legend = new UiElement("legend").text("DEPLOY").grow(1f)
                .style("width: 0px; text-align: center");
        UiElement root = new UiElement("root")
                .style("flex-direction: row; align-items: center; padding: 4px; border-width: 2px")
                .child(new UiElement("lamp").style("width: 14px; height: 8px"))
                .child(legend)
                .child(new UiElement("grip").style("width: 14px; height: 24px"));
        UiDocument document = document(root);
        document.layout(200f, 40f);
        UiTextMeasurer text = new UiTextMeasurer(document.styles());
        Rect line = text.lineBox(legend, text.measure(legend));
        assertEquals(100f, line.x() + line.width() / 2f, EPSILON);
        assertEquals(20f, line.y() + line.height() / 2f, EPSILON);
        document.layout(300f, 60f);
        line = text.lineBox(legend, text.measure(legend));
        assertEquals(150f, line.x() + line.width() / 2f, EPSILON);
        assertEquals(30f, line.y() + line.height() / 2f, EPSILON);
    }

    @Test
    void columnAlignmentUsesItsHorizontalCrossAxisAndCanChange() {
        UiElement child = new UiElement("child").style("width: 30px; height: 20px");
        UiElement root = new UiElement("root").style("flex-direction: column; align-items: center")
                .child(child);
        UiDocument document = document(root);
        document.layout(100f, 100f);
        assertEquals(35f, child.box().borderBox().x(), EPSILON);
        root.style("flex-direction: column; align-items: end");
        document.advance(0f);
        assertEquals(70f, child.box().borderBox().x(), EPSILON);
    }

    @Test
    void rowMeasuresWrappedCrossHeightAtTheAllocatedChildWidth() {
        UiElement wrapped = new UiElement("wrapped").text("ONE TWO")
                .style("width: 35px; white-space: normal");
        UiDocument document = document(new UiElement("root")
                .style("flex-direction: row; align-items: center").child(wrapped));
        document.layout(200f, 60f);
        assertEquals(40f, wrapped.box().borderBox().height(), EPSILON);
        assertEquals(10f, wrapped.box().borderBox().y(), EPSILON);
    }

    @Test
    void wrappedRowKeepsPercentagePaddingRelativeToTheContainingBlock() {
        UiElement wrapped = new UiElement("wrapped").text("ONE TWO")
                .style("width: 35px; padding: 10%; white-space: normal");
        UiDocument document = document(new UiElement("root")
                .style("flex-direction: row; align-items: center").child(wrapped));
        document.layout(200f, 100f);
        assertEquals(75f, wrapped.box().borderBox().width(), EPSILON);
        assertEquals(80f, wrapped.box().borderBox().height(), EPSILON);
        assertEquals(10f, wrapped.box().borderBox().y(), EPSILON);
    }

    @Test
    void autoRowTextPaysItsContentWidthAndDeclaredWidthWins() {
        UiElement automatic = button("automatic", "HIGH CONTRAST");
        UiElement declared = button("declared", "HIGH CONTRAST")
                .style("width: 80px");
        UiElement root = new UiElement("root")
                .layout(UiLayout.ROW)
                .child(automatic)
                .child(declared);
        UiDocument document = document(root);

        document.layout(400f, 40f);

        assertEquals(152f, automatic.box().borderBox().width(), EPSILON);
        assertEquals(102f, declared.box().borderBox().width(), EPSILON);
    }

    @Test
    void changingIntrinsicTextSchedulesOneRelayout() {
        UiElement button = button("button", "A");
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(button));
        document.layout(200f, 40f);
        int baseline = document.layoutPasses();
        assertEquals(32f, button.box().borderBox().width(), EPSILON);

        button.text("AAAA");
        UiDocument.FrameStats changed = document.advance(0f);

        assertEquals(1, changed.layoutPasses());
        assertEquals(baseline + 1, document.layoutPasses());
        assertEquals(62f, button.box().borderBox().width(), EPSILON);
    }

    @Test
    void centeredButtonUsesTheSameMeasuredLineBoxAsLayout() {
        UiElement button = button("button", "OK").preferredWidth(100f);
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(button));
        document.layout(200f, 40f);
        UiTextMeasurer text = new UiTextMeasurer(document.styles());

        Rect line = text.lineBox(button, text.measure(button));

        assertEquals(40f, line.x(), EPSILON);
        assertEquals(10f, line.y(), EPSILON);
        assertEquals(20f, line.width(), EPSILON);
        assertEquals(20f, line.height(), EPSILON);
    }

    @Test
    void undersizedCenteredButtonKeepsItsLineCentered() {
        UiElement button = button("button", "OK").preferredWidth(30f);
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(button));
        document.layout(200f, 40f);
        UiTextMeasurer text = new UiTextMeasurer(document.styles());

        Rect line = text.lineBox(button, text.measure(button));

        assertEquals(5f, line.x(), EPSILON);
        assertEquals(20f, line.width(), EPSILON);
    }

    @Test
    void replacingAThemeFontRemeasuresIntrinsicText() {
        UiElement button = button("button", "AAAA");
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(button));
        document.layout(200f, 40f);
        assertEquals(62f, button.box().borderBox().width(), EPSILON);

        document.theme(theme(new FixedFont(20f, 20)));
        UiDocument.FrameStats changed = document.advance(0f);

        assertEquals(1, changed.layoutPasses());
        assertEquals(102f, button.box().borderBox().width(), EPSILON);
    }

    @Test
    void tagAndLayoutMutationsInvalidateGeometry() {
        UiElement element = new UiElement("element");
        UiElement root = new UiElement("root")
                .layout(UiLayout.ROW)
                .child(element);
        UiDocument document = document(root);
        document.layout(500f, 200f);

        element.tag(UiTag.CANVAS);
        assertEquals(1, document.advance(0f).layoutPasses());
        assertEquals(300f, element.box().borderBox().width(), EPSILON);

        root.layout(UiLayout.STACK);
        assertEquals(1, document.advance(0f).layoutPasses());
    }

    @Test
    void emptyTextElementStillPaysItsFrame() {
        UiElement empty = button("empty", null);
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(empty));

        document.layout(200f, 40f);

        assertEquals(22f, empty.box().borderBox().width(), EPSILON);
    }

    @Test
    void normalWhiteSpaceWrapsIntoTheAuthoredContentWidth() {
        UiElement prose = new UiElement("prose").text("AAAA BBBB")
                .style("width: 50px; white-space: normal");
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.ROW)
                .child(prose));

        document.layout(100f, 100f);
        UiTextMeasurer.Measurement measured = new UiTextMeasurer(document.styles())
                .measure(prose, prose.box().contentBox().width());

        assertEquals(List.of("AAAA", "BBBB"), measured.lines());
        assertEquals(40f, measured.height(), EPSILON);
    }

    private static UiElement button(String id, String text) {
        return new UiElement(id).tag(UiTag.BUTTON).text(text);
    }

    private static UiDocument document(UiElement root) {
        return new UiDocument(root).theme(theme(FONT));
    }

    private static UiTheme theme(BitmapFont font) {
        return new UiTheme(
                StyleSheet.parse("test-theme", """
                        :root { font-family: body; }
                        button {
                            padding: 5px 10px;
                            border-width: 1px;
                            text-align: center;
                        }
                        """), Map.of("body", font));
    }

    private static final class FixedFont extends BitmapFont {
        private final float advance;
        private final int lineHeight;

        private FixedFont(float advance, int lineHeight) {
            super("unused-test-font.fnt");
            this.advance = advance;
            this.lineHeight = lineHeight;
        }

        @Override
        public float measureWidth(String value) {
            return value == null ? 0f : value.length() * advance;
        }

        @Override
        public int getLineHeight() {
            return lineHeight;
        }

        @Override
        public List<String> wrapLines(String value, float maxWidth) {
            if (value == null || value.isEmpty()) return List.of();
            if (measureWidth(value) <= maxWidth) return List.of(value);
            return List.of(value.split(" "));
        }
    }
}
