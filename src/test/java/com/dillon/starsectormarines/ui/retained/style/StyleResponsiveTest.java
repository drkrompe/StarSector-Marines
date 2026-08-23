package com.dillon.starsectormarines.ui.retained.style;

import com.dillon.starsectormarines.ui.retained.UiAlign;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleResponsiveTest {

    private static final float EPSILON = 0.01f;

    @Test
    void remPercentAndClampRemainBoundedAcrossAspectAndUiScaleMatrix() {
        float[][] physicalViewports = {
                {1920f, 1080f},
                {1920f, 1200f},
                {2560f, 1080f}
        };
        float[] scales = {1f, 1.25f, 1.5f};

        for (float[] physical : physicalViewports) {
            for (float scale : scales) {
                float width = physical[0] / scale;
                float height = physical[1] / scale;
                UiElement panel = new UiElement("panel")
                        .addClass("responsive")
                        .align(UiAlign.START, UiAlign.START);
                UiElement root = new UiElement("root")
                        .layout(UiLayout.STACK)
                        .child(panel);
                UiDocument document = new UiDocument(root).theme(new UiTheme(
                        StyleSheet.parse("marine-ops-theme", """
                                .responsive {
                                    width: clamp(12rem, 30%, 26.25rem);
                                    height: min(25%, 12rem);
                                    padding: clamp(8px, 2%, 16px) 2%;
                                    border-width: 2px;
                                }
                                """), Map.of()));

                document.layout(width, height);

                float expectedContentWidth = Math.max(192f, Math.min(width * 0.30f, 420f));
                float expectedOuterWidth = expectedContentWidth + width * 0.04f + 4f;
                assertEquals(expectedOuterWidth, panel.box().borderBox().width(), EPSILON);
                assertEquals(width * 0.02f, panel.box().padding().left(), EPSILON);
                assertTrue(panel.box().borderBox().right() <= width + EPSILON);
                assertTrue(panel.box().borderBox().bottom() <= height + EPSILON);
            }
        }
    }
}
