package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiViewportTest {

    private static final float EPSILON = 0.001f;

    @Test
    void smallerPhysicalPanelPreservesTheReferenceLogicalView() {
        UiViewport viewport = UiViewport.relative(
                0f, 0f, 872f, 469f, 1f, 1744f, 938f);

        assertEquals(0.5f, viewport.documentScale(), EPSILON);
        assertEquals(1744f, viewport.documentWidth(), EPSILON);
        assertEquals(938f, viewport.documentHeight(), EPSILON);
    }

    @Test
    void selectedUiScaleStillReducesTheLogicalWorkspace() {
        UiViewport viewport = UiViewport.relative(
                0f, 0f, 1744f / 1.5f, 938f / 1.5f,
                1.5f, 1744f, 938f);

        assertEquals(1f, viewport.documentScale(), EPSILON);
        assertEquals(1744f / 1.5f, viewport.documentWidth(), EPSILON);
        assertEquals(938f / 1.5f, viewport.documentHeight(), EPSILON);
    }

    @Test
    void largerPanelExposesMoreLayoutRoomInsteadOfUpscaling() {
        UiViewport viewport = UiViewport.relative(
                0f, 0f, 2325f, 1250f, 1f, 1744f, 938f);

        assertEquals(1f, viewport.documentScale(), EPSILON);
        assertEquals(2325f, viewport.documentWidth(), EPSILON);
        assertEquals(1250f, viewport.documentHeight(), EPSILON);
    }

    @Test
    void scaledScreenAndDocumentCoordinatesRoundTrip() {
        UiViewport viewport = new UiViewport(40f, 20f, 800f, 500f, 0.625f);

        float screenX = viewport.screenXFor(320f);
        float screenY = viewport.screenTopFor(140f);

        assertEquals(320f, viewport.documentX(screenX), EPSILON);
        assertEquals(140f, viewport.documentY(screenY), EPSILON);
    }
}
